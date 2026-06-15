package com.github.santiescobares.jarca.config;

/**
 * ARCA deployment environment.
 * Determines which endpoint URLs are used for all services.
 */
public enum Environment {
    /** Homologation (testing) environment — safe for development and certification. */
    HOMOLOGACION,
    /** Production environment — emits legally binding comprobantes. */
    PRODUCCION
}
