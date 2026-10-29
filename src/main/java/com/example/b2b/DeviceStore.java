package com.example.b2b;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

import com.example.b2b.config.Environment;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Persists device key material and metadata under {@code data/{env}/devices/<device>/}:
 * <ul>
 *   <li>{@code jwk.json} – public RSA signing key in JWK format</li>
 *   <li>{@code private-key.pem}, {@code public-key.pem} – PEM copies</li>
 *   <li>{@code device.json} – {@link DeviceMetadata}</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public final class DeviceStore {

    private static final String METADATA_FILE = "device.json";

    private final Path dataDir;
    private final ObjectMapper mapper;

    /** Derives a filesystem-safe alias from a device name. */
    public static String aliasFor(String deviceName) {
        var alias = deviceName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return alias.isEmpty() ? "device" : alias;
    }

    public Path deviceDir(Environment environment, String alias) {
        return dataDir.resolve(environment.id()).resolve("devices").resolve(alias);
    }

    public Path signingKeyPath(Environment environment, String alias) {
        return deviceDir(environment, alias).resolve("private-key.pem");
    }

    /** Returns the stored metadata for the device that would use {@code alias}, if any. */
    public Optional<DeviceMetadata> find(Environment environment, String alias) {
        return readMetadata(deviceDir(environment, alias).resolve(METADATA_FILE));
    }

    /** Lists successfully registered devices whose private PEM key is present, sorted by name. */
    public List<DeviceMetadata> listRegistered(Environment environment) {
        var devicesDir = dataDir.resolve(environment.id()).resolve("devices");
        if (!Files.isDirectory(devicesDir)) {
            return List.of();
        }
        var devices = new ArrayList<DeviceMetadata>();
        try (var dirs = Files.list(devicesDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> readMetadata(dir.resolve(METADATA_FILE))
                    .filter(DeviceMetadata::isRegistered)
                    .filter(meta -> Files.isRegularFile(dir.resolve("private-key.pem")))
                    .ifPresent(devices::add));
        } catch (IOException e) {
            log.warn("Unable to list devices in {}: {}", devicesDir, e.getMessage());
        }
        devices.sort(Comparator.comparing(DeviceMetadata::deviceName, String.CASE_INSENSITIVE_ORDER));
        return devices;
    }

    /**
     * Writes the public JWK, PEM files and metadata, replacing any previous files for this device.
     *
     * @return the device directory
     */
    public Path save(Environment environment, DeviceMetadata metadata, KeyPair keyPair) throws B2bClientException {
        var dir = deviceDir(environment, metadata.alias());
        try {
            Files.createDirectories(dir);
            if (!(keyPair.getPublic() instanceof RSAPublicKey rsa)) {
                throw new B2bClientException("Device JWK export requires an RSA key");
            }
            var jwk = mapper.createObjectNode().put("kty", "RSA").put("kid", metadata.alias())
                    .put("use", "sig").put("alg", "RS256")
                    .put("n", unsignedBase64(rsa.getModulus()))
                    .put("e", unsignedBase64(rsa.getPublicExponent()));
            Files.writeString(dir.resolve("jwk.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jwk));
            Files.writeString(dir.resolve("private-key.pem"), pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            Files.writeString(dir.resolve("public-key.pem"), pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
            writeMetadata(environment, metadata);
            return dir;
        } catch (IOException e) {
            throw new B2bClientException("Unable to save device key material to " + dir + ": " + e.getMessage(), e);
        }
    }

    private static String unsignedBase64(BigInteger value) {
        var bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void writeMetadata(Environment environment, DeviceMetadata metadata) throws B2bClientException {
        var file = deviceDir(environment, metadata.alias()).resolve(METADATA_FILE);
        try {
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata));
        } catch (IOException e) {
            throw new B2bClientException("Unable to write device metadata " + file + ": " + e.getMessage(), e);
        }
    }

    private Optional<DeviceMetadata> readMetadata(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(file.toFile(), DeviceMetadata.class));
        } catch (IOException e) {
            log.warn("Ignoring unreadable device metadata {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private static String pem(String type, byte[] der) {
        var body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }
}
