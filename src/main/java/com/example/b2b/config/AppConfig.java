package com.example.b2b.config;

/** Environment endpoints and attended/unattended client settings for token exchanges. */
public record AppConfig(String env, String label, String issuerUrlBase, String tokenEndpoint,
                        String authorizeEndpoint, String attendedClientId, String attendedClientSecret,
                        String callbackUri, String clientId, String audience) {
}
