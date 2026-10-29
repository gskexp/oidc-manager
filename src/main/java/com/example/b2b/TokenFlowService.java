package com.example.b2b;

import com.example.b2b.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Coordinates unattended, attended and final tokens using the same environment and device. */
@Slf4j
@RequiredArgsConstructor
public final class TokenFlowService {
    private final TokenExchangeService exchanges;
    private final UserAuthService userAuth;
    private final JwtSigner signer;

    public TokenResponse authenticate(AppConfig config, SigningKey key)
            throws B2bClientException, InterruptedException {
        var privateKey = key.privateKey();
        var assertion = signer.createClientAssertion(privateKey, key.alias(), config.clientId(), config.tokenEndpoint());
        var b2b = exchanges.unattended(config, assertion);
        log.debug("Unattended B2B access token: {}", b2b.accessToken());
        var user = userAuth.authenticate(config);
        log.debug("User access token: {}", user.accessToken());
        var finalAssertion = signer.createClientAssertion(privateKey, key.alias(), config.clientId(), config.tokenEndpoint());
        return exchanges.finalToken(config, user.accessToken(), b2b.accessToken(), finalAssertion);
    }
}
