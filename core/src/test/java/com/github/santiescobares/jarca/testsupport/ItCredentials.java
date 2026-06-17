package com.github.santiescobares.jarca.testsupport;

/**
 * Resolves integration-test credentials from a system property first, falling back to an environment
 * variable.
 *
 * <p>This reconciles the two ways the homologation suite can be configured: the {@code homologacion}
 * Maven profile injects {@code arca.*} system properties (e.g. {@code -Darca.cert=...}), while the
 * README documents {@code ARCA_*} environment variables. Reading the property first and the env var
 * second makes both paths work.
 */
public final class ItCredentials {

    private ItCredentials() {}

    /** Returns the system property if set and non-blank, otherwise the environment variable. */
    public static String resolve(String systemProperty, String envVar) {
        String v = System.getProperty(systemProperty);
        if (v != null && !v.isBlank()) {
            return v;
        }
        return System.getenv(envVar);
    }

    /** Like {@link #resolve} but returns {@code fallback} when neither source is set. */
    public static String resolve(String systemProperty, String envVar, String fallback) {
        String v = resolve(systemProperty, envVar);
        return (v != null && !v.isBlank()) ? v : fallback;
    }
}
