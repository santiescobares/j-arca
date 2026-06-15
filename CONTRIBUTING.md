# Contributing to j-arca

Thank you for your interest in contributing! This guide covers the essentials.

## Prerequisites

- Java 21+
- Maven 3.9+
- A BouncyCastle-compatible JVM (standard JDK 21 works out of the box)

## Building

```bash
mvn verify
```

Unit tests run by default. Integration tests (against ARCA's homologation environment) require
real credentials and run only under the `homologacion` profile:

```bash
mvn verify -Phomologacion
```

See the `j-arca-examples` module for the full homologation checklist (`HomologacionChecklist`).

## Code conventions

- Language: class names, method names, and comments in **English**, except for ARCA-specific
  terms (e.g. `Cbte`, `PtoVta`, `CbteTipo`).
- Imports: no wildcards. Monetary amounts: always `BigDecimal` (scale 2, `HALF_UP`).
- The core module (`j-core`) must remain dependency-free except for BouncyCastle.
- No secrets (certificates, token, sign) in logs or committed files.

## Running tests

```bash
# Unit tests only
mvn test

# Unit tests under Argentine locale (verifies locale-safe amount formatting)
mvn test -Duser.language=es -Duser.country=AR

# Integration tests (requires ARCA_* environment variables with homologation credentials)
mvn verify -Phomologacion
```

## Pull requests

1. Fork the repository and create a feature branch.
2. Make sure `mvn verify` passes before opening a PR.
3. Describe what the change does and reference any ARCA spec section if applicable.
4. One logical change per PR; keep diffs focused.

## Reporting issues

Open a GitHub issue with:
- A minimal reproducer (comprobante type, field values that trigger the problem).
- The ARCA error code or response if available.
- The `j-arca` version you are using.
