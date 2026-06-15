package com.github.santiescobares.jarca.crypto;

import com.github.santiescobares.jarca.error.ArcaException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Loads an X.509 certificate and private key from either PEM files or a PKCS12 keystore.
 */
public final class CertificateLoader {

    private CertificateLoader() {}

    /**
     * Loads certificate and key from separate PEM files.
     *
     * @param certPath path to the {@code .crt} / {@code .pem} certificate file
     * @param keyPath  path to the {@code .key} / {@code .pem} private key file
     */
    public static CertAndKey loadPem(String certPath, String keyPath) {
        try {
            X509Certificate cert;
            try (FileInputStream fis = new FileInputStream(certPath)) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                cert = (X509Certificate) cf.generateCertificate(fis);
            }

            PrivateKey privateKey;
            try (PEMParser parser = new PEMParser(new FileReader(keyPath))) {
                Object obj = parser.readObject();
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());
                if (obj instanceof PEMKeyPair pair) {
                    privateKey = converter.getKeyPair(pair).getPrivate();
                } else if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo info) {
                    privateKey = converter.getPrivateKey(info);
                } else {
                    throw new ArcaException("Unrecognized PEM key format in: " + keyPath);
                }
            }

            return new CertAndKey(cert, privateKey);
        } catch (ArcaException e) {
            throw e;
        } catch (Exception e) {
            throw new ArcaException("Failed to load PEM certificate/key", e);
        }
    }

    /**
     * Loads certificate and key from a PKCS12 keystore ({@code .p12} / {@code .pfx}).
     *
     * @param keystorePath path to the keystore file
     * @param password     keystore password
     */
    public static CertAndKey loadPkcs12(String keystorePath, String password) {
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            char[] pwd = password != null ? password.toCharArray() : new char[0];
            ks.load(fis, pwd);

            String alias = ks.aliases().nextElement();
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            PrivateKey privateKey = (PrivateKey) ks.getKey(alias, pwd);
            return new CertAndKey(cert, privateKey);
        } catch (Exception e) {
            throw new ArcaException("Failed to load PKCS12 keystore: " + keystorePath, e);
        }
    }

    /**
     * Loads credentials from {@link com.github.santiescobares.jarca.config.ArcaProperties}.
     * Prefers PKCS12 if {@code keystorePath} is set; falls back to PEM pair.
     */
    public static CertAndKey fromProperties(com.github.santiescobares.jarca.config.ArcaProperties props) {
        if (props.getKeystorePath() != null) {
            return loadPkcs12(props.getKeystorePath(), props.getKeystorePassword());
        }
        return loadPem(props.getCertificatePath(), props.getPrivateKeyPath());
    }

    /** Value object carrying a certificate and its matching private key. */
    public record CertAndKey(X509Certificate certificate, PrivateKey privateKey) {}
}
