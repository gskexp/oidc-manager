package com.example.b2b;

import java.nio.file.Path;

/**
 * A key selectable for the token flow: either a key listed in the environment config or a
 * registered device.
 *
 * @param name          friendly name shown in the menu
 * @param alias         alias of the key pair inside the keystore
 * @param keystorePath  keystore holding the key
 * @param storePassword keystore password
 * @param keyPassword   private key password
 */
public record SigningKey(String name, String alias, Path keystorePath, String storePassword, String keyPassword) {

    /** Masks secrets so the record can never leak them through logging. */
    @Override
    public String toString() {
        return "SigningKey[name=" + name + ", alias=" + alias + ", keystorePath=" + keystorePath + "]";
    }
}
