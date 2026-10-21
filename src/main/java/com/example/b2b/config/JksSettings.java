package com.example.b2b.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Keystore settings for an environment.
 *
 * @param jksPath     path to the JKS (or PKCS12) keystore, relative to the working directory
 * @param jksPassword keystore password
 * @param keyPassword optional private key password; defaults to {@code jksPassword}
 * @param keys        selectable keys / service accounts
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JksSettings(String jksPath, String jksPassword, String keyPassword, List<KeyEntry> keys) {

    public JksSettings {
        keys = keys == null ? List.of() : List.copyOf(keys);
        if (keyPassword == null || keyPassword.isEmpty()) {
            keyPassword = jksPassword;
        }
    }

    /** Masks secrets so the record can never leak them through logging. */
    @Override
    public String toString() {
        return "JksSettings[jksPath=" + jksPath + ", jksPassword=***, keys=" + keys + "]";
    }
}
