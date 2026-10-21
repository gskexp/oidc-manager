package com.example.b2b;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Contents of {@code data/{env}/devices/<device>/device.json}.
 *
 * @param deviceName           name entered at registration
 * @param alias                keystore alias of the device key pair
 * @param raNumber             RA number entered at registration
 * @param environment          environment id (dev/test/prod)
 * @param keyAlgorithm         key algorithm, e.g. RSA
 * @param keySize              key size in bits
 * @param createdAt            ISO-8601 time the key pair was generated
 * @param status               {@link #STATUS_PENDING}, {@link #STATUS_REGISTERED} or {@link #STATUS_FAILED}
 * @param registeredAt         ISO-8601 time the registration endpoint responded
 * @param lastHttpStatus       HTTP status of the registration call
 * @param registrationResponse response body (JSON, or a text node)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceMetadata(
        String deviceName,
        String alias,
        String raNumber,
        String environment,
        String keyAlgorithm,
        int keySize,
        String createdAt,
        String status,
        String registeredAt,
        Integer lastHttpStatus,
        JsonNode registrationResponse) {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REGISTERED = "REGISTERED";
    public static final String STATUS_FAILED = "FAILED";

    /** Metadata for a freshly generated, not-yet-registered key pair. */
    public static DeviceMetadata pending(String deviceName, String alias, String raNumber, String environment,
                                         String keyAlgorithm, int keySize) {
        return new DeviceMetadata(deviceName, alias, raNumber, environment, keyAlgorithm, keySize,
                Instant.now().toString(), STATUS_PENDING, null, null, null);
    }

    /** Returns a copy recording the outcome of the registration call. */
    public DeviceMetadata withResult(boolean success, int httpStatus, JsonNode response) {
        return new DeviceMetadata(deviceName, alias, raNumber, environment, keyAlgorithm, keySize, createdAt,
                success ? STATUS_REGISTERED : STATUS_FAILED, Instant.now().toString(), httpStatus, response);
    }

    @JsonIgnore
    public boolean isRegistered() {
        return STATUS_REGISTERED.equals(status);
    }
}
