package com.example.b2b;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtSignerTest {
    @Test
    void createsVerifiableRs256AssertionWithExpectedClaims() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        var mapper = new ObjectMapper();
        var jwt = new JwtSigner(mapper).createClientAssertion(
                keyPair.getPrivate(), "device-key", "client", "https://example.com/token");
        var parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        var decoder = Base64.getUrlDecoder();
        var header = mapper.readTree(decoder.decode(parts[0]));
        assertEquals("RS256", header.path("alg").asText());
        assertEquals("device-key", header.path("kid").asText());
        var claims = mapper.readTree(decoder.decode(parts[1]));
        assertEquals("client", claims.path("iss").asText());
        assertEquals("client", claims.path("sub").asText());
        assertEquals("https://example.com/token", claims.path("aud").asText());
        assertEquals(300, claims.path("exp").asLong() - claims.path("iat").asLong());
        var verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verifier.verify(decoder.decode(parts[2])));
    }
}
