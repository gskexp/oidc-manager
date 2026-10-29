package com.example.b2b;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultHeadersTest {
    @TempDir Path dir;

    @Test
    void eachRequestGetsFreshUrnUuidsAndPreservesRequestDetails() throws Exception {
        var defaults = new DefaultHeaders(Path.of("data/http-headers.json"));
        var request = HttpRequest.newBuilder(URI.create("https://example.com/register"))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer token")
                .POST(HttpRequest.BodyPublishers.ofString("payload")).build();
        var ids = new HashSet<String>();
        for (var attempt = 0; attempt < 2; attempt++) {
            var outgoing = defaults.apply(request);
            for (var header : new String[]{"messageId", "correlationId"}) {
                var value = outgoing.headers().firstValue(header).orElseThrow();
                assertTrue(value.startsWith("urn:uuid:"));
                UUID.fromString(value.substring("urn:uuid:".length()));
                assertTrue(ids.add(value));
            }
            assertEquals(11, outgoing.headers().map().size());
            assertEquals("Bearer token", outgoing.headers().firstValue("Authorization").orElseThrow());
            assertEquals(request.uri(), outgoing.uri());
            assertEquals(request.method(), outgoing.method());
            assertEquals(request.timeout(), outgoing.timeout());
            assertSame(request.bodyPublisher().orElseThrow(), outgoing.bodyPublisher().orElseThrow());
        }
    }

    @Test
    void reloadsChangesAndPreservesRequestSpecificHeaders() throws Exception {
        var file = dir.resolve("http-headers.json");
        var defaults = new DefaultHeaders(file);
        var request = HttpRequest.newBuilder(URI.create("https://example.com"))
                .header("Accept", "application/json").build();
        Files.writeString(file, "{\"accept\":\"text/plain\",\"X-Partner\":\"first\"}");
        var first = defaults.apply(request);
        assertEquals("application/json", first.headers().firstValue("accept").orElseThrow());
        assertEquals("first", first.headers().firstValue("X-Partner").orElseThrow());
        Files.writeString(file, "{\"X-Partner\":\"changed\"}");
        assertEquals("changed", defaults.apply(request).headers().firstValue("X-Partner").orElseThrow());
    }

    @Test
    void rejectsMissingOrInvalidHeaderConfiguration() throws Exception {
        var file = dir.resolve("http-headers.json");
        var defaults = new DefaultHeaders(file);
        var request = HttpRequest.newBuilder(URI.create("https://example.com")).build();
        assertThrows(IOException.class, () -> defaults.apply(request));
        for (var json : new String[]{"null", "[]", "{\"X-Test\":123}", "{\"bad name\":\"value\"}"}) {
            Files.writeString(file, json);
            assertThrows(IOException.class, () -> defaults.apply(request));
        }
    }
}
