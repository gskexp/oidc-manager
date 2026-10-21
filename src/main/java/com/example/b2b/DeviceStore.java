package com.example.b2b;

import com.example.b2b.config.Environment;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Persists device key material and metadata under {@code data/{env}/devices/<device>/}:
 * <ul>
 *   <li>{@code keystore.jks} – JKS holding the private key + self-signed certificate</li>
 *   <li>{@code private-key.pem}, {@code public-key.pem}, {@code certificate.pem} – PEM copies</li>
 *   <li>{@code device.json} – {@link DeviceMetadata}</li>
 * </ul>
 */
public final class DeviceStore {

    public static final String KEYSTORE_FILE = "keystore.jks";
    private static final String METADATA_FILE = "device.json";

    private final Path dataDir;
    private final ObjectMapper mapper;
    private final PrintStream err;

    public DeviceStore(Path dataDir, ObjectMapper mapper, PrintStream err) {
        this.dataDir = dataDir;
        this.mapper = mapper;
        this.err = err;
    }

    /** Derives a filesystem- and keystore-safe alias from a device name. */
    public static String aliasFor(String deviceName) {
        String alias = deviceName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return alias.isEmpty() ? "device" : alias;
    }

    public Path deviceDir(Environment environment, String alias) {
        return dataDir.resolve(environment.id()).resolve("devices").resolve(alias);
    }

    public Path keystorePath(Environment environment, String alias) {
        return deviceDir(environment, alias).resolve(KEYSTORE_FILE);
    }

    /** Returns the stored metadata for the device that would use {@code alias}, if any. */
    public Optional<DeviceMetadata> find(Environment environment, String alias) {
        return readMetadata(deviceDir(environment, alias).resolve(METADATA_FILE));
    }

    /** Lists successfully registered devices whose keystore is present, sorted by name. */
    public List<DeviceMetadata> listRegistered(Environment environment) {
        Path devicesDir = dataDir.resolve(environment.id()).resolve("devices");
        if (!Files.isDirectory(devicesDir)) {
            return List.of();
        }
        List<DeviceMetadata> devices = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(devicesDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> readMetadata(dir.resolve(METADATA_FILE))
                    .filter(DeviceMetadata::isRegistered)
                    .filter(meta -> Files.isRegularFile(dir.resolve(KEYSTORE_FILE)))
                    .ifPresent(devices::add));
        } catch (IOException e) {
            err.println("[WARN] Unable to list devices in " + devicesDir + ": " + e.getMessage());
        }
        devices.sort(Comparator.comparing(DeviceMetadata::deviceName, String.CASE_INSENSITIVE_ORDER));
        return devices;
    }

    /**
     * Writes the keystore, PEM files and metadata, replacing any previous files for this device.
     *
     * @return the device directory
     */
    public Path save(Environment environment, DeviceMetadata metadata, KeyPair keyPair, X509Certificate certificate,
                     String keystorePassword) throws B2bClientException {
        Path dir = deviceDir(environment, metadata.alias());
        char[] password = keystorePassword.toCharArray();
        try {
            Files.createDirectories(dir);
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, null);
            keyStore.setKeyEntry(metadata.alias(), keyPair.getPrivate(), password, new Certificate[]{certificate});
            try (OutputStream out = Files.newOutputStream(dir.resolve(KEYSTORE_FILE))) {
                keyStore.store(out, password);
            }
            Files.writeString(dir.resolve("private-key.pem"), pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            Files.writeString(dir.resolve("public-key.pem"), pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
            Files.writeString(dir.resolve("certificate.pem"), pem("CERTIFICATE", certificate.getEncoded()));
            writeMetadata(environment, metadata);
            return dir;
        } catch (IOException e) {
            throw new B2bClientException("Unable to save device key material to " + dir + ": " + e.getMessage(), e);
        } catch (GeneralSecurityException e) {
            throw new B2bClientException("Unable to create device keystore: " + e.getMessage(), e);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    public void writeMetadata(Environment environment, DeviceMetadata metadata) throws B2bClientException {
        Path file = deviceDir(environment, metadata.alias()).resolve(METADATA_FILE);
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
            err.println("[WARN] Ignoring unreadable device metadata " + file + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }
}
