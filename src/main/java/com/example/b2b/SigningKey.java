package com.example.b2b;

import java.nio.file.Path;

/** A registered device signing key backed by an unencrypted RSA PKCS8 PEM file. */
public record SigningKey(String name, String alias, Path privateKeyPath) {

    /** Loads the device private key for signing. */
    public java.security.PrivateKey privateKey() throws B2bClientException {
        try {
            var pem = java.nio.file.Files.readString(privateKeyPath);
            var der = java.util.Base64.getDecoder().decode(pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", ""));
            return java.security.KeyFactory.getInstance("RSA").generatePrivate(
                    new java.security.spec.PKCS8EncodedKeySpec(der));
        } catch (java.io.IOException | java.security.GeneralSecurityException | IllegalArgumentException e) {
            throw new B2bClientException("Unable to load device private key: " + privateKeyPath, e);
        }
    }

}
