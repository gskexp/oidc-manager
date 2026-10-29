package com.example.b2b;

import com.example.b2b.config.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UserAuthServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private String base;
    @TempDir Path dir;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void browserCallbackIsExchangedForUserTokenWithEnvironmentCredentials() throws Exception {
        var tls = CallbackTls.loadOrCreate(dir);
        var browser = TlsTestClient.trusting(tls.certificate());
        var query = new AtomicReference<Map<String, String>>();
        var form = new AtomicReference<Map<String, String>>();
        installTokenEndpoint(200, "{\"access_token\":\"user-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}", form);
        var config = config();
        var client = new RecordingHttpClient(HttpSupport.newClient());
        var auth = new UserAuthService(client, mapper, dir, uri -> {
            query.set(HttpSupport.queryParams(uri));
            var callback = URI.create(query.get().get("redirect_uri") + "?code=user-code&state=" + query.get().get("state"));
            try {
                assertEquals(200, browser.send(HttpRequest.newBuilder(callback).build(),
                        HttpResponse.BodyHandlers.ofString()).statusCode());
            } catch (IOException | InterruptedException e) { throw new AssertionError(e); }
        });
        var token = auth.authenticate(config);
        assertEquals("user-token", token.accessToken());
        assertEquals(3600L, token.expiresIn());
        assertEquals("code", query.get().get("response_type"));
        assertEquals(config.attendedClientId(), query.get().get("client_id"));
        assertEquals(config.callbackUri(), query.get().get("redirect_uri"));
        assertFalse(query.get().containsKey("client_secret"));
        UUID.fromString(query.get().get("state"));
        UUID.fromString(query.get().get("nonce"));
        assertEquals("authorization_code", form.get().get("grant_type"));
        assertEquals(config.attendedClientId(), form.get().get("client_id"));
        assertEquals(config.attendedClientSecret(), form.get().get("client_secret"));
        assertEquals(config.callbackUri(), form.get().get("redirect_uri"));
        assertEquals("user-code", form.get().get("code"));
        assertEquals(Set.of("grant_type", "code", "client_id", "client_secret", "redirect_uri"), form.get().keySet());
        assertEquals(Duration.ofSeconds(10), client.requests().get(0).timeout().orElseThrow());
    }

    @Test
    void fullFlowUsesB2bUserAndFinalExchangeInOrder() throws Exception {
        var forms = new CopyOnWriteArrayList<Map<String, String>>();
        installFlowEndpoint(forms);
        var tls = CallbackTls.loadOrCreate(dir);
        var browser = TlsTestClient.trusting(tls.certificate());
        var client = new RecordingHttpClient(HttpSupport.newClient());
        var userAuth = new UserAuthService(client, mapper, dir, uri -> completeSignIn(uri, browser));
        var exchanges = new TokenExchangeService(new TokenClient(client, mapper));
        var flow = new TokenFlowService(exchanges, userAuth, new JwtSigner(mapper));
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var store = new DeviceStore(dir, mapper);
        store.save(com.example.b2b.config.Environment.DEV1,
                DeviceMetadata.pending("device", "device", "123", "dev1", "RSA", 2048), generator.generateKeyPair());
        var key = new SigningKey("device", "device", store.signingKeyPath(com.example.b2b.config.Environment.DEV1, "device"));
        var result = flow.authenticate(config(), key);
        assertEquals("flow-token-3", result.accessToken());
        assertEquals(List.of("urn:ietf:params:oauth:grant-type:jwt-bearer", "authorization_code",
                "urn:ietf:params:oauth:grant-type:token-exchange"), forms.stream().map(f -> f.get("grant_type")).toList());
        assertEquals("flow-token-2", forms.get(2).get("subject_token"));
        assertEquals("flow-token-1", forms.get(2).get("actor_token"));
        assertNotEquals(forms.get(0).get("assertion"), forms.get(2).get("client_assertion"));
        assertTrue(client.requests().stream().allMatch(r -> r.timeout().orElseThrow().equals(Duration.ofSeconds(10))));
    }

    private void installFlowEndpoint(List<Map<String, String>> forms) {
        server.createContext("/token", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            forms.add(HttpSupport.queryParams(URI.create("http://localhost/?" + body)));
            var response = ("{\"access_token\":\"flow-token-" + forms.size() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
    }

    private static void completeSignIn(URI uri, java.net.http.HttpClient browser) {
        var query = HttpSupport.queryParams(uri);
        var callback = URI.create(query.get("redirect_uri") + "?code=user-code&state=" + query.get("state"));
        try {
            assertEquals(200, browser.send(HttpRequest.newBuilder(callback).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        } catch (IOException | InterruptedException e) { throw new AssertionError(e); }
    }

    @Test
    void tokenEndpointErrorsAndMissingTokensAreReported() throws Exception {
        var captured = new AtomicReference<Map<String, String>>();
        installTokenEndpoint(400, "{\"error\":\"invalid_client\"}", captured);
        var auth = new UserAuthService(HttpSupport.newClient(), mapper, dir, uri -> { });
        var error = assertThrows(B2bClientException.class, () -> auth.exchangeCode(config(), "code"));
        assertTrue(error.getMessage().contains("invalid_client"));
        server.removeContext("/token");
        installTokenEndpoint(200, "{}", captured);
        assertTrue(assertThrows(B2bClientException.class, () -> auth.exchangeCode(config(), "code"))
                .getMessage().contains("access_token"));
    }

    private void installTokenEndpoint(int status, String response, AtomicReference<Map<String, String>> captured) {
        server.createContext("/token", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            captured.set(HttpSupport.queryParams(URI.create("http://localhost/?" + body)));
            assertTrue(exchange.getRequestHeaders().getFirst("messageId").startsWith("urn:uuid:"));
            assertTrue(exchange.getRequestHeaders().getFirst("correlationId").startsWith("urn:uuid:"));
            var bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
    }

    private AppConfig config() throws IOException {
        try (var socket = new ServerSocket(0, 0, InetAddress.getByName("localhost"))) {
            return new AppConfig("dev1", "Dev1", base, base + "/token", base + "/authorize?existing=1",
                    "attended+id", "secret&value=+", "https://localhost:" + socket.getLocalPort() + "/openid-callback", "unattended-id", "final-audience");
        }
    }
}
