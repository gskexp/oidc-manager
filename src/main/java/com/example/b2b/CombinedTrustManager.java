package com.example.b2b;

import lombok.RequiredArgsConstructor;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.stream.Stream;

/** Accepts JVM-trusted or configured CA chains while retaining the TLS connection context. */
@RequiredArgsConstructor
final class CombinedTrustManager extends X509ExtendedTrustManager {
    private final X509ExtendedTrustManager defaults;
    private final X509ExtendedTrustManager additional;

    @FunctionalInterface
    private interface Check {
        void verify(X509ExtendedTrustManager manager) throws CertificateException;
    }

    private void check(Check check) throws CertificateException {
        try {
            check.verify(defaults);
        } catch (CertificateException original) {
            try {
                check.verify(additional);
            } catch (CertificateException failed) {
                original.addSuppressed(failed);
                throw original;
            }
        }
    }

    @Override public void checkServerTrusted(X509Certificate[] chain, String type) throws CertificateException {
        check(manager -> manager.checkServerTrusted(chain, type));
    }
    @Override public void checkClientTrusted(X509Certificate[] chain, String type) throws CertificateException {
        check(manager -> manager.checkClientTrusted(chain, type));
    }
    @Override public void checkServerTrusted(X509Certificate[] chain, String type, Socket socket) throws CertificateException {
        check(manager -> manager.checkServerTrusted(chain, type, socket));
    }
    @Override public void checkClientTrusted(X509Certificate[] chain, String type, Socket socket) throws CertificateException {
        check(manager -> manager.checkClientTrusted(chain, type, socket));
    }
    @Override public void checkServerTrusted(X509Certificate[] chain, String type, SSLEngine engine) throws CertificateException {
        check(manager -> manager.checkServerTrusted(chain, type, engine));
    }
    @Override public void checkClientTrusted(X509Certificate[] chain, String type, SSLEngine engine) throws CertificateException {
        check(manager -> manager.checkClientTrusted(chain, type, engine));
    }
    @Override public X509Certificate[] getAcceptedIssuers() {
        return Stream.concat(Stream.of(defaults.getAcceptedIssuers()), Stream.of(additional.getAcceptedIssuers()))
                .toArray(X509Certificate[]::new);
    }
}
