package com.example.b2b;

import com.example.b2b.config.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TokenExchangeServiceTest {
    private HttpServer server;
    private AppConfig config;
    private final RecordingHttpClient client = new RecordingHttpClient(HttpSupport.newClient());
    private final TokenExchangeService service = new TokenExchangeService(new TokenClient(client, new ObjectMapper()));
    private Map<String, String> form;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            form = HttpSupport.queryParams(URI.create("http://localhost/?" + body));
            var response = "{\"access_token\":\"result-token\",\"expires_in\":60}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        var base = "http://127.0.0.1:" + server.getAddress().getPort();
        config = new AppConfig("dev1", "Dev1", base, base + "/token", base + "/authorize", "attended-id",
                "attended-secret", "https://localhost:8090/openid-callback", "unattended+id", "audience&value");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void unattendedMatchesJavascriptFormExactly() throws Exception {
        var token = service.unattended(config, "assertion+value&=");
        assertEquals("result-token", token.accessToken());
        assertEquals(Map.of("client_id", "unattended+id", "grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer",
                "assertion", "assertion+value&="), form);
        assertRequestMetadata();
    }

    @Test
    void finalExchangeMatchesJavascriptFormExactly() throws Exception {
        service.finalToken(config, "user+token", "b2b&token", "final.assertion");
        assertEquals(Set.of("client_id", "client_assertion", "audience", "grant_type", "subject_token_type",
                "subject_token", "actor_token_type", "actor_token"), form.keySet());
        assertEquals("unattended+id", form.get("client_id"));
        assertEquals("final.assertion", form.get("client_assertion"));
        assertEquals("audience&value", form.get("audience"));
        assertEquals("urn:ietf:params:oauth:grant-type:token-exchange", form.get("grant_type"));
        assertEquals("urn:ietf:params:oauth:token-type:access_token", form.get("subject_token_type"));
        assertEquals("user+token", form.get("subject_token"));
        assertEquals("urn:ietf:params:oauth:token-type:jwt", form.get("actor_token_type"));
        assertEquals("b2b&token", form.get("actor_token"));
        assertRequestMetadata();
    }

    private void assertRequestMetadata() {
        var request = client.requests().get(0);
        assertEquals(URI.create(config.tokenEndpoint()), request.uri());
        assertEquals("POST", request.method());
        assertEquals(Duration.ofSeconds(10), request.timeout().orElseThrow());
        assertEquals("application/x-www-form-urlencoded", request.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(request.headers().firstValue("messageId").orElseThrow().startsWith("urn:uuid:"));
        assertTrue(request.headers().firstValue("correlationId").orElseThrow().startsWith("urn:uuid:"));
    }
}
