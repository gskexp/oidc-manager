package com.example.b2b;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AdditionalCaTrustTest {
    @TempDir Path dir;

    @Test
    void customPemTrustAllowsHttpsThatDefaultJavaTrustRejects() throws Exception {
        var tls = CallbackTls.loadOrCreate(dir);
        try (var listener = CallbackListener.start(URI.create("https://localhost:0/callback"), "state", tls.context())) {
            var request = HttpRequest.newBuilder(URI.create(listener.uri() + "?code=code&state=state")).build();
            var original = AdditionalCaTrust.configure(HttpClient.newBuilder(), dir).build();
            assertThrows(javax.net.ssl.SSLHandshakeException.class,
                    () -> original.send(request, HttpResponse.BodyHandlers.ofString()));
            trustCertificate(tls.certificate());
            var configured = AdditionalCaTrust.configure(HttpClient.newBuilder(), dir).build();
            assertEquals(200, configured.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals("code", listener.awaitCode(java.time.Duration.ofSeconds(1)));
        }
    }

    @Test
    void trustedCertificateStillRequiresMatchingHostname() throws Exception {
        var tls = CallbackTls.loadOrCreate(dir);
        var name = new org.bouncycastle.asn1.x500.X500Name("CN=other.example.com");
        var builder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(name,
                java.math.BigInteger.ONE, tls.certificate().getNotBefore(), tls.certificate().getNotAfter(),
                name, tls.certificate().getPublicKey());
        var signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA").build(tls.privateKey());
        var certificate = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(builder.build(signer));
        trustCertificate(certificate);
        var mismatched = new CallbackTls(tls.privateKey(), certificate);
        try (var listener = CallbackListener.start(URI.create("https://localhost:0/callback"), "state", mismatched.context())) {
            var client = AdditionalCaTrust.configure(HttpClient.newBuilder(), dir).build();
            assertThrows(javax.net.ssl.SSLHandshakeException.class, () -> client.send(
                    HttpRequest.newBuilder(listener.uri()).build(), HttpResponse.BodyHandlers.ofString()));
        }
    }

    @Test
    void malformedCertificateReportsItsConfigurationDirectory() throws Exception {
        Files.createDirectories(dir.resolve("trusted-certs"));
        Files.writeString(dir.resolve("trusted-certs/invalid.pem"), "not a certificate");
        var error = assertThrows(IllegalArgumentException.class,
                () -> AdditionalCaTrust.configure(HttpClient.newBuilder(), dir));
        assertTrue(error.getMessage().contains("trusted-certs"));
    }

    private void trustCertificate(X509Certificate certificate) throws Exception {
        var directory = dir.resolve("trusted-certs");
        Files.createDirectories(directory);
        var pem = "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder().encodeToString(certificate.getEncoded())
                + "\n-----END CERTIFICATE-----\n";
        Files.writeString(directory.resolve("issuer.pem"), pem);
    }
}
