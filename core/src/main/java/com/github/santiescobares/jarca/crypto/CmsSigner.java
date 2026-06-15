package com.github.santiescobares.jarca.crypto;

import com.github.santiescobares.jarca.error.ArcaException;

/**
 * SPI: produces a CMS/PKCS#7 signature (DER-encoded) over arbitrary bytes.
 * Default implementation: {@link BouncyCastleCmsSigner} (SHA256withRSA).
 * Alternative: a JDK-internal implementation can replace it without touching any other class.
 */
public interface CmsSigner {

    /**
     * Signs {@code data} and returns the DER-encoded CMS SignedData object.
     * The content is encapsulated (included) in the signed data structure, as required by WSAA.
     *
     * @param data bytes to sign (the TRA XML)
     * @return DER-encoded CMS SignedData
     * @throws ArcaException if signing fails
     */
    byte[] sign(byte[] data);
}
