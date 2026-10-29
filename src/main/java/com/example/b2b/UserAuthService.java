package com.example.b2b;

import com.example.b2b.config.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Consumer;

/** Attended browser sign-in followed by authorization-code exchange at the environment token endpoint. */
@Slf4j
@RequiredArgsConstructor
public final class UserAuthService {
    private static final Duration LOGIN_TIMEOUT = Duration.ofMinutes(5);
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Path dataDir;
    private final Consumer<URI> openBrowser;

    public TokenResponse authenticate(AppConfig config) throws B2bClientException, InterruptedException {
        validateClient(config);
        var state = UUID.randomUUID().toString();
        var nonce = UUID.randomUUID().toString();
        try {
            var tls = CallbackTls.loadOrCreate(dataDir);
            log.info("Local HTTPS certificate: {}", dataDir.resolve("callback-tls/certificate.pem"));
            try (var listener = CallbackListener.start(URI.create(config.callbackUri()), state, tls.context())) {
                openBrowser.accept(authorizationUri(config, state, nonce));
                var code = listener.awaitCode(LOGIN_TIMEOUT);
                log.debug("User authorization code: {}", code);
                return exchangeCode(config, code);
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new B2bClientException("Unable to run HTTPS callback listener: " + e.getMessage(), e);
        }
    }

    static URI authorizationUri(AppConfig config, String state, String nonce) {
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("response_type", "code");
        parameters.put("client_id", config.attendedClientId());
        parameters.put("redirect_uri", config.callbackUri());
        parameters.put("scope", "openid");
        parameters.put("state", state);
        parameters.put("nonce", nonce);
        var base = config.authorizeEndpoint();
        return URI.create(base + (base.contains("?") ? "&" : "?") + HttpSupport.formEncode(parameters));
    }

    TokenResponse exchangeCode(AppConfig config, String code) throws B2bClientException, InterruptedException {
        var form = new LinkedHashMap<String, String>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("client_id", config.attendedClientId());
        form.put("client_secret", config.attendedClientSecret());
        form.put("redirect_uri", config.callbackUri());
        return new TokenClient(httpClient, mapper).exchange(config.tokenEndpoint(), form, "User token");
    }

    private static void validateClient(AppConfig config) throws B2bClientException {
        if (config.attendedClientId() == null || config.attendedClientId().isBlank()
                || config.attendedClientSecret() == null || config.attendedClientSecret().isBlank()
                || config.callbackUri() == null || config.callbackUri().isBlank()) {
            throw new B2bClientException("Set attendedClientId, attendedClientSecret and callbackUri in "
                    + config.env() + "-config.json before signing in");
        }
    }
}
