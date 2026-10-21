package com.example.b2b;

import com.example.b2b.config.AppConfig;
import com.example.b2b.config.KeyEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Registers a device by sending the public key of the selected keystore alias to the
 * registration endpoint.
 *
 * <p>Payload (JSON):</p>
 * <pre>
 * {
 *   "client_id": "...",
 *   "key_id": "&lt;alias&gt;",
 *   "key_algorithm": "RSA" | "EC",
 *   "key_format": "X.509",
 *   "public_key": "&lt;Base64 SubjectPublicKeyInfo&gt;"
 * }
 * </pre>
 */
public final class DeviceRegistrationService {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final PrintStream out;
    private final PrintStream err;

    public DeviceRegistrationService(HttpClient httpClient, ObjectMapper mapper, PrintStream out, PrintStream err) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.out = out;
        this.err = err;
    }

    /**
     * Performs the registration.
     *
     * @return {@code true} if the endpoint answered with a 2xx status
     */
    public boolean register(AppConfig config, KeyEntry key) throws B2bClientException, InterruptedException {
        out.println();
        out.println("== Device Registration [" + config.environment() + " / " + key.name() + "] ==");

        KeyStoreService keyStore = KeyStoreService.load(config.jks());
        PublicKey publicKey = keyStore.publicKey(key.alias());
        String encodedKey = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        out.println("Loaded " + publicKey.getAlgorithm() + " public key for alias '" + key.alias()
                + "' (" + publicKey.getFormat() + ", " + publicKey.getEncoded().length + " bytes)");

        ObjectNode payload = mapper.createObjectNode()
                .put("client_id", config.oauth().clientId())
                .put("key_id", key.alias())
                .put("key_algorithm", publicKey.getAlgorithm())
                .put("key_format", publicKey.getFormat())
                .put("public_key", encodedKey);

        URI endpoint = URI.create(config.endpoints().registrationEndpoint());
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(endpoint)
                    .timeout(HttpSupport.REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", HttpSupport.USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
        } catch (JsonProcessingException e) {
            throw new B2bClientException("Failed to build registration payload", e);
        }

        out.println("POST " + endpoint);
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw HttpSupport.transportError("Device registration", endpoint, e);
        }

        int status = response.statusCode();
        String body = HttpSupport.parseJson(mapper, response.body())
                .map(node -> node.toPrettyString())
                .orElse(response.body());
        if (status / 100 == 2) {
            out.println("[SUCCESS] Device registered (HTTP " + status + ")");
            if (body != null && !body.isBlank()) {
                out.println(body);
            }
            return true;
        }
        err.println("[FAILURE] Device registration rejected (HTTP " + status + "): "
                + HttpSupport.describeOAuthError(mapper, response.body()));
        return false;
    }
}
