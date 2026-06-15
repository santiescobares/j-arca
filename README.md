# j-arca

Java library for integrating with **ARCA** (ex-AFIP) electronic billing (facturación electrónica) in Argentina.

Covers WSAA (auth), WSFEv1 (invoices), Padrón (taxpayer lookup), and WSCDC (verification).
Designed for use standalone (Java 21+) or with Spring Boot, with optional Redis caching.

## Modules

| Module | Description |
|--------|-------------|
| `core` | Core logic. Only runtime dependency: BouncyCastle (CMS signing). |
| `cache-redis` | `ArcaCache` implementation backed by Redis (zero extra dependencies — pure RESP protocol). |
| `spring-boot-starter` | Spring Boot auto-configuration. |

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

Then wire the clients and emit a comprobante:

```java
ArcaProperties props = ArcaProperties.builder()
    .environment(Environment.HOMOLOGACION)
    .cuit("20123456789")
    .certificatePath("/path/to/cert.crt")
    .privateKeyPath("/path/to/key.pem")
    .build();

WsaaClient       wsaaClient    = new WsaaClient(props, new InMemoryArcaCache());
WsfevClient      wsfevClient   = new WsfevClient(props);
ComprobanteService service     = new ComprobanteServiceImpl(props, wsaaClient, wsfevClient);

Comprobante cbte = Comprobante.builder()
    .tipo(InvoiceType.FACTURA_C)
    .ptoVta(1)
    .fechaCbte(LocalDate.now())
    .docTipo(IdType.DOC_SIN_NUMERO)
    .docNro("0")
    .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
    .concepto(InvoiceConcept.PRODUCTOS)
    .impNeto(new BigDecimal("1000.00"))
    .impTotal(new BigDecimal("1000.00"))
    .build();

ResultadoEmision resultado = service.emitir(cbte);
System.out.println("CAE: " + resultado.cae().codigo());
```

## License

Apache-2.0 — see [LICENSE](LICENSE).
