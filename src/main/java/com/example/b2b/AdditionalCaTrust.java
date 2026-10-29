package com.example.b2b;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

/** Adds PEM CA certificates to JVM trust without modifying global trust or device key storage. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AdditionalCaTrust {
    static HttpClient.Builder configure(HttpClient.Builder builder, Path dataDir) {
        var directory = dataDir.resolve("trusted-certs");
        if (!Files.exists(directory)) return builder;
        try {
            var anchors = loadAnchors(directory);
            if (anchors.isEmpty()) return builder;
            var defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            defaults.init((KeyStore) null);
            var parameters = new PKIXBuilderParameters(anchors, new X509CertSelector());
            parameters.setRevocationEnabled(false);
            var additional = TrustManagerFactory.getInstance("PKIX");
            additional.init(new CertPathTrustManagerParameters(parameters));
            var context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new CombinedTrustManager(manager(defaults), manager(additional))}, null);
            log.debug("Loaded {} additional trusted certificates from {}", anchors.size(), directory);
            return builder.sslContext(context);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Unable to load TLS CA certificates from " + directory + ": " + e.getMessage(), e);
        }
    }

    private static java.util.Set<TrustAnchor> loadAnchors(Path directory) throws IOException, GeneralSecurityException {
        var anchors = new HashSet<TrustAnchor>();
        var factory = CertificateFactory.getInstance("X.509");
        try (var files = Files.list(directory)) {
            for (var file : files.filter(Files::isRegularFile).filter(AdditionalCaTrust::isCertificate).sorted().toList()) {
                try (var input = Files.newInputStream(file)) {
                    var certificates = factory.generateCertificates(input);
                    if (certificates.isEmpty()) throw new IOException("No certificates in " + file);
                    for (var certificate : certificates) anchors.add(new TrustAnchor((X509Certificate) certificate, null));
                }
            }
        }
        return anchors;
    }

    private static boolean isCertificate(Path path) {
        var name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".pem") || name.endsWith(".crt") || name.endsWith(".cer");
    }

    private static X509ExtendedTrustManager manager(TrustManagerFactory factory) throws GeneralSecurityException {
        return Arrays.stream(factory.getTrustManagers()).filter(X509ExtendedTrustManager.class::isInstance)
                .map(X509ExtendedTrustManager.class::cast).findFirst()
                .orElseThrow(() -> new GeneralSecurityException("TLS provider does not supply an extended X.509 trust manager"));
    }
}
