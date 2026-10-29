package com.example.b2b;

import com.example.b2b.config.ConfigLoader;
import com.example.b2b.config.Environment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir Path dir;

    @Test
    void allEnvironmentsIncludeAttendedClientAndCallbackSettings() throws Exception {
        var loader = new ConfigLoader(Path.of("data"));
        for (var env : Environment.values()) {
            var root = mapper.readTree(Path.of("data", env.fileName()).toFile());
            var names = new java.util.HashSet<String>();
            root.fieldNames().forEachRemaining(names::add);
            assertEquals(Set.of("env", "label", "issuerUrlBase", "tokenEndpoint", "authorizeEndpoint",
                    "attendedClientId", "attendedClientSecret", "callbackUri", "clientId", "audience"), names);
            var config = loader.load(env);
            assertEquals(env.id(), config.env());
            assertFalse(config.label().isBlank());
            assertEquals("https://localhost:8090/openid-callback", config.callbackUri());
            assertEquals(10, mapper.valueToTree(config).size());
            assertFalse(config.attendedClientId().isBlank());
            assertFalse(config.attendedClientSecret().isBlank());
        }
    }

    @Test
    void missingFieldsAreReportedAndExtraSettingsAreRejected() throws Exception {
        var root = (ObjectNode) mapper.readTree(Path.of("data/dev1-config.json").toFile());
        root.remove("tokenEndpoint");
        Files.writeString(dir.resolve("dev1-config.json"), root.toString());
        var error = assertThrows(B2bClientException.class, () -> new ConfigLoader(dir).load(Environment.DEV1));
        assertTrue(error.getMessage().contains("tokenEndpoint"));
        root.put("tokenEndpoint", "https://example.com/token");
        root.putObject("oauth").put("username", "obsolete");
        Files.writeString(dir.resolve("dev1-config.json"), root.toString());
        assertThrows(B2bClientException.class, () -> new ConfigLoader(dir).load(Environment.DEV1));
    }

    @Test
    void envMustMatchTheSelectedEnvironment() throws Exception {
        var root = (ObjectNode) mapper.readTree(Path.of("data/dev1-config.json").toFile());
        root.put("env", "dev2");
        Files.writeString(dir.resolve("dev1-config.json"), root.toString());
        var error = assertThrows(B2bClientException.class, () -> new ConfigLoader(dir).load(Environment.DEV1));
        assertTrue(error.getMessage().contains("must use env 'dev1'"));
    }
}
