package com.example.b2b;

import com.example.b2b.config.AppConfig;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;

/** Exact unattended and final exchange form fields from the provided JavaScript samples. */
@RequiredArgsConstructor
public final class TokenExchangeService {
    private final TokenClient tokenClient;

    public TokenResponse unattended(AppConfig config, String assertion)
            throws B2bClientException, InterruptedException {
        require("clientId", config.clientId());
        require("assertion", assertion);
        var form = new LinkedHashMap<String, String>();
        form.put("client_id", config.clientId());
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.put("assertion", assertion);
        return tokenClient.exchange(config.tokenEndpoint(), form, "Unattended B2B token");
    }

    public TokenResponse finalToken(AppConfig config, String userToken, String b2bToken, String assertion)
            throws B2bClientException, InterruptedException {
        require("clientId", config.clientId());
        require("audience", config.audience());
        require("userToken", userToken);
        require("b2bToken", b2bToken);
        require("assertion", assertion);
        var form = new LinkedHashMap<String, String>();
        form.put("client_id", config.clientId());
        form.put("client_assertion", assertion);
        form.put("audience", config.audience());
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        form.put("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        form.put("subject_token", userToken);
        form.put("actor_token_type", "urn:ietf:params:oauth:token-type:jwt");
        form.put("actor_token", b2bToken);
        return tokenClient.exchange(config.tokenEndpoint(), form, "Final token exchange");
    }

    private static void require(String name, String value) throws B2bClientException {
        if (value == null || value.isBlank()) throw new B2bClientException("Set " + name + " before token exchange");
    }
}
