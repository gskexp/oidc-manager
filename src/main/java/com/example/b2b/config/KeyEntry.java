package com.example.b2b.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A selectable key / service account.
 *
 * @param name  friendly name shown in the menu
 * @param alias alias of the key pair inside the keystore
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeyEntry(String name, String alias) {
}
