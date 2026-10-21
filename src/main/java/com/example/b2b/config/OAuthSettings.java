package com.example.b2b.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * OAuth 2.0 / OIDC parameters. Optional fields receive defaults in the compact constructor so the
 * rest of the code never has to null-check them.
 *
 * @param clientId           OAuth client identifier
 * @param redirectUri        registered redirect URI; the auth code is captured when the IdP redirects here
 * @param username           end-user login name for the OIDC login form
 * @param password           end-user password for the OIDC login form
 * @param scope              scopes requested in the OIDC authorization request (default {@code openid})
 * @param b2bScope           optional scope for the B2B token request
 * @param b2bAuthMethod      {@code private_key_jwt} (signed client assertion, default) or
 *                           {@code client_secret_post} (form-based credentials)
 * @param clientSecret       client secret, only used with {@code client_secret_post}
 * @param loginUsernameField name of the username input on the login form (default {@code username})
 * @param loginPasswordField name of the password input on the login form (default {@code password})
 * @param b2bTokenPlacement  how the B2B token is sent to the final token endpoint:
 *                           {@code header} (Authorization: Bearer, default) or {@code form}
 * @param b2bTokenParamName  form parameter name used when {@code b2bTokenPlacement} is {@code form}
 * @param usePkce            whether to send a PKCE (S256) challenge/verifier (default true)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OAuthSettings(
        String clientId,
        String redirectUri,
        String username,
        String password,
        String scope,
        String b2bScope,
        String b2bAuthMethod,
        String clientSecret,
        String loginUsernameField,
        String loginPasswordField,
        String b2bTokenPlacement,
        String b2bTokenParamName,
        Boolean usePkce) {

    public static final String AUTH_PRIVATE_KEY_JWT = "private_key_jwt";
    public static final String AUTH_CLIENT_SECRET_POST = "client_secret_post";
    public static final String PLACEMENT_HEADER = "header";
    public static final String PLACEMENT_FORM = "form";

    public OAuthSettings {
        scope = defaultIfBlank(scope, "openid");
        b2bAuthMethod = defaultIfBlank(b2bAuthMethod, AUTH_PRIVATE_KEY_JWT);
        loginUsernameField = defaultIfBlank(loginUsernameField, "username");
        loginPasswordField = defaultIfBlank(loginPasswordField, "password");
        b2bTokenPlacement = defaultIfBlank(b2bTokenPlacement, PLACEMENT_HEADER);
        b2bTokenParamName = defaultIfBlank(b2bTokenParamName, "b2b_token");
        usePkce = usePkce == null || usePkce;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Masks secrets so the record can never leak them through logging. */
    @Override
    public String toString() {
        return "OAuthSettings[clientId=" + clientId + ", redirectUri=" + redirectUri
                + ", username=" + username + ", password=***, clientSecret=***]";
    }
}
