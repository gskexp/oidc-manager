package com.example.b2b;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Builds signed JWT client assertions (RFC 7523 / {@code private_key_jwt}) using only the JDK.
 * RSA keys are signed with RS256; EC keys with ES256/ES384/ES512 depending on the curve.
 */
public final class JwtSigner {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Duration ASSERTION_LIFETIME = Duration.ofMinutes(5);

    private final ObjectMapper mapper;

    public JwtSigner(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Creates a client assertion where {@code iss} and {@code sub} are the client id.
     *
     * @param privateKey signing key
     * @param keyId      value for the {@code kid} header (the keystore alias)
     * @param clientId   OAuth client id
     * @param audience   token endpoint the assertion is intended for
     */
    public String createClientAssertion(PrivateKey privateKey, String keyId, String clientId, String audience)
            throws B2bClientException {
        Algorithm alg = Algorithm.forKey(privateKey);
        Instant now = Instant.now();

        ObjectNode header = mapper.createObjectNode()
                .put("alg", alg.jwsName)
                .put("typ", "JWT")
                .put("kid", keyId);
        ObjectNode claims = mapper.createObjectNode()
                .put("iss", clientId)
                .put("sub", clientId)
                .put("aud", audience)
                .put("jti", UUID.randomUUID().toString())
                .put("iat", now.getEpochSecond())
                .put("nbf", now.getEpochSecond())
                .put("exp", now.plus(ASSERTION_LIFETIME).getEpochSecond());

        try {
            String signingInput = encode(mapper.writeValueAsBytes(header)) + "." + encode(mapper.writeValueAsBytes(claims));
            Signature signature = Signature.getInstance(alg.jcaName);
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

    /** JWS algorithms supported for client assertions. */
    private enum Algorithm {
        RS256("RS256", "SHA256withRSA"),
        // The P1363 variants emit the raw R||S signature format required by JWS (RFC 7518 §3.4).
        ES256("ES256", "SHA256withECDSAinP1363Format"),
        ES384("ES384", "SHA384withECDSAinP1363Format"),
        ES512("ES512", "SHA512withECDSAinP1363Format");

        private final String jwsName;
        private final String jcaName;

        Algorithm(String jwsName, String jcaName) {
            this.jwsName = jwsName;
            this.jcaName = jcaName;
        }

        static Algorithm forKey(PrivateKey key) throws B2bClientException {
            if (key instanceof RSAPrivateKey) {
                return RS256;
            }
            if (key instanceof ECPrivateKey ec) {
                int bits = ec.getParams().getOrder().bitLength();
                if (bits <= 256) {
                    return ES256;
                }
                return bits <= 384 ? ES384 : ES512;
            }
            throw new B2bClientException("Unsupported private key type '" + key.getAlgorithm()
                    + "' for JWT signing (use an RSA or EC key)");
        }
    }
}
