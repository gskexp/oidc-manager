package com.example.b2b;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import javax.net.ssl.SSLException;

/** Shared HTTP helpers: client construction, form encoding, query parsing and console logging. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HttpSupport {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final String USER_AGENT = "b2b-api-tester/1.0";
    public static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";


    /** Logs every request and status; verbose mode includes full headers and body. */
    public static HttpResponse<String> send(HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {
        log.info("[HTTP request] {} {}", request.method(), request.uri());
        try {
            var headersFile = Path.of(System.getProperty("b2b.dataDir", "data")).resolve("http-headers.json");
            var outgoing = new DefaultHeaders(headersFile).apply(request);
            log.debug("[HTTP request headers] {}", outgoing.headers().map());
            outgoing = withBodyLogging(outgoing);
            var response = client.send(outgoing, HttpResponse.BodyHandlers.ofString());
            log.info("[HTTP response] {}", response.statusCode());
            log.debug("[HTTP response headers] {}", response.headers().map());
            log.debug("[HTTP response body] {}", response.body());
            return response;
        } catch (IOException | InterruptedException e) {
            log.error("[HTTP failed] {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    private static HttpRequest withBodyLogging(HttpRequest request) {
        if (!log.isDebugEnabled() || request.bodyPublisher().isEmpty()) {
            return request;
        }
        var publisher = new LoggingBodyPublisher(request.bodyPublisher().orElseThrow());
        return HttpRequest.newBuilder(request, (name, value) -> true)
                .method(request.method(), publisher).build();
    }

    /** Creates a client that does not follow redirects (callers handle them explicitly). */
    public static HttpClient newClient() {
        return configuredBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Creates a client that stores cookies in the given manager and does not follow redirects. */
    public static HttpClient newClient(CookieManager cookieManager) {
        return configuredBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(cookieManager)
                .build();
    }

    private static HttpClient.Builder configuredBuilder() {
        var dataDir = Path.of(System.getProperty("b2b.dataDir", "data"));
        return AdditionalCaTrust.configure(HttpClient.newBuilder(), dataDir);
    }

    /** URL-encodes the map as {@code application/x-www-form-urlencoded}, skipping null values. */
    public static String formEncode(Map<String, String> params) {
        var joiner = new StringJoiner("&");
        params.forEach((key, value) -> {
            if (value != null) {
                joiner.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        });
        return joiner.toString();
    }

    /** Parses the query string of a URI into an ordered map (first occurrence wins). */
    public static Map<String, String> queryParams(URI uri) {
        var params = new LinkedHashMap<String, String>();
        var query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (var pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            var eq = pair.indexOf('=');
            var key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            var value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.putIfAbsent(key, value);
        }
        return params;
    }

    /** Returns the URI without query or fragment for URI manipulation. */
    public static String withoutQuery(URI uri) {
        var s = uri.toString();
        var cut = s.indexOf('?');
        if (cut < 0) {
            cut = s.indexOf('#');
        }
        return cut < 0 ? s : s.substring(0, cut);
    }

    /** Parses a body as JSON if possible. */
    public static Optional<JsonNode> parseJson(ObjectMapper mapper, String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readTree(body));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /** Extracts a concise OAuth error description from an error response body. */
    public static String describeOAuthError(ObjectMapper mapper, String body) {
        return parseJson(mapper, body)
                .filter(node -> node.has("error"))
                .map(node -> node.path("error").asText()
                        + (node.hasNonNull("error_description") ? " - " + node.path("error_description").asText() : ""))
                .orElseGet(() -> truncate(body, 500));
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        var trimmed = text.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "...";
    }

    /** Converts transport-level failures into actionable messages. */
    public static B2bClientException transportError(String step, URI uri, IOException e) {
        return transportError(step, uri, e, REQUEST_TIMEOUT);
    }

    public static B2bClientException transportError(String step, URI uri, IOException e, Duration timeout) {
        var target = uri.toString();
        String reason;
        if (e instanceof HttpConnectTimeoutException) {
            reason = "connection timed out after " + CONNECT_TIMEOUT.toSeconds() + "s";
        } else if (e instanceof HttpTimeoutException) {
            reason = "no response within " + timeout.toSeconds() + "s";
        } else if (e instanceof ConnectException) {
            reason = "connection refused or host unreachable";
        } else if (e instanceof SSLException) {
            reason = "TLS certificate/handshake failed (" + e.getMessage() + "). Add the token server's issuing CA "
                    + "certificate (or corporate proxy CA) as PEM under "
                    + Path.of(System.getProperty("b2b.dataDir", "data")).resolve("trusted-certs")
                    + ". The localhost callback certificate is separate from remote server trust.";
        } else {
            reason = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
        }
        return new B2bClientException(step + " failed calling " + target + ": " + reason, e);
    }
}
