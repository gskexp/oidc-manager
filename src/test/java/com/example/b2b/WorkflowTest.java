package com.example.b2b;

import com.example.b2b.config.AppConfig;
import com.example.b2b.config.Environment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;
    private final List<com.sun.net.httpserver.Headers> receivedHeaders = new CopyOnWriteArrayList<>();
    @TempDir Path dataDir;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        var ids = new HashSet<String>();
        for (var headers : receivedHeaders) {
            for (var name : new String[]{"messageId", "correlationId"}) {
                var value = headers.getFirst(name);
                assertNotNull(value);
                assertTrue(value.startsWith("urn:uuid:"));
                UUID.fromString(value.substring("urn:uuid:".length()));
                assertTrue(ids.add(value));
            }
            for (var index = 1; index <= 8; index++) {
                assertEquals("replace-me", headers.getFirst("X-Default-Header-" + index));
            }
        }
    }

    private void createContext(String path, HttpHandler handler) {
        var context = server.createContext(path, handler);
        context.getFilters().add(new Filter() {
            @Override public String description() { return "Capture headers of every HTTP request"; }
            @Override public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
                receivedHeaders.add(exchange.getRequestHeaders());
                chain.doFilter(exchange);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 400})
    void registrationSendsSavedJwkAndRecordsOutcome(int status) throws Exception {
        var captured = new AtomicReference<JsonNode>();
        var path = new AtomicReference<String>();
        var savedBeforeSend = new AtomicReference<Boolean>();
        var store = new DeviceStore(dataDir, mapper);
        var dir = store.deviceDir(Environment.DEV1, "device-a");
        createContext("/issuer/", exchange -> {
            captured.set(mapper.readTree(exchange.getRequestBody()));
            path.set(exchange.getRequestURI().getRawPath());
            savedBeforeSend.set(Files.exists(dir.resolve("private-key.pem")));
            respond(exchange, status, "{}");
        });
        var service = new DeviceRegistrationService(HttpSupport.newClient(), mapper, store);
        var success = service.register(Environment.DEV1, config(),
                new DeviceRegistrationService.RegistrationRequest("00123", "Device A/+?", "otac-value"));
        assertEquals(status == 201, success);
        assertEquals("/issuer/Device%20A%2F%2B%3F/jwk", path.get());
        assertEquals(Boolean.TRUE, savedBeforeSend.get());
        var body = captured.get();
        assertEquals(Set.of("orgId", "otac", "key"), mapper.convertValue(body, Map.class).keySet());
        assertEquals("00123", body.path("orgId").asText());
        assertEquals("otac-value", body.path("otac").asText());
        assertEquals(mapper.readTree(dir.resolve("jwk.json").toFile()), body.get("key"));
        var metadata = store.find(Environment.DEV1, "device-a").orElseThrow();
        assertEquals(status, metadata.lastHttpStatus());
        assertEquals(success, metadata.isRegistered());
    }

    private AppConfig config() throws IOException {
        var root = (ObjectNode) mapper.readTree(Path.of("data/dev1-config.json").toFile());
        root.put("issuerUrlBase", baseUrl + "/issuer/");
        return mapper.treeToValue(root, AppConfig.class);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
