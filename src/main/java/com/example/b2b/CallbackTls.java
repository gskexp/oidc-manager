package com.example.b2b;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** PEM identity used only by the localhost HTTPS listener, separate from device keys. */
record CallbackTls(PrivateKey privateKey, X509Certificate certificate) {
    static CallbackTls loadOrCreate(Path dataDir) throws IOException, GeneralSecurityException {
        var dir = dataDir.resolve("callback-tls");
        Files.createDirectories(dir);
        var keyFile = dir.resolve("private-key.pem");
        var certFile = dir.resolve("certificate.pem");
        if (Files.isRegularFile(keyFile) && Files.isRegularFile(certFile)) {
            return load(keyFile, certFile);
        }
        var identity = generate();
        writePem(keyFile, identity.privateKey());
        writePem(certFile, identity.certificate());
        return identity;
    }

    private static CallbackTls load(Path keyFile, Path certFile) throws IOException, GeneralSecurityException {
        PrivateKey key;
        try (var parser = new PEMParser(Files.newBufferedReader(keyFile))) {
            var object = parser.readObject();
            var converter = new JcaPEMKeyConverter();
            if (object instanceof PEMKeyPair pair) {
                key = converter.getKeyPair(pair).getPrivate();
            } else if (object instanceof PrivateKeyInfo info) {
                key = converter.getPrivateKey(info);
            } else {
                throw new IOException("Expected a PEM private key in " + keyFile);
            }
        }
        try (var input = Files.newInputStream(certFile)) {
            var cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            cert.checkValidity();
            return new CallbackTls(key, cert);
        }
    }

    private static CallbackTls generate() throws GeneralSecurityException, IOException {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var name = new X500Name("CN=localhost");
        var now = Instant.now();
        var builder = new JcaX509v3CertificateBuilder(name, new BigInteger(128, new SecureRandom()).setBit(127),
                Date.from(now.minusSeconds(300)), Date.from(now.plus(Duration.ofDays(365))), name, pair.getPublic());
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName[]{
                new GeneralName(GeneralName.dNSName, "localhost"),
                new GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                new GeneralName(GeneralName.iPAddress, "::1")
        }));
        try {
            var signer = new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
            var cert = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
            return new CallbackTls(pair.getPrivate(), cert);
        } catch (OperatorCreationException e) {
            throw new GeneralSecurityException("Unable to create localhost TLS certificate", e);
        }
    }

    private static void writePem(Path file, Object object) throws IOException {
        try (var writer = new JcaPEMWriter(Files.newBufferedWriter(file))) {
            writer.writeObject(object);
        }
    }

    SSLContext context() throws GeneralSecurityException {
        var context = SSLContext.getInstance("TLS");
        context.init(new KeyManager[]{new PemKeyManager(privateKey, certificate)}, null, null);
        return context;
    }
}
