package com.github.santiescobares.jarca.crypto;

import com.github.santiescobares.jarca.error.ArcaException;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * {@link CmsSigner} implementation using BouncyCastle (SHA256withRSA, encapsulated SignedData).
 * Registers the BC provider once on first instantiation.
 */
public final class BouncyCastleCmsSigner implements CmsSigner {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final X509Certificate certificate;
    private final PrivateKey privateKey;

    public BouncyCastleCmsSigner(X509Certificate certificate, PrivateKey privateKey) {
        this.certificate = certificate;
        this.privateKey  = privateKey;
    }

    public static BouncyCastleCmsSigner fromProperties(
            com.github.santiescobares.jarca.config.ArcaProperties props) {
        CertificateLoader.CertAndKey ck = CertificateLoader.fromProperties(props);
        return new BouncyCastleCmsSigner(ck.certificate(), ck.privateKey());
    }

    @Override
    public byte[] sign(byte[] data) {
        try {
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();

            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(privateKey);

            gen.addSignerInfoGenerator(
                    new JcaSignerInfoGeneratorBuilder(
                            new JcaDigestCalculatorProviderBuilder()
                                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                    .build()
                    ).build(contentSigner, certificate)
            );

            gen.addCertificates(new JcaCertStore(List.of(certificate)));

            CMSTypedData msg = new CMSProcessableByteArray(data);
            // encapsulated=true: content is included in the CMS structure (required by WSAA)
            CMSSignedData signedData = gen.generate(msg, true);
            return signedData.getEncoded();

        } catch (Exception e) {
            throw new ArcaException("CMS signing failed", e);
        }
    }
}
