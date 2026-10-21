package com.example.b2b;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Arrays;

/**
 * Thin wrapper over a loaded {@link KeyStore} that turns the many low-level keystore exceptions
 * into actionable {@link B2bClientException}s. Supports JKS and PKCS12 (auto-detected).
 */
public final class KeyStoreService {

    private final KeyStore keyStore;
    private final Path path;
    private final char[] keyPassword;

    private KeyStoreService(KeyStore keyStore, Path path, char[] keyPassword) {
        this.keyStore = keyStore;
        this.path = path;
        this.keyPassword = keyPassword;
    }

    /** Loads the keystore holding the given signing key. */
    public static KeyStoreService load(SigningKey key) throws B2bClientException {
        return load(key.keystorePath(), key.storePassword(), key.keyPassword());
    }

    /** Loads a JKS or PKCS12 keystore. */
    public static KeyStoreService load(Path path, String password, String keyPassword) throws B2bClientException {
        if (!Files.isRegularFile(path)) {
            throw new B2bClientException("Keystore not found: " + path.toAbsolutePath()
                    + " (check jks.jksPath in the configuration)");
        }
        char[] storePassword = password.toCharArray();
        try {
            KeyStore keyStore = KeyStore.getInstance(path.toFile(), storePassword);
            return new KeyStoreService(keyStore, path, keyPassword.toCharArray());
        } catch (IOException e) {
            if (e.getCause() instanceof UnrecoverableKeyException) {
                throw new B2bClientException("Unable to open keystore " + path
                        + ": the keystore password is incorrect (check jks.jksPassword)", e);
            }
            throw new B2bClientException("Unable to read keystore " + path + ": " + e.getMessage(), e);
        } catch (KeyStoreException e) {
            throw new B2bClientException("Unrecognised keystore format for " + path
                    + " (expected JKS or PKCS12)", e);
        } catch (NoSuchAlgorithmException | CertificateException e) {
            throw new B2bClientException("Keystore " + path + " could not be verified: " + e.getMessage(), e);
        } finally {
            Arrays.fill(storePassword, '\0');
        }
    }

    /** Returns the public key from the certificate stored under {@code alias}. */
    public PublicKey publicKey(String alias) throws B2bClientException {
        try {
            requireAlias(alias);
            Certificate certificate = keyStore.getCertificate(alias);
            if (certificate == null) {
                throw new B2bClientException("Alias '" + alias + "' in " + path + " has no certificate/public key");
            }
            return certificate.getPublicKey();
        } catch (KeyStoreException e) {
            throw new B2bClientException("Keystore " + path + " is not initialised", e);
        }
    }

    /** Returns the private key stored under {@code alias}. */
    public PrivateKey privateKey(String alias) throws B2bClientException {
        try {
            requireAlias(alias);
            if (!keyStore.isKeyEntry(alias)) {
                throw new B2bClientException("Alias '" + alias + "' in " + path
                        + " is a trusted certificate, not a private key entry");
            }
            Key key = keyStore.getKey(alias, keyPassword);
            if (!(key instanceof PrivateKey privateKey)) {
                throw new B2bClientException("Alias '" + alias + "' in " + path + " does not hold a private key");
            }
            return privateKey;
        } catch (UnrecoverableKeyException e) {
            throw new B2bClientException("Unable to recover private key '" + alias
                    + "': the key password is incorrect (check jks.keyPassword / jks.jksPassword)", e);
        } catch (KeyStoreException e) {
            throw new B2bClientException("Keystore " + path + " is not initialised", e);
        } catch (NoSuchAlgorithmException e) {
            throw new B2bClientException("Unsupported key algorithm for alias '" + alias + "': " + e.getMessage(), e);
        }
    }

    private void requireAlias(String alias) throws KeyStoreException, B2bClientException {
        if (!keyStore.containsAlias(alias)) {
            throw new B2bClientException("Alias '" + alias + "' not found in keystore " + path
                    + " (available: " + String.join(", ", java.util.Collections.list(keyStore.aliases())) + ")");
        }
    }
}
