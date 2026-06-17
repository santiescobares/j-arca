# j-arca

Java library for integrating with **ARCA** (ex-AFIP) electronic billing (*facturación electrónica*) in Argentina.

It covers the full happy path for issuing official comprobantes: authenticating against
**WSAA**, requesting a **CAE** via **WSFEv1**, looking up a receiver in the **Padrón**, verifying
received comprobantes with **WSCDC**, and building the **QR** payload required by RG 4892.

The library is agnostic about the consumer: use it standalone (plain Java 21+) or wired into
**Spring Boot**, with optional **Redis** caching for tokens and parameter tables.

## Table of contents

- [Features](#features)
- [Modules](#modules)
- [Requirements](#requirements)
- [Quick start (native Java)](#quick-start-native-java)
- [Configuration](#configuration)
- [Module reference](#module-reference)
  - [Credentials (`crypto`)](#credentials-crypto)
  - [WSAA — authentication (`auth`)](#wsaa--authentication-auth)
  - [WSFEv1 — emission & CAE (`wsfe`)](#wsfev1--emission--cae-wsfe)
  - [Padrón — taxpayer lookup (`padron`)](#padrón--taxpayer-lookup-padron)
  - [WSCDC — verification (`cdc`)](#wscdc--verification-cdc)
  - [QR payload (`qr`)](#qr-payload-qr)
  - [Caching (`cache` / `cache-redis`)](#caching-cache--cache-redis)
  - [Spring Boot starter](#spring-boot-starter)
- [Error handling](#error-handling)
- [Building & testing](#building--testing)
- [ARCA official documentation](#arca-official-documentation)
- [Contributing](#contributing)
- [License](#license)

## Features

- **WSAA** — authentication via CMS/PKCS#7 signing of the TRA; transparent caching of the
  Ticket de Acceso (TA) per `(CUIT, servicio)`.
- **WSFEv1** — emission of Facturas, Notas de Crédito and Notas de Débito (classes A, B and C),
  CAE retrieval, last-authorised lookup and `FEDummy` health check.
- **Padrón** — taxpayer constancia (name, monotributo category, IVA condition), with automatic
  derivation of the mandatory `CondicionIVAReceptorId` (RG 5616).
- **WSCDC** — verification of received comprobantes against ARCA's database.
- **QR** — builds the RG 4892 QR URL payload.
- **No floating point for money** — all amounts are `BigDecimal` (scale 2, `HALF_UP`).
- **Lean core** — the only runtime dependency is BouncyCastle (CMS signing); everything else is
  plain JDK. Replaceable pieces (`CmsSigner`, `ArcaCache`) are exposed as SPIs.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| `core` | `core` | Core logic. Only runtime dependency: BouncyCastle (CMS signing). |
| `cache-redis` | `cache-redis` | `ArcaCache` implementation backed by Redis (zero extra dependencies — pure RESP protocol). |
| `spring-boot-starter` | `spring-boot-starter` | Spring Boot auto-configuration that wires every client from `arca.*` properties. |

Core packages (`com.github.santiescobares.jarca`): `config`, `crypto`, `auth`, `wsfe`,
`padron`, `cdc`, `qr`, `model`, `error`, `cache`, `soap`.

## Requirements

- Java 21+
- Maven 3.9+

## Quick start (native Java)

Add the dependency (replace `TAG` with the desired version, e.g. `v0.1.0`):

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.santiescobares</groupId>
  <artifactId>core</artifactId>
  <version>TAG</version>
</dependency>
```

Then load the credentials, wire the clients, and emit a comprobante:

```java
ArcaProperties props = ArcaProperties.builder()
    .environment(Environment.HOMOLOGACION)
    .cuit("20123456789")
    .certificatePath("/path/to/cert.crt")
    .privateKeyPath("/path/to/key.pem")
    .build();

// Load the certificate/key and build the CMS signer (BouncyCastle).
CertificateLoader.CertAndKey ck = CertificateLoader.fromProperties(props);
BouncyCastleCmsSigner signer = new BouncyCastleCmsSigner(ck.certificate(), ck.privateKey());

WsaaClient wsaaClient = new WsaaClient(props, signer, new InMemoryArcaCache());
WsfevClient wsfevClient = new WsfevClient(props);
ComprobanteService service = new ComprobanteServiceImpl(props, wsaaClient, wsfevClient);

Comprobante cbte = Comprobante.builder()
    .tipo(InvoiceType.FACTURA_C)
    .ptoVta(1)
    .fechaCbte(LocalDate.now(ArcaProperties.ZONE))
    .docTipo(IdType.DOC_SIN_NUMERO)
    .docNro("0")
    .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
    .concepto(InvoiceConcept.PRODUCTOS)
    .impNeto(new BigDecimal("1000.00"))
    .impTotal(new BigDecimal("1000.00"))
    .build();

ResultadoEmision resultado = service.emitir(cbte);
System.out.println("Resultado: " + resultado.resultado());
System.out.println("CbteNro: " + resultado.cbteNro());
System.out.println("CAE: " + resultado.cae().codigo());
System.out.println("CAE vto: " + resultado.cae().vencimiento());
```

## Configuration

Everything starts from an `ArcaProperties` instance, built via its fluent builder:

```java
ArcaProperties props = ArcaProperties.builder()
    .environment(Environment.HOMOLOGACION) // or Environment.PRODUCCION
    .cuit("20123456789") // digits only, no hyphens
    // Option A — PEM pair:
    .certificatePath("/etc/arca/cert.crt")
    .privateKeyPath("/etc/arca/key.pem")
    // Option B — PKCS12 keystore (overrides PEM paths when set):
    // .keystorePath("/etc/arca/arca.p12")
    // .keystorePassword("secret")
    // Optional: override endpoints / timeouts
    // .serviceUrls(ServiceUrls.builder(Environment.HOMOLOGACION).wsfeUrl("https://…").build())
    .connectTimeout(Duration.ofSeconds(30)) // default 30s
    .requestTimeout(Duration.ofSeconds(60)) // default 60s
    .build();
```

Notes:
- Per-service URLs default to the official ARCA endpoints for the chosen environment; override them
  via `ServiceUrls` if ARCA migrates a domain between releases.
- All dates and TRA timestamps use the zone `America/Argentina/Cordoba`, exposed as
  `ArcaProperties.ZONE`.

## Module reference

### Credentials (`crypto`)

`CertificateLoader` reads an X.509 certificate and its private key from either a PEM pair or a
PKCS12 keystore; `BouncyCastleCmsSigner` is the default `CmsSigner` SPI implementation used by WSAA.

```java
// From an ArcaProperties (PKCS12 if keystorePath is set, otherwise PEM pair):
CertificateLoader.CertAndKey ck = CertificateLoader.fromProperties(props);

// Or explicitly:
CertificateLoader.CertAndKey pem = CertificateLoader.loadPem("/path/cert.crt", "/path/key.pem");
CertificateLoader.CertAndKey p12 = CertificateLoader.loadPkcs12("/path/arca.p12", "secret");

CmsSigner signer = new BouncyCastleCmsSigner(ck.certificate(), ck.privateKey());
```

To plug a custom signer (e.g. an HSM), implement the `CmsSigner` SPI and pass it to `WsaaClient`.

### WSAA — authentication (`auth`)

`WsaaClient` obtains and caches a Ticket de Acceso (TA) for a given service. A cached TA is reused
until it is close to expiry, so repeated calls are cheap.

```java
WsaaClient wsaaClient = new WsaaClient(props, signer, new InMemoryArcaCache());

TicketAccess ta = wsaaClient.obtener("wsfe"); // service name, e.g. "wsfe", "wscdc", "ws_sr_constancia_inscripcion"
ta.token();     // base64 token
ta.sign();      // base64 sign
ta.expiresAt(); // Instant
ta.isValid();   // true while > 60s of validity remains
```

The cache key is `ta:{cuit}:{servicio}` and the value is stored with an 11h TTL (under the 12h WSAA
validity). Most callers never touch `WsaaClient` directly — `ComprobanteService` handles TA
acquisition internally.

### WSFEv1 — emission & CAE (`wsfe`)

`ComprobanteService` is the high-level entry point: it looks up the last authorised number, requests
the CAE, and maps the response — including an idempotency guard so the same comprobante is not
authorised twice.

```java
ComprobanteService service = new ComprobanteServiceImpl(props, wsaaClient, wsfevClient);

ResultadoEmision r = service.emitir(cbte);
if (r.isAprobado()) {
    System.out.println("CAE " + r.cae().codigo() + " vto " + r.cae().vencimiento());
} else {
    r.errores().forEach(e -> System.out.println(e.codigo() + ": " + e.mensaje()));
}
```

**Class A/B invoice with an IVA breakdown** — class A and B comprobantes must carry the IVA array;
class C must not.

```java
Comprobante facturaA = Comprobante.builder()
    .tipo(InvoiceType.FACTURA_A)
    .ptoVta(1)
    .fechaCbte(LocalDate.now(ArcaProperties.ZONE))
    .docTipo(IdType.CUIT)
    .docNro("30711111118")
    .condicionIvaReceptor(IvaCondition.IVA_RESPONSABLE_INSCRIPTO)
    .concepto(InvoiceConcept.PRODUCTOS)
    .impNeto(new BigDecimal("1000.00"))
    .impIva(new BigDecimal("210.00"))
    .impTotal(new BigDecimal("1210.00"))
    .iva(List.of(new AlicuotaIva(
        IvaType.IVA_21,
        new BigDecimal("1000.00"),  // base imponible
        new BigDecimal("210.00")))) // importe IVA
    .build();
```

Lower-level operations are available on `WsfevClient` when needed:

```java
WsfevClient wsfev = new WsfevClient(props);
boolean up = wsfev.feDummy(); // service health check
long last = wsfev.feCompUltimoAutorizado(ta, 1, 11); // ptoVta, cbteTipo
```

For service concepts (`SERVICIOS` / `PRODUCTOS_Y_SERVICIOS`) also set `fchServDesde`, `fchServHasta`
and `fchVtoPago`. For Notas de Crédito/Débito set the associated comprobantes via `cbtesAsoc(...)`.

### Padrón — taxpayer lookup (`padron`)

`PadronClient` retrieves taxpayer data, and `CondicionIvaResolver` derives the mandatory
`CondicionIVAReceptorId`.

```java
PadronClient padron = new PadronClient(props);
TicketAccess taPadron = wsaaClient.obtener("ws_sr_constancia_inscripcion");

PadronClient.PersonaData persona = padron.getPersona(taPadron, "30711111118");
persona.razonSocial();
persona.responsableInscripto();

IvaCondition condicion = CondicionIvaResolver.resolve(persona); // feed into Comprobante
```

### WSCDC — verification (`cdc`)

`WscdcClient` validates that a received comprobante was actually authorised by ARCA — useful before
registering a supplier invoice as a deductible expense.

```java
WscdcClient wscdc = new WscdcClient(props);
TicketAccess taCdc = wsaaClient.obtener("wscdc");

WscdcClient.ConstatarRequest req = new WscdcClient.ConstatarRequest(
    "30711111118",     // CUIT emisor
    6,                 // CbteTipo (6 = Factura B)
    1,                 // PtoVta
    1234L,             // CbteNro
    "71234567890123",  // CAE
    LocalDate.of(2026, 6, 15),
    new BigDecimal("1210.00"),
    Currency.PESOS,
    96,                // DocTipo receptor (96 = DNI, 80 = CUIT)
    0L);               // DocNro receptor

InvoiceResult result = wscdc.constatar(taCdc, req); // APROBADO or RECHAZADO
```

### QR payload (`qr`)

`QrPayloadBuilder` produces the RG 4892 QR URL from an emitted comprobante and its result. Rendering
the QR image itself (e.g. with ZXing) is left to the caller.

```java
String qrUrl = QrPayloadBuilder.build("20123456789", cbte, resultado);
// https://www.arca.gob.ar/fe/qr/?p=<base64-json>

// Custom base URL (testing / proxies):
String custom = QrPayloadBuilder.build("https://proxy.example.com/qr/?p=", "20123456789", cbte, resultado);
```

Only approved comprobantes with a valid CAE can be encoded; otherwise an `IllegalArgumentException`
is thrown.

### Caching (`cache` / `cache-redis`)

`ArcaCache` is the SPI used to cache TAs and WSFEv1 parameter tables.

- `InMemoryArcaCache` (in `core`) — default; lost on restart, fine for single-instance or local dev.
- `RedisArcaCache` (in `cache-redis`) — shared cache for multi-instance deployments, using a
  zero-dependency RESP client.

```xml
<dependency>
  <groupId>com.github.santiescobares</groupId>
  <artifactId>cache-redis</artifactId>
  <version>TAG</version>
</dependency>
```

```java
ArcaCache cache = new RedisArcaCache("localhost", 6379);
// or with a custom connect timeout:
ArcaCache cache2 = new RedisArcaCache("localhost", 6379, Duration.ofSeconds(5));

WsaaClient wsaaClient = new WsaaClient(props, signer, cache);
```

To use a different backend, implement `ArcaCache` (`get`, `put`, `evict`). Key conventions:
`ta:{cuit}:{servicio}` for tokens and `param:{servicio}:{tabla}` for parameter tables.

### Spring Boot starter

Add the starter and configure `arca.*` — the auto-configuration wires `ArcaProperties`, the
`CmsSigner`, every client, `ComprobanteService`, and an `ArcaCache` (Redis when Spring Data Redis is
on the classpath, in-memory otherwise). Every bean is `@ConditionalOnMissingBean`, so any of them
can be overridden.

```xml
<dependency>
  <groupId>com.github.santiescobares</groupId>
  <artifactId>spring-boot-starter</artifactId>
  <version>TAG</version>
</dependency>
```

```properties
# application.properties
arca.environment=HOMOLOGACION
arca.cuit=20123456789
arca.certificate-path=/etc/arca/cert.crt
arca.private-key-path=/etc/arca/key.pem
arca.connect-timeout=30s
arca.request-timeout=60s

# Use a PKCS12 keystore instead of the PEM pair:
# arca.keystore-path=/etc/arca/arca.p12
# arca.keystore-password=secret

# Optional endpoint overrides:
# arca.urls.wsfe=https://custom-wsfe.example.com/wsfev1/service.asmx

# Enable the shared Redis cache (requires spring-boot-starter-data-redis):
# spring.data.redis.host=localhost
# spring.data.redis.port=6379
```

```java
@Service
public class BillingService {

    private final ComprobanteService comprobantes;

    public BillingService(ComprobanteService comprobantes) { // injected by the starter
        this.comprobantes = comprobantes;
    }

    public ResultadoEmision emit(Comprobante cbte) {
        return comprobantes.emitir(cbte);
    }
}
```

## Error handling

| Exception | When |
|-----------|------|
| `ArcaException` | Base type for library errors (e.g. credential loading, Padrón lookup failures). |
| `ArcaTransportException` | Network or SOAP/XML parsing failure talking to ARCA. |
| `ArcaRechazo` | ARCA rejected the comprobante; inspect the carried error codes. |

`ResultadoEmision` also reports non-fatal `observaciones()` for approved-with-observations results,
and `errores()` for rejected ones. Secrets (certificates, private keys, `token`, `sign`) are never
logged in clear.

## Building & testing

```bash
mvn verify                  # unit tests (default)
mvn test                    # unit tests only
mvn verify -Phomologacion   # integration tests against ARCA homologation (needs ARCA_* env vars)
mvn -Prelease ...           # sources + javadoc + GPG-signed artifacts for publishing
```

Integration tests require homologation credentials, provided either through environment variables
(`ARCA_CERT_PATH`, `ARCA_KEY_PATH`, `ARCA_CUIT`, optional `ARCA_PTO_VTA`) or the equivalent system
properties (`-Darca.cert`, `-Darca.key`, `-Darca.cuit`, `-Darca.ptoVta`). They do not run in the
default build.

WSAA issues a single valid Ticket de Acceso per `(CUIT, servicio)` and refuses a new `loginCms`
until it expires (TRA validity, 12 h by default), returning `coe.alreadyAuthenticated`. The
integration tests therefore reuse one TA across all IT classes and across runs via a persistent
cache file under `target/`; run `mvn clean` to discard it. If a TA was issued and lost (e.g. a run
that did not persist it), wait for it to expire or shorten the window with `arca.traValidity`.

## ARCA official documentation

Selected references from ARCA (ex-AFIP) for the specs implemented here:

- **Web services catalog & architecture** — <https://www.afip.gob.ar/ws/documentacion/>
- **WSAA (authentication)** — <https://www.afip.gob.ar/ws/documentacion/wsaa.asp>
  and the technical spec <https://www.afip.gob.ar/ws/WSAA/Especificacion_Tecnica_WSAA_1.2.2.pdf>
- **WSFEv1 (electronic invoicing)** — <https://www.afip.gob.ar/ws/documentacion/ws-factura-electronica.asp>
  (developer manual, RG 4291: <https://www.afip.gob.ar/fe/ayuda/documentos/wsfev1-RG-4291.pdf>)
- **WSCDC (comprobante verification)** — <https://www.afip.gob.ar/ws/WSCDCV1/WSCDC-manual-desarrollador-v4.pdf>
- **Padrón constancia de inscripción (A5)** — <https://www.afip.gob.ar/ws/WSCI/manual_ws_sr_ws_constancia_inscripcion.pdf>
- **QR comprobantes (RG 4892)** — <https://www.afip.gob.ar/fe/qr/documentos/QRespecificaciones.pdf>
- **RG 5616 (CondicionIVAReceptorId)** — <https://www.argentina.gob.ar/normativa/nacional/resoluci%C3%B3n-5616-2024-407369/texto>

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for prerequisites, code
conventions, and the pull-request workflow. In short: class names, methods and comments in English
(except ARCA-specific terms like `Cbte`, `PtoVta`); no wildcard imports; `BigDecimal` for all
amounts; the `core` module stays dependency-free except for BouncyCastle; and no secrets in logs or
commits. Make sure `mvn verify` passes before opening a PR.

## License

Apache-2.0 — see [LICENSE](LICENSE).
</content>
