package com.example.b2b;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

import com.example.b2b.config.AppConfig;
import com.example.b2b.config.Environment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

/**
 * Registers a new device:
 * <ol>
 *   <li>Generates an RSA-2048 key pair.</li>
 *   <li>Saves them via {@link DeviceStore} (public JWK + PEM + metadata) <em>before</em> calling the
 *       endpoint, so the private key is never lost if the server accepts the key but the
 *       response does not arrive.</li>
 *   <li>POSTs orgId (RA number), OTAC and the public JWK to
 *       {@code issuerUrlBase}/{deviceName}/jwk.</li>
 *   <li>Records the outcome in the device metadata.</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public final class DeviceRegistrationService {

    public static final String KEY_ALGORITHM = "RSA";
    public static final int KEY_SIZE = 2048;

    /** Values entered by the user at registration time. */
    public record RegistrationRequest(String raNumber, String deviceName, String otac) {
    }

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final DeviceStore deviceStore;

    /**
     * Performs the registration.
     *
     * @return {@code true} if the endpoint answered with a 2xx status
     */
    public boolean register(Environment environment, AppConfig config, RegistrationRequest request)
            throws B2bClientException, InterruptedException {
        log.debug("== Device Registration [{} / {}] ==", config.env(), request.deviceName());

        var endpoint = registrationUri(config.issuerUrlBase(), request.deviceName());
        var alias = DeviceStore.aliasFor(request.deviceName());
        var keyPair = generateKeyPair();
        var metadata = DeviceMetadata.pending(request.deviceName(), alias, request.raNumber(),
                environment.id(), KEY_ALGORITHM, KEY_SIZE);
        var dir = deviceStore.save(environment, metadata, keyPair);
        log.debug("Generated {}-{} key pair (alias '{}')", KEY_ALGORITHM, KEY_SIZE, alias);
        log.debug("Saved jwk.json, PEM files and metadata to {}", dir);

        var httpRequest = buildRegistrationRequest(endpoint, registrationPayload(request, dir));
        var response = sendRegistration(httpRequest);
        return recordRegistrationResult(environment, metadata, dir, response);
    }

    private KeyPair generateKeyPair() throws B2bClientException {
        try {
            var generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(KEY_SIZE);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new B2bClientException("Unable to generate device key pair: " + e.getMessage(), e);
        }
    }

    private ObjectNode registrationPayload(RegistrationRequest request, Path dir) throws B2bClientException {
        var payload = mapper.createObjectNode()
                .put("orgId", request.raNumber())
                .put("otac", request.otac());
        try {
            payload.set("key", mapper.readTree(Files.readString(dir.resolve("jwk.json"))));
        } catch (IOException e) {
            throw new B2bClientException("Unable to read saved public JWK: " + dir.resolve("jwk.json"), e);
        }

        return payload;
    }

    private HttpRequest buildRegistrationRequest(URI endpoint, ObjectNode payload) throws B2bClientException {
        try {
            return HttpRequest.newBuilder(endpoint)
                    .timeout(HttpSupport.REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", HttpSupport.USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
        } catch (JsonProcessingException e) {
            throw new B2bClientException("Failed to build registration payload", e);
        }
    }

    private HttpResponse<String> sendRegistration(HttpRequest request)
            throws B2bClientException, InterruptedException {
        try {
            return HttpSupport.send(httpClient, request);
        } catch (IOException e) {
            throw HttpSupport.transportError("Device registration", request.uri(), e);
        }
    }

    private boolean recordRegistrationResult(Environment environment, DeviceMetadata metadata, Path dir,
                                             HttpResponse<String> response) throws B2bClientException {
        var status = response.statusCode();
        var success = status / 100 == 2;
        var body = HttpSupport.parseJson(mapper, response.body())
                .orElseGet(() -> TextNode.valueOf(response.body() == null ? "" : response.body()));
        deviceStore.writeMetadata(environment, metadata.withResult(success, status, body));

        if (success) {
            log.info("[SUCCESS] Device registered (HTTP {})", status);
            var printable = body.isTextual() ? body.asText() : body.toPrettyString();
            if (!printable.isBlank()) {
                log.debug(printable);
            }
            return true;
        }
        log.error("[FAILURE] Device registration rejected (HTTP {}): {}", status, HttpSupport.describeOAuthError(mapper, response.body()));
        log.error("          Key material kept in {} (status FAILED); registering the same device name again will replace it.", dir);
        return false;
    }

    /** Appends the device name as a single UTF-8 encoded path segment. */
    static URI registrationUri(String issuerUrlBase, String deviceName) {
        var base = URI.create(issuerUrlBase);
        if (!("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null || base.getRawQuery() != null || base.getRawFragment() != null
                || base.getRawUserInfo() != null) {
            throw new IllegalArgumentException("issuerUrlBase must be an HTTP(S) URL without query, fragment or user info");
        }
        var segment = URLEncoder.encode(deviceName, StandardCharsets.UTF_8).replace("+", "%20")
                .replace(".", "%2E");
        return URI.create(issuerUrlBase.replaceAll("/+$", "") + "/" + segment + "/jwk");
    }
}
