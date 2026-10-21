package com.example.b2b.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Endpoints used across the registration and authentication flows.
 *
 * @param registrationEndpoint device registration endpoint (receives the public key)
 * @param b2bTokenEndpoint     token endpoint issuing the B2B (client) token
 * @param oidcAuthEndpoint     OIDC authorization endpoint where the user login starts
 * @param oidcTokenEndpoint    OIDC token endpoint (informational; not called by the default flow)
 * @param finalTokenEndpoint   endpoint exchanging the auth code + B2B token for the final access token
 * @param targetApiEndpoint    API invoked with the final access token
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Endpoints(
        String registrationEndpoint,
        String b2bTokenEndpoint,
        String oidcAuthEndpoint,
        String oidcTokenEndpoint,
        String finalTokenEndpoint,
        String targetApiEndpoint) {
}
