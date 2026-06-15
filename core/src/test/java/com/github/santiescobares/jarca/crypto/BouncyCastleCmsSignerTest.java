package com.github.santiescobares.jarca.crypto;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class BouncyCastleCmsSignerTest {

    private static X509Certificate certificate;
    private static PrivateKey privateKey;

    @BeforeAll
    static void generateTestCert() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        privateKey = kp.getPrivate();

        X500Name subject = new X500Name("CN=Test, O=j-arca, C=AR");
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(kp.getPublic().getEncoded());
        Date notBefore = new Date(System.currentTimeMillis() - 1000);
        Date notAfter = new Date(System.currentTimeMillis() + 3_600_000L);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(privateKey);

        certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(
                        new X509v3CertificateBuilder(subject, BigInteger.ONE,
                                notBefore, notAfter, subject, spki)
                                .build(signer)
                );
    }

    @Test
    void sign_producesNonEmptyDerBytes() {
        BouncyCastleCmsSigner cms = new BouncyCastleCmsSigner(certificate, privateKey);
        byte[] data = "<?xml version=\"1.0\"?><loginTicketRequest/>".getBytes(StandardCharsets.UTF_8);
        byte[] signed = cms.sign(data);

        assertNotNull(signed);
        assertTrue(signed.length > 0, "Signed CMS should not be empty");
    }

    @Test
    void sign_producesValidCmsSignedData() throws Exception {
        BouncyCastleCmsSigner cms = new BouncyCastleCmsSigner(certificate, privateKey);
        byte[] data = "test payload".getBytes(StandardCharsets.UTF_8);
        byte[] signed = cms.sign(data);

        // Parse the DER bytes to verify it is a valid CMS SignedData structure
        CMSSignedData cmsData = new CMSSignedData(signed);
        assertNotNull(cmsData.getSignedContent(), "Encapsulated content must be present");
        assertEquals(1, cmsData.getSignerInfos().size(), "Must have exactly one signer");
    }

    @Test
    void sign_encapsulatesOriginalContent() throws Exception {
        BouncyCastleCmsSigner cms = new BouncyCastleCmsSigner(certificate, privateKey);
        byte[] data = "hello ARCA".getBytes(StandardCharsets.UTF_8);
        byte[] signed = cms.sign(data);

        CMSSignedData cmsData = new CMSSignedData(signed);
        byte[] extracted = (byte[]) cmsData.getSignedContent().getContent();
        assertArrayEquals(data, extracted, "Encapsulated content must match original");
    }
}
