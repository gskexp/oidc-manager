package com.example.b2b;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Receives one authorization result and releases its HTTPS port when closed. */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class CallbackListener implements AutoCloseable {
    private final HttpsServer server;
    private final URI callbackUri;
    private final String expectedState;
    private final CompletableFuture<String> result = new CompletableFuture<>();

    static CallbackListener start(URI callbackUri, String state, SSLContext context) throws IOException {
        var address = InetAddress.getByName(callbackUri.getHost());
        if (!"https".equals(callbackUri.getScheme()) || !address.isLoopbackAddress()
                || callbackUri.getPort() < 0 || callbackUri.getPath().isBlank()
                || callbackUri.getRawQuery() != null || callbackUri.getRawFragment() != null) {
            throw new IOException("Callback URI must be an HTTPS loopback URL with an explicit port and path");
        }
        var server = HttpsServer.create(new InetSocketAddress(address, callbackUri.getPort()), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context));
        var listener = new CallbackListener(server, callbackUri, state);
        server.createContext(callbackUri.getPath(), listener::handle);
        server.start();
        log.info("Listening for authorization code at {}", listener.uri());
        return listener;
    }

    URI uri() {
        return URI.create("https://" + callbackUri.getRawAuthority().replaceFirst(":\\d+$", "")
                + ":" + server.getAddress().getPort() + callbackUri.getRawPath());
    }

    private void handle(HttpExchange exchange) throws IOException {
        log.info("[HTTP callback] {} {}", exchange.getRequestMethod(), exchange.getRequestURI());
        if (!callbackUri.getPath().equals(exchange.getRequestURI().getPath())) {
            respond(exchange, 404, "Unknown callback path.");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            respond(exchange, 405, "The callback requires GET.");
            return;
        }
        try {
            acceptCallback(exchange, HttpSupport.queryParams(exchange.getRequestURI()));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, "Malformed callback query.");
        }
    }

    private void acceptCallback(HttpExchange exchange, java.util.Map<String, String> parameters) throws IOException {
        if (!expectedState.equals(parameters.get("state"))) {
            respond(exchange, 400, "Callback state does not match. Return to the sign-in page.");
        } else if (result.isDone()) {
            respond(exchange, 409, "This sign-in has already completed.");
        } else if (parameters.containsKey("error")) {
            respond(exchange, 400, "Sign-in was not completed. See the application console.");
            result.completeExceptionally(new B2bClientException("Authorization failed: " + parameters.get("error")
                    + " " + parameters.getOrDefault("error_description", "")));
        } else if (parameters.getOrDefault("code", "").isBlank()) {
            respond(exchange, 400, "No authorization code was supplied.");
        } else {
            respond(exchange, 200, "Authorization code received. You can close this tab and return to the application.");
            result.complete(parameters.get("code"));
        }
    }

    String awaitCode(Duration timeout) throws B2bClientException, InterruptedException {
        try {
            return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new B2bClientException("Timed out waiting for sign-in at " + uri(), e);
        } catch (ExecutionException e) {
            throw new B2bClientException(e.getCause().getMessage(), e.getCause());
        }
    }

    private static void respond(HttpExchange exchange, int status, String text) throws IOException {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
