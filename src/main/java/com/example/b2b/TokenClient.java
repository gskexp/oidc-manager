package com.example.b2b;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;

/** Shared form POST behavior for all three JavaScript-compatible token exchanges. */
@RequiredArgsConstructor
public final class TokenClient {
    private static final Duration TOKEN_TIMEOUT = Duration.ofSeconds(10);
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    TokenResponse exchange(String tokenEndpoint, Map<String, String> form, String step)
            throws B2bClientException, InterruptedException {
        var endpoint = URI.create(tokenEndpoint);
        var request = HttpRequest.newBuilder(endpoint).timeout(TOKEN_TIMEOUT)
                .header("Content-Type", HttpSupport.FORM_CONTENT_TYPE)
                .header("Accept", "application/json").header("User-Agent", HttpSupport.USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(HttpSupport.formEncode(form))).build();
        try {
            var response = HttpSupport.send(httpClient, request);
            if (response.statusCode() / 100 != 2) {
                throw new B2bClientException(step + " endpoint returned HTTP " + response.statusCode() + ": "
                        + HttpSupport.describeOAuthError(mapper, response.body()));
            }
            return parseToken(response.body(), step);
        } catch (IOException e) {
            throw HttpSupport.transportError(step, endpoint, e, TOKEN_TIMEOUT);
        }
    }

    private TokenResponse parseToken(String body, String step) throws B2bClientException {
        var json = HttpSupport.parseJson(mapper, body)
                .orElseThrow(() -> new B2bClientException(step + " endpoint returned a non-JSON response"));
        var accessToken = json.path("access_token").asText("");
        if (accessToken.isBlank()) throw new B2bClientException(step + " response is missing access_token");
        var expiresIn = json.hasNonNull("expires_in") ? json.get("expires_in").asLong() : null;
        return new TokenResponse(accessToken, json.path("token_type").asText("Bearer"), expiresIn, json);
    }
}
