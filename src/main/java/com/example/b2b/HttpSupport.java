package com.example.b2b;

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
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import javax.net.ssl.SSLException;

/** Shared HTTP helpers: client construction, form encoding, query parsing and safe logging. */
public final class HttpSupport {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final String USER_AGENT = "b2b-api-tester/1.0";
    public static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

    private HttpSupport() {
    }

    /** Creates a client that does not follow redirects (callers handle them explicitly). */
    public static HttpClient newClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Creates a client that stores cookies in the given manager and does not follow redirects. */
    public static HttpClient newClient(CookieManager cookieManager) {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(cookieManager)
                .build();
    }

    /** URL-encodes the map as {@code application/x-www-form-urlencoded}, skipping null values. */
    public static String formEncode(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
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
        Map<String, String> params = new LinkedHashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.putIfAbsent(key, value);
        }
        return params;
    }

    /** Returns the URI without query or fragment so it can be logged without leaking codes/state. */
    public static String withoutQuery(URI uri) {
        String s = uri.toString();
        int cut = s.indexOf('?');
        if (cut < 0) {
            cut = s.indexOf('#');
        }
        return cut < 0 ? s : s.substring(0, cut);
    }

    /** Masks a token for display: first 8 characters plus its length. */
    public static String mask(String token) {
        if (token == null) {
            return "<none>";
        }
        if (token.length() <= 12) {
            return "***(" + token.length() + " chars)";
        }
        return token.substring(0, 8) + "...(" + token.length() + " chars)";
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
        String trimmed = text.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "...";
    }

    /** Converts transport-level failures into actionable messages. */
    public static B2bClientException transportError(String step, URI uri, IOException e) {
        String target = withoutQuery(uri);
        String reason;
        if (e instanceof HttpConnectTimeoutException) {
            reason = "connection timed out after " + CONNECT_TIMEOUT.toSeconds() + "s";
        } else if (e instanceof HttpTimeoutException) {
            reason = "no response within " + REQUEST_TIMEOUT.toSeconds() + "s";
        } else if (e instanceof ConnectException) {
            reason = "connection refused or host unreachable";
        } else if (e instanceof SSLException) {
            reason = "TLS handshake failed (" + e.getMessage() + "). If the server uses a private CA, run with "
                    + "-Djavax.net.ssl.trustStore=<truststore> -Djavax.net.ssl.trustStorePassword=<password>";
        } else {
            reason = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
        }
        return new B2bClientException(step + " failed calling " + target + ": " + reason, e);
    }
}
