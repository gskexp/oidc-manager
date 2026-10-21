package com.example.b2b;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds a minimal self-signed X.509 v3 certificate (SHA256withRSA) using only the JDK.
 *
 * <p>A JKS {@code PrivateKeyEntry} must be stored with a certificate chain, and the JDK has no
 * public API for issuing certificates, so the DER structure (RFC 5280 §4.1) is encoded here
 * directly. Only RSA keys are supported.</p>
 */
public final class SelfSignedCertificate {

    private static final String OID_SHA256_WITH_RSA = "1.2.840.113549.1.1.11";
    private static final String OID_COMMON_NAME = "2.5.4.3";
    private static final String OID_ORGANISATIONAL_UNIT = "2.5.4.11";
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'");
    private static final DateTimeFormatter GENERALIZED_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'");

    private SelfSignedCertificate() {
    }

    /**
     * Creates a certificate with subject/issuer {@code OU=<organisationalUnit>, CN=<commonName>}.
     *
     * @param keyPair            RSA key pair; the private key signs the certificate
     * @param commonName         subject CN
     * @param organisationalUnit subject OU
     * @param validity           validity period starting now
     */
    public static X509Certificate create(KeyPair keyPair, String commonName, String organisationalUnit,
                                         Duration validity) throws GeneralSecurityException {
        if (!(keyPair.getPublic() instanceof RSAPublicKey)) {
            throw new GeneralSecurityException("Only RSA key pairs are supported, got " + keyPair.getPublic().getAlgorithm());
        }
        Instant notBefore = Instant.now().minus(Duration.ofMinutes(5));
        Instant notAfter = notBefore.plus(validity);
        byte[] algorithm = sequence(oid(OID_SHA256_WITH_RSA), new byte[]{0x05, 0x00});
        byte[] name = sequence(
                rdn(OID_ORGANISATIONAL_UNIT, organisationalUnit),
                rdn(OID_COMMON_NAME, commonName));

        byte[] tbs = sequence(
                tlv(0xA0, integer(BigInteger.valueOf(2))),                    // version: v3
                integer(new BigInteger(63, new SecureRandom()).add(BigInteger.ONE)),  // serial
                algorithm,
                name,                                                          // issuer
                sequence(time(notBefore), time(notAfter)),
                name,                                                          // subject
                keyPair.getPublic().getEncoded());                             // SubjectPublicKeyInfo

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(tbs);
        byte[] der = sequence(tbs, algorithm, bitString(signer.sign()));

        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
        certificate.verify(keyPair.getPublic());
        return certificate;
    }

    private static byte[] rdn(String attributeOid, String value) {
        return tlv(0x31, sequence(oid(attributeOid), tlv(0x0C, value.getBytes(StandardCharsets.UTF_8))));
    }

    private static byte[] time(Instant instant) {
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        // RFC 5280: UTCTime through 2049, GeneralizedTime from 2050.
        return utc.getYear() < 2050
                ? tlv(0x17, UTC_TIME.format(utc).getBytes(StandardCharsets.US_ASCII))
                : tlv(0x18, GENERALIZED_TIME.format(utc).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] integer(BigInteger value) {
        return tlv(0x02, value.toByteArray());
    }

    private static byte[] bitString(byte[] value) {
        byte[] content = new byte[value.length + 1];
        System.arraycopy(value, 0, content, 1, value.length); // leading 0 = no unused bits
        return tlv(0x03, content);
    }

    private static byte[] oid(String dotted) {
        String[] parts = dotted.split("\\.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]));
        for (int i = 2; i < parts.length; i++) {
            long arc = Long.parseLong(parts[i]);
            int groups = Math.max(1, (64 - Long.numberOfLeadingZeros(arc) + 6) / 7);
            for (int g = groups - 1; g >= 0; g--) {
                int bits = (int) ((arc >> (7 * g)) & 0x7F);
                out.write(g == 0 ? bits : bits | 0x80);
            }
        }
        return tlv(0x06, out.toByteArray());
    }

    private static byte[] sequence(byte[]... parts) {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            content.writeBytes(part);
        }
        return tlv(0x30, content.toByteArray());
    }

    private static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(value.length + 6);
        out.write(tag);
        int length = value.length;
        if (length < 0x80) {
            out.write(length);
        } else {
            int bytes = (32 - Integer.numberOfLeadingZeros(length) + 7) / 8;
            out.write(0x80 | bytes);
            for (int i = bytes - 1; i >= 0; i--) {
                out.write((length >> (8 * i)) & 0xFF);
            }
        }
        out.writeBytes(value);
        return out.toByteArray();
    }
}
