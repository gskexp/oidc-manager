package com.example.b2b;

import lombok.RequiredArgsConstructor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Builds signed JWT client assertions (RFC 7523 / {@code private_key_jwt}) using only the JDK.
 * Device RSA keys are signed with RS256.
 */
@RequiredArgsConstructor
public final class JwtSigner {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Duration ASSERTION_LIFETIME = Duration.ofMinutes(5);

    private final ObjectMapper mapper;

    /**
     * Creates a client assertion where {@code iss} and {@code sub} are the client id.
     *
     * @param privateKey signing key
     * @param keyId      value for the {@code kid} header (the key identifier)
     * @param clientId   OAuth client id
     * @param audience   token endpoint the assertion is intended for
     */
    public String createClientAssertion(PrivateKey privateKey, String keyId, String clientId, String audience)
            throws B2bClientException {
        var now = Instant.now();

        var header = mapper.createObjectNode()
                .put("alg", "RS256")
                .put("typ", "JWT")
                .put("kid", keyId);
        var claims = mapper.createObjectNode()
                .put("iss", clientId)
                .put("sub", clientId)
                .put("aud", audience)
                .put("jti", UUID.randomUUID().toString())
                .put("iat", now.getEpochSecond())
                .put("nbf", now.getEpochSecond())
                .put("exp", now.plus(ASSERTION_LIFETIME).getEpochSecond());

        try {
            var signingInput = encode(mapper.writeValueAsBytes(header)) + "." + encode(mapper.writeValueAsBytes(claims));
            var signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + encode(signature.sign());
        } catch (JsonProcessingException e) {
            throw new B2bClientException("Failed to serialise JWT client assertion", e);
        } catch (GeneralSecurityException e) {
            throw new B2bClientException("Failed to sign JWT client assertion with key '" + keyId + "': "
                    + e.getMessage(), e);
        }
    }

    private static String encode(byte[] bytes) {
        return B64URL.encodeToString(bytes);
    }

}
