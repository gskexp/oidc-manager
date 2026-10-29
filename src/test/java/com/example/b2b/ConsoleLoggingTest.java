package com.example.b2b;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleLoggingTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger("com.example.b2b");
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        events.start();
        logger.addAppender(events);
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
    }

    @AfterEach
    void restoreLogging() {
        logger.detachAppender(events);
        events.stop();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
    }

    @Test
    void minimalKeepsRequestsAndResultsAndVerboseAddsResponseBody() throws Exception {
        var client = new StubClient(false);
        var request = HttpRequest.newBuilder(URI.create("https://example.com/orders?code=secret-query"))
                .header("Authorization", "Bearer secret-token").build();
        HttpSupport.send(client, request);
        assertTrue(messages().contains("[HTTP request] GET https://example.com/orders?code=secret-query"));
        assertTrue(messages().contains("[HTTP response] 200"));
        assertFalse(messages().contains("sample-body"));
        assertTrue(messages().contains("secret-query"));

        events.list.clear();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        HttpSupport.send(client, request);
        assertTrue(messages().contains("sample-body"));
        assertTrue(messages().contains("secret-token"));

        events.list.clear();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        HttpSupport.send(client, request);
        assertFalse(messages().contains("sample-body"));
    }

    @Test
    void failedRequestsRetainFullUrlAndError() {
        var request = HttpRequest.newBuilder(URI.create("https://user:secret-user@example.com/login?code=secret-code"))
                .header("Authorization", "Bearer secret-token")
                .POST(HttpRequest.BodyPublishers.ofString("password=secret-password")).build();
        assertThrows(IOException.class, () -> HttpSupport.send(new StubClient(true), request));
        assertTrue(messages().contains("[HTTP request] POST https://user:secret-user@example.com/login?code=secret-code"));
        assertTrue(messages().contains("[HTTP failed] IOException"));
        assertTrue(messages().contains("secret-message"));
    }

    @Test
    void verboseShowsLiteralBodyAndRecordCredentials() throws Exception {
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        var request = HttpRequest.newBuilder(URI.create("https://example.com/register"))
                .POST(HttpRequest.BodyPublishers.ofString("orgId=123&otac=literal-otac")).build();
        HttpSupport.send(new StubClient(false), request);
        assertTrue(messages().contains("[HTTP request body] orgId=123&otac=literal-otac"));
        assertTrue(new DeviceRegistrationService.RegistrationRequest("123", "device", "literal-otac")
                .toString().contains("literal-otac"));
    }

    private String messages() {
        return events.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /** Exercises the real HTTP logging path without opening a socket. */
    @lombok.RequiredArgsConstructor
    private static final class StubClient extends HttpClient {
        private final boolean fail;
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { throw new UnsupportedOperationException(); }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
            if (fail) throw new IOException("secret-message");
            request.bodyPublisher().ifPresent(publisher -> publisher.subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                @Override public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                @Override public void onNext(java.nio.ByteBuffer buffer) { }
                @Override public void onError(Throwable error) { throw new AssertionError(error); }
                @Override public void onComplete() { }
            }));
            return (HttpResponse<T>) new Response(request);
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h) {
            throw new UnsupportedOperationException();
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h,
                HttpResponse.PushPromiseHandler<T> p) { throw new UnsupportedOperationException(); }
    }

    private record Response(HttpRequest request) implements HttpResponse<String> {
        @Override public int statusCode() { return 200; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (k, v) -> true); }
        @Override public String body() { return "sample-body"; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
