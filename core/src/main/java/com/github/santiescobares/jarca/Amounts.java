package com.github.santiescobares.jarca;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Locale-safe utilities for formatting monetary amounts as ARCA expects them.
 *
 * <p>{@code String.format("%.2f", value)} depends on the JVM default locale: in
 * {@code es_AR} it produces a comma as decimal separator, breaking ARCA's SOAP schema.
 * This class uses {@link BigDecimal#toPlainString()} to guarantee a dot separator
 * regardless of the active locale.
 */
public final class Amounts {

    private Amounts() {}

    /**
     * Formats a monetary amount as a plain decimal string with exactly 2 decimal places.
     *
     * <p>Equivalent to scaling to 2 places with {@link RoundingMode#HALF_UP} and calling
     * {@link BigDecimal#toPlainString()} — no locale involvement.
     *
     * @param v amount to format; must not be {@code null}
     * @return e.g. {@code "1234.56"} or {@code "0.00"}
     */
    public static String toArca(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
