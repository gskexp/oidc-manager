package com.example.b2b;

import lombok.RequiredArgsConstructor;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/** Supplies the callback's PEM identity directly to TLS without a keystore. */
@RequiredArgsConstructor
final class PemKeyManager extends X509ExtendedKeyManager {
    private final PrivateKey privateKey;
    private final X509Certificate certificate;

    @Override public String[] getClientAliases(String type, Principal[] issuers) { return null; }
    @Override public String chooseClientAlias(String[] types, Principal[] issuers, Socket socket) { return null; }
    @Override public String[] getServerAliases(String type, Principal[] issuers) {
        return privateKey.getAlgorithm().equalsIgnoreCase(type) ? new String[]{"localhost"} : null;
    }
    @Override public String chooseServerAlias(String type, Principal[] issuers, Socket socket) {
        return getServerAliases(type, issuers) == null ? null : "localhost";
    }
    @Override public String chooseEngineServerAlias(String type, Principal[] issuers, SSLEngine engine) {
        return chooseServerAlias(type, issuers, null);
    }
    @Override public X509Certificate[] getCertificateChain(String alias) {
        return "localhost".equals(alias) ? new X509Certificate[]{certificate} : null;
    }
    @Override public PrivateKey getPrivateKey(String alias) {
        return "localhost".equals(alias) ? privateKey : null;
    }
}
