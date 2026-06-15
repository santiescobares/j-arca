package com.github.santiescobares.jarca.error;

/**
 * Base exception for all j-arca errors.
 * Subclasses distinguish network problems ({@link ArcaTransportException})
 * from ARCA business rejections ({@link ArcaRechazo}).
 */
public class ArcaException extends RuntimeException {

    public ArcaException(String message) {
        super(message);
    }

    public ArcaException(String message, Throwable cause) {
        super(message, cause);
    }
}
