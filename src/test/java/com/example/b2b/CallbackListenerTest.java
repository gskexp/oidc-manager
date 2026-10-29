package com.example.b2b;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class CallbackListenerTest {
    @TempDir Path dir;

    @Test
    void httpsCallbackChecksStateAndPathBeforeAcceptingCode() throws Exception {
        var tls = CallbackTls.loadOrCreate(dir);
        var client = TlsTestClient.trusting(tls.certificate());
        try (var listener = CallbackListener.start(URI.create("https://localhost:0/openID-callback"), "state", tls.context())) {
            assertEquals(400, client.send(HttpRequest.newBuilder(URI.create(listener.uri() + "?code=bad&state=wrong"))
                    .build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(listener.uri() + "/other?code=bad&state=state"))
                    .build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(400, client.send(HttpRequest.newBuilder(URI.create(listener.uri() + "?state=state"))
                    .build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            var response = client.send(HttpRequest.newBuilder(URI.create(listener.uri() + "?code=code%2Bvalue&state=state"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("code+value", listener.awaitCode(Duration.ofSeconds(1)));
        }
    }

    @Test
    void authorizationErrorIsReportedAndListenerCanBeClosed() throws Exception {
        var tls = CallbackTls.loadOrCreate(dir);
        try (var listener = CallbackListener.start(URI.create("https://localhost:0/openID-callback"), "state", tls.context())) {
            var response = TlsTestClient.trusting(tls.certificate()).send(HttpRequest.newBuilder(
                    URI.create(listener.uri() + "?error=access_denied&error_description=Cancelled&state=state")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode());
            var error = assertThrows(B2bClientException.class, () -> listener.awaitCode(Duration.ofSeconds(1)));
            assertTrue(error.getMessage().contains("access_denied"));
        }
    }

    @Test
    void timeoutReleasesPortAndTlsIdentityIsReused() throws Exception {
        var tls = CallbackTls.loadOrCreate(dir);
        var listener = CallbackListener.start(URI.create("https://localhost:0/openID-callback"), "state", tls.context());
        var uri = listener.uri();
        try (listener) {
            assertThrows(B2bClientException.class, () -> listener.awaitCode(Duration.ofMillis(20)));
        }
        try (var restarted = CallbackListener.start(uri, "new-state", tls.context())) {
            assertEquals(uri, restarted.uri());
        }
        var reloaded = CallbackTls.loadOrCreate(dir);
        assertArrayEquals(tls.certificate().getEncoded(), reloaded.certificate().getEncoded());
        assertArrayEquals(tls.privateKey().getEncoded(), reloaded.privateKey().getEncoded());
    }
}
