package com.example.b2b;

import com.example.b2b.config.ConfigLoader;
import com.example.b2b.config.Environment;
import com.example.b2b.config.StartupConfig;
import com.example.b2b.config.StartupConfig.Action;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MainMenuTest {
    @TempDir Path dir;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Map<String, String>> forms = new CopyOnWriteArrayList<>();
    private final List<String> activations = new CopyOnWriteArrayList<>();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
    private HttpServer server;
    private DeviceStore store;
    private String previousDataDir;
    private int browserCalls;

    @BeforeEach
    void setup() throws Exception {
        previousDataDir = System.getProperty("b2b.dataDir");
        System.setProperty("b2b.dataDir", dir.toString());
        Files.copy(Path.of("data/http-headers.json"), dir.resolve("http-headers.json"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        installEndpoints();
        server.start();
        createEnvironmentFiles();
        store = new DeviceStore(dir, mapper);
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var metadata = DeviceMetadata.pending("Device A", "device-a", "123", "dev1", "RSA", 2048)
                .withResult(true, 201, mapper.createObjectNode());
        store.save(Environment.DEV1, metadata, generator.generateKeyPair());
    }

    @AfterEach
    void cleanup() {
        if (server != null) server.stop(0);
        if (previousDataDir == null) System.clearProperty("b2b.dataDir");
        else System.setProperty("b2b.dataDir", previousDataDir);
    }

    @Test
    void defaultMenuSeparatesActivationFullFlowAndSteps() throws Exception {
        run("4\n");
        assertTrue(text().contains("1. Activate Device"));
        assertTrue(text().contains("2. Select Device & Run Full Flow"));
        assertTrue(text().contains("3. Check Individual Token Steps"));
        assertFalse(text().contains("Register Device"));
        assertTrue(forms.isEmpty());
    }

    @Test
    void configuredUnattendedActionSkipsActionEnvironmentAndDevicePrompts() throws Exception {
        startup(Action.UNATTENDED_TOKEN, "device-a", null, null);
        run("");
        assertEquals(1, forms.size());
        assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", forms.get(0).get("grant_type"));
        assertFalse(text().contains("Main Menu"));
        assertFalse(text().contains("Environment ["));
        assertFalse(text().contains("Device ["));
        assertTrue(text().contains("Unattended B2B access token: token-1"));
    }

    @Test
    void finalOnlyUsesConfiguredTokensWithoutRepeatingOtherSteps() throws Exception {
        startup(Action.FINAL_TOKEN, "device-a", null, new StartupConfig.Tokens("provided-user", "provided-b2b"));
        run("");
        assertEquals(1, forms.size());
        assertEquals("urn:ietf:params:oauth:grant-type:token-exchange", forms.get(0).get("grant_type"));
        assertEquals("provided-user", forms.get(0).get("subject_token"));
        assertEquals("provided-b2b", forms.get(0).get("actor_token"));
        assertEquals(0, browserCalls);
        assertTrue(text().contains("Final access token: token-1"));
    }

    @Test
    void fullFlowPresentsOnlyTheFinalToken() throws Exception {
        startup(Action.FULL_FLOW, "device-a", null, null);
        run("");
        assertEquals(3, forms.size());
        assertEquals("token-2", forms.get(2).get("subject_token"));
        assertEquals("token-1", forms.get(2).get("actor_token"));
        assertEquals(1, browserCalls);
        assertTrue(text().contains("Final access token: token-3"));
        assertFalse(text().contains("token-1"));
        assertFalse(text().contains("token-2"));
        assertFalse(text().contains("Main Menu"));
    }

    @Test
    void individualStepsReuseTokensInTheSession() throws Exception {
        startup(Action.TOKEN_STEPS, "device-a", null, null);
        run("1\n2\n3\n4\n");
        assertEquals(3, forms.size());
        assertEquals("token-2", forms.get(2).get("subject_token"));
        assertEquals("token-1", forms.get(2).get("actor_token"));
        assertFalse(text().contains("User access token: User"));
        assertFalse(text().contains("B2B access token: B2B"));
        assertTrue(errors.toString(StandardCharsets.UTF_8).isEmpty());
    }

    @Test
    void startupActivationUsesConfiguredInputValues() throws Exception {
        startup(Action.ACTIVATE_DEVICE, null, new StartupConfig.Activation("00123", "New Device", "000000"), null);
        run("");
        assertEquals(List.of("/issuer/New%20Device/jwk"), activations);
        assertTrue(store.find(Environment.DEV1, "new-device").orElseThrow().isRegistered());
        assertFalse(text().contains("RA number:"));
        assertFalse(text().contains("Device name:"));
        assertTrue(forms.isEmpty());
    }

    @Test
    void missingDeviceDefaultPromptsForDeviceOnly() throws Exception {
        startup(Action.UNATTENDED_TOKEN, null, null, null);
        run("1\n");
        assertTrue(text().contains("Device [1-1]:"));
        assertFalse(text().contains("Environment ["));
        assertEquals(1, forms.size());
    }

    @Test
    void invalidConfiguredDeviceDoesNotFallBackOrSendRequests() throws Exception {
        startup(Action.FULL_FLOW, "missing-device", null, null);
        run("");
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("No activated device with alias 'missing-device'"));
        assertTrue(forms.isEmpty());
        assertEquals(0, browserCalls);
    }

    private void startup(Action action, String alias, StartupConfig.Activation activation, StartupConfig.Tokens tokens)
            throws IOException {
        var config = new StartupConfig(action, "dev1", alias, activation, tokens, true);
        Files.writeString(dir.resolve("startup-config.json"), mapper.writeValueAsString(config));
    }

    private void run(String commands) throws Exception {
        var tls = CallbackTls.loadOrCreate(dir);
        var browser = TlsTestClient.trusting(tls.certificate());
        try (var scanner = new Scanner(commands); var out = new PrintStream(output); var err = new PrintStream(errors)) {
            var input = new ConsoleInput(scanner, out, err);
            new Main(input, dir, new ConfigLoader(dir), store, mapper, out, err, uri -> {
                browserCalls++;
                var parameters = HttpSupport.queryParams(uri);
                var callback = URI.create(parameters.get("redirect_uri") + "?code=code&state=" + parameters.get("state"));
                try {
                    assertEquals(200, browser.send(HttpRequest.newBuilder(callback).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
                } catch (IOException | InterruptedException e) { throw new AssertionError(e); }
            }).run();
        }
    }

    private String text() {
        return output.toString(StandardCharsets.UTF_8);
    }

    private void createEnvironmentFiles() throws IOException {
        var base = "http://127.0.0.1:" + server.getAddress().getPort();
        try (var socket = new ServerSocket(0, 0, InetAddress.getByName("localhost"))) {
            for (var env : Environment.values()) {
                var root = (ObjectNode) mapper.readTree(Path.of("data", env.fileName()).toFile());
                root.put("issuerUrlBase", base + "/issuer");
                root.put("tokenEndpoint", base + "/token");
                root.put("callbackUri", "https://localhost:" + socket.getLocalPort() + "/openid-callback");
                Files.writeString(dir.resolve(env.fileName()), root.toString());
            }
        }
    }

    private void installEndpoints() {
        server.createContext("/", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response;
            if (exchange.getRequestURI().getPath().equals("/token")) {
                forms.add(HttpSupport.queryParams(URI.create("http://localhost/?" + body)));
                response = "{\"access_token\":\"token-" + forms.size() + "\"}";
            } else {
                activations.add(exchange.getRequestURI().getRawPath());
                response = "{}";
            }
            var bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
    }
}
