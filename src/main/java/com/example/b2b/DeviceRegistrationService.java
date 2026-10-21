package com.example.b2b;

import com.example.b2b.config.AppConfig;
import com.example.b2b.config.Environment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Registers a new device:
 * <ol>
 *   <li>Generates an RSA-2048 key pair and a self-signed certificate.</li>
 *   <li>Saves them via {@link DeviceStore} (JKS + PEM + metadata) <em>before</em> calling the
 *       endpoint, so the private key is never lost if the server accepts the key but the
 *       response does not arrive.</li>
 *   <li>POSTs the public key, RA number, device name and OTAC, merged over the environment's
 *       {@code registration.fixedFields}, to {@code registrationEndpoint}.</li>
 *   <li>Records the outcome in the device metadata.</li>
 * </ol>
 *
 * <p>Payload (JSON):</p>
 * <pre>
 * {
 *   ...registration.fixedFields...,
 *   "clientId": "...",
 *   "raNumber": "...",
 *   "deviceName": "...",
 *   "otac": "...",
 *   "keyId": "&lt;alias&gt;",
 *   "keyAlgorithm": "RSA",
 *   "keySize": 2048,
 *   "publicKey": "&lt;Base64 X.509 SubjectPublicKeyInfo&gt;"
 * }
 * </pre>
 */
public final class DeviceRegistrationService {

    public static final String KEY_ALGORITHM = "RSA";
    public static final int KEY_SIZE = 2048;
    private static final Duration CERT_VALIDITY = Duration.ofDays(3650);

    /** Values entered by the user at registration time. */
    public record RegistrationRequest(String raNumber, String deviceName, String otac) {

        /** Masks the OTAC so the record can never leak it through logging. */
        @Override
        public String toString() {
            return "RegistrationRequest[raNumber=" + raNumber + ", deviceName=" + deviceName + ", otac=***]";
        }
    }

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final DeviceStore deviceStore;
    private final PrintStream out;
    private final PrintStream err;

    public DeviceRegistrationService(HttpClient httpClient, ObjectMapper mapper, DeviceStore deviceStore,
                                     PrintStream out, PrintStream err) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.deviceStore = deviceStore;
        this.out = out;
        this.err = err;
    }

    /**
     * Performs the registration.
     *
     * @return {@code true} if the endpoint answered with a 2xx status
     */
    public boolean register(Environment environment, AppConfig config, RegistrationRequest request)
            throws B2bClientException, InterruptedException {
        out.println();
        out.println("== Device Registration [" + config.environment() + " / " + request.deviceName() + "] ==");

        String alias = DeviceStore.aliasFor(request.deviceName());
        KeyPair keyPair;
        X509Certificate certificate;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(KEY_SIZE);
            keyPair = generator.generateKeyPair();
            certificate = SelfSignedCertificate.create(keyPair, request.deviceName(),
                    "RA " + request.raNumber(), CERT_VALIDITY);
        } catch (GeneralSecurityException e) {
            throw new B2bClientException("Unable to generate device key pair: " + e.getMessage(), e);
        }

        DeviceMetadata metadata = DeviceMetadata.pending(request.deviceName(), alias, request.raNumber(),
                environment.id(), KEY_ALGORITHM, KEY_SIZE);
        Path dir = deviceStore.save(environment, metadata, keyPair, certificate, config.jks().jksPassword());
        out.println("Generated " + KEY_ALGORITHM + "-" + KEY_SIZE + " key pair (alias '" + alias + "')");
        out.println("Saved keystore, PEM files and metadata to " + dir);

        String encodedKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        ObjectNode payload = config.registration().fixedFields().deepCopy();
        payload.put("clientId", config.oauth().clientId())
                .put("raNumber", request.raNumber())
                .put("deviceName", request.deviceName())
                .put("otac", request.otac())
                .put("keyId", alias)
                .put("keyAlgorithm", KEY_ALGORITHM)
                .put("keySize", KEY_SIZE)
                .put("publicKey", encodedKey);

        URI endpoint = URI.create(config.endpoints().registrationEndpoint());
        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(HttpSupport.REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", HttpSupport.USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
        } catch (JsonProcessingException e) {
            throw new B2bClientException("Failed to build registration payload", e);
        }

        out.println("POST " + endpoint + " (fixed fields: " + fixedFieldNames(config) + ")");
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw HttpSupport.transportError("Device registration", endpoint, e);
        }

        int status = response.statusCode();
        boolean success = status / 100 == 2;
        JsonNode body = HttpSupport.parseJson(mapper, response.body())
                .orElseGet(() -> TextNode.valueOf(response.body() == null ? "" : response.body()));
        deviceStore.writeMetadata(environment, metadata.withResult(success, status, body));

        if (success) {
            out.println("[SUCCESS] Device registered (HTTP " + status + ")");
            String printable = body.isTextual() ? body.asText() : body.toPrettyString();
            if (!printable.isBlank()) {
                out.println(printable);
            }
            return true;
        }
        err.println("[FAILURE] Device registration rejected (HTTP " + status + "): "
                + HttpSupport.describeOAuthError(mapper, response.body()));
        err.println("          Key material kept in " + dir + " (status FAILED); registering the same device "
                + "name again will replace it.");
        return false;
    }

    private static String fixedFieldNames(AppConfig config) {
        List<String> names = new ArrayList<>();
        config.registration().fixedFields().fieldNames().forEachRemaining(names::add);
        return names.isEmpty() ? "none" : String.join(", ", names);
    }
}
