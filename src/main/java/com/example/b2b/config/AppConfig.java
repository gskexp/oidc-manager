package com.example.b2b.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Root of an environment configuration file ({@code data/{env}-config.json}).
 *
 * @param environment human-readable environment name (e.g. "dev")
 * @param endpoints   all HTTP endpoints used by the tool
 * @param oauth       OAuth/OIDC client and user settings
 * @param jks         keystore location, password and the selectable keys
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppConfig(String environment, Endpoints endpoints, OAuthSettings oauth, JksSettings jks) {
}
