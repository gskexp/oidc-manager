package com.example.b2b;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TlsTestClient {
    static HttpClient trusting(X509Certificate expected) throws GeneralSecurityException {
        var trust = new X509TrustManager() {
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{expected}; }
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                checkServerTrusted(chain, authType);
            }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (chain.length == 0 || !chain[0].equals(expected)) throw new CertificateException("Unexpected certificate");
                chain[0].checkValidity();
            }
        };
        var context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{trust}, null);
        return HttpClient.newBuilder().sslContext(context).build();
    }
}
