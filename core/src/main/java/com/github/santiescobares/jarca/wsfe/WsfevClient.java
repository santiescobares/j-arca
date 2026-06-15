package com.github.santiescobares.jarca.wsfe;

import com.github.santiescobares.jarca.auth.TicketAccess;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.error.ArcaObservacion;
import com.github.santiescobares.jarca.error.ArcaTransportException;
import com.github.santiescobares.jarca.model.enums.InvoiceResult;
import com.github.santiescobares.jarca.soap.SoapClient;
import com.github.santiescobares.jarca.soap.SoapMessageBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Low-level WSFEv1 SOAP client.
 *
 * <p>Covers the following operations:
 * <ul>
 *   <li>{@link #feDummy()} — health check (no auth required)</li>
 *   <li>{@link #feCompUltimoAutorizado(TicketAccess, int, int)} — last authorised number</li>
 *   <li>{@link #feCaeSolicitar(TicketAccess, String)} — CAE request (body XML pre-built by {@link FeCaeMapper})</li>
 *   <li>{@link #feCompConsultar(TicketAccess, int, int, long)} — idempotency lookup</li>
 *   <li>{@link #feParamGetTiposCbte(TicketAccess)} — valid CbteTipo codes</li>
 * </ul>
 *
 * <p>All authenticated operations require a valid {@link TicketAccess} for service {@code "wsfe"}.
 * CUIT isolation is enforced by the caller (the TA belongs to the tenant CUIT).
 */
public class WsfevClient {

    private static final System.Logger LOG = System.getLogger(WsfevClient.class.getName());

    /** SOAPAction prefix for all WSFEv1 operations. */
    private static final String ACTION_PREFIX = "http://ar.gov.afip.dif.FEV1/";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    protected final ArcaProperties props;
    private final SoapClient soapClient;

    public WsfevClient(ArcaProperties props) {
        this.props      = props;
        this.soapClient = new SoapClient(props);
    }

    /** Constructor for tests that inject a custom {@link SoapClient}. */
    WsfevClient(ArcaProperties props, SoapClient soapClient) {
        this.props      = props;
        this.soapClient = soapClient;
    }

    // ── public operations ────────────────────────────────────────────────────

    /**
     * Health-check: verifies AppServer, AuthServer and DbServer are all "OK".
     * Does not require a TA.
     *
     * @return true if all servers are OK
     * @throws ArcaTransportException on network or parse error
     */
    public boolean feDummy() {
        String url = props.getServiceUrls().getWsfeUrl();
        Document doc = soapClient.post(url, ACTION_PREFIX + "FEDummy",
                SoapMessageBuilder.feDummy());

        String app  = firstText(doc, "AppServer");
        String auth = firstText(doc, "AuthServer");
        String db   = firstText(doc, "DbServer");
        boolean ok = "OK".equalsIgnoreCase(app) && "OK".equalsIgnoreCase(auth) && "OK".equalsIgnoreCase(db);
        LOG.log(System.Logger.Level.DEBUG, "FEDummy: AppServer={0} AuthServer={1} DbServer={2}", app, auth, db);
        return ok;
    }

    /**
     * Returns the last authorised comprobante number for a given {@code (ptoVta, cbteTipo)}.
     * Returns {@code 0} if no comprobante has been issued yet.
     *
     * @param ta       valid Ticket de Acceso for {@code wsfe}
     * @param ptoVta   punto de venta
     * @param cbteTipo ARCA numeric type code (e.g. 11 for Factura C)
     * @throws ArcaTransportException on network or parse error
     */
    public long feCompUltimoAutorizado(TicketAccess ta, int ptoVta, int cbteTipo) {
        String body = """
                <ar:FECompUltimoAutorizado xmlns:ar="http://ar.gov.afip.dif.FEV1/">
                  %s
                  <ar:PtoVta>%d</ar:PtoVta>
                  <ar:CbteTipo>%d</ar:CbteTipo>
                </ar:FECompUltimoAutorizado>
                """.formatted(authElement(ta), ptoVta, cbteTipo);

        Document doc = soapClient.post(props.getServiceUrls().getWsfeUrl(),
                ACTION_PREFIX + "FECompUltimoAutorizado",
                SoapMessageBuilder.wsfeRequest(body));

        requireNoErrors(doc, "FECompUltimoAutorizado");

        String nro = firstText(doc, "CbteNro");
        if (nro == null || nro.isBlank()) {
            throw new ArcaTransportException("FECompUltimoAutorizado response missing CbteNro");
        }
        return Long.parseLong(nro.trim());
    }

    /**
     * Submits the FECAESolicitar request and returns the parsed ARCA response.
     *
     * <p>The caller is responsible for building {@code bodySoap} via {@link FeCaeMapper}.
     * This method only handles the HTTP transport and XML parsing.
     *
     * @param ta       valid Ticket de Acceso for {@code wsfe}
     * @param bodySoap WSFE body XML for FECAESolicitar (without the SOAP envelope)
     * @return parsed CAE response
     * @throws ArcaTransportException on network or parse error
     */
    public CaeResponse feCaeSolicitar(TicketAccess ta, String bodySoap) {
        Document doc = soapClient.post(props.getServiceUrls().getWsfeUrl(),
                ACTION_PREFIX + "FECAESolicitar",
                SoapMessageBuilder.wsfeRequest(bodySoap));

        return parseCaeSolicitarResponse(doc);
    }

    /**
     * Looks up a comprobante that may have already been authorised.
     * Used for idempotency: if a transport error occurs during {@link #feCaeSolicitar}, call this
     * to check whether ARCA already issued a CAE for the number before retrying.
     *
     * @param ta       valid Ticket de Acceso for {@code wsfe}
     * @param ptoVta   punto de venta
     * @param cbteTipo ARCA numeric type code
     * @param cbteNro  comprobante number to look up
     * @return an Optional containing the CAE response, or empty if not found
     */
    public Optional<CaeResponse> feCompConsultar(TicketAccess ta, int ptoVta, int cbteTipo, long cbteNro) {
        String body = """
                <ar:FECompConsultar xmlns:ar="http://ar.gov.afip.dif.FEV1/">
                  %s
                  <ar:FeCompConsReq>
                    <ar:CbteTipo>%d</ar:CbteTipo>
                    <ar:CbteNro>%d</ar:CbteNro>
                    <ar:PtoVta>%d</ar:PtoVta>
                  </ar:FeCompConsReq>
                </ar:FECompConsultar>
                """.formatted(authElement(ta), cbteTipo, cbteNro, ptoVta);

        Document doc = soapClient.post(props.getServiceUrls().getWsfeUrl(),
                ACTION_PREFIX + "FECompConsultar",
                SoapMessageBuilder.wsfeRequest(body));

        List<ArcaObservacion> errors = parseItems(doc, "Err", "Code", "Msg");
        if (!errors.isEmpty()) {
            // A 602 error means "comprobante not found" — not an error, just absent
            boolean notFound = errors.stream().anyMatch(e -> e.codigo() == 602);
            if (notFound) return Optional.empty();
            // Other errors are genuine problems
            LOG.log(System.Logger.Level.WARNING, "FECompConsultar errors: {0}", errors);
            return Optional.empty();
        }

        String cae     = firstText(doc, "CodAutorizacion");
        String fchVto  = firstText(doc, "FchVto");
        String resultado = firstText(doc, "Resultado");

        if (cae == null || cae.isBlank() || resultado == null) {
            return Optional.empty();
        }

        LocalDate vto = parseDate(fchVto);
        InvoiceResult res = InvoiceResult.fromCodigo(resultado.trim());
        long nro = Long.parseLong(firstTextRequired(doc, "CbteDesde", "FECompConsultar"));

        return Optional.of(new CaeResponse(nro, res, cae.trim(), vto, List.of(), List.of()));
    }

    /**
     * Fetches the list of valid {@code CbteTipo} codes from ARCA.
     * Results are stable; cache them via {@link ParamCache} rather than calling this directly.
     *
     * @param ta valid Ticket de Acceso for {@code wsfe}
     */
    public List<Integer> feParamGetTiposCbte(TicketAccess ta) {
        String body = """
                <ar:FEParamGetTiposCbte xmlns:ar="http://ar.gov.afip.dif.FEV1/">
                  %s
                </ar:FEParamGetTiposCbte>
                """.formatted(authElement(ta));

        Document doc = soapClient.post(props.getServiceUrls().getWsfeUrl(),
                ACTION_PREFIX + "FEParamGetTiposCbte",
                SoapMessageBuilder.wsfeRequest(body));

        requireNoErrors(doc, "FEParamGetTiposCbte");

        NodeList ids = doc.getElementsByTagName("Id");
        List<Integer> result = new ArrayList<>(ids.getLength());
        for (int i = 0; i < ids.getLength(); i++) {
            String text = ids.item(i).getTextContent().trim();
            if (!text.isBlank()) {
                try { result.add(Integer.parseInt(text)); } catch (NumberFormatException ignored) {}
            }
        }
        return List.copyOf(result);
    }

    // ── internal result type ─────────────────────────────────────────────────

    /**
     * Parsed result of a {@link #feCaeSolicitar} call.
     *
     * @param cbteNro   authorised comprobante number
     * @param resultado overall result from ARCA
     * @param cae       14-digit CAE code (null when rejected)
     * @param caeFchVto CAE expiry date (null when rejected)
     * @param obs       list of non-fatal observations
     * @param errores   list of rejection error codes
     */
    record CaeResponse(long cbteNro, InvoiceResult resultado, String cae,
                       LocalDate caeFchVto, List<ArcaObservacion> obs,
                       List<ArcaObservacion> errores) {}

    // ── private helpers ──────────────────────────────────────────────────────

    private String authElement(TicketAccess ta) {
        return """
                <ar:Auth>
                  <ar:Token>%s</ar:Token>
                  <ar:Sign>%s</ar:Sign>
                  <ar:Cuit>%s</ar:Cuit>
                </ar:Auth>
                """.formatted(ta.token(), ta.sign(), props.getCuit());
    }

    private CaeResponse parseCaeSolicitarResponse(Document doc) {
        // Global errors (not per-comprobante)
        List<ArcaObservacion> globalErrors = parseItems(doc, "Err", "Code", "Msg");

        String cbteDesde = firstText(doc, "CbteDesde");
        long cbteNro = cbteDesde != null && !cbteDesde.isBlank()
                ? Long.parseLong(cbteDesde.trim()) : 0L;

        String resultadoStr = firstText(doc, "Resultado");
        if (resultadoStr == null || resultadoStr.isBlank()) {
            // No Resultado element — treat as rejection with global errors
            return new CaeResponse(cbteNro, InvoiceResult.RECHAZADO, null, null, List.of(), globalErrors);
        }

        InvoiceResult resultado = InvoiceResult.fromCodigo(resultadoStr.trim());

        String cae    = firstText(doc, "CAE");
        String fchVto = firstText(doc, "CAEFchVto");
        LocalDate caeFchVto = parseDate(fchVto);

        List<ArcaObservacion> obs = parseItems(doc, "Obs", "Code", "Msg");

        if (resultado == InvoiceResult.RECHAZADO || cae == null || cae.isBlank()) {
            return new CaeResponse(cbteNro, InvoiceResult.RECHAZADO, null, null, obs, globalErrors);
        }

        InvoiceResult effective = obs.isEmpty() ? InvoiceResult.APROBADO
                                                : InvoiceResult.APROBADO_CON_OBSERVACIONES;
        return new CaeResponse(cbteNro, effective, cae.trim(), caeFchVto, obs, globalErrors);
    }

    /** Parses a list of items from elements named {@code itemTag} with children {@code codeTag} and {@code msgTag}. */
    private List<ArcaObservacion> parseItems(Document doc, String itemTag, String codeTag, String msgTag) {
        NodeList items = doc.getElementsByTagName(itemTag);
        List<ArcaObservacion> result = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            org.w3c.dom.Element el = (org.w3c.dom.Element) items.item(i);
            String codeStr = textContentOf(el, codeTag);
            String msg     = textContentOf(el, msgTag);
            if (codeStr != null && !codeStr.isBlank()) {
                try {
                    result.add(new ArcaObservacion(Integer.parseInt(codeStr.trim()),
                            msg != null ? msg.trim() : ""));
                } catch (NumberFormatException ignored) {}
            }
        }
        return List.copyOf(result);
    }

    private void requireNoErrors(Document doc, String operation) {
        List<ArcaObservacion> errors = parseItems(doc, "Err", "Code", "Msg");
        if (!errors.isEmpty()) {
            throw new ArcaTransportException(operation + " returned errors: " + errors);
        }
    }

    private static String firstText(Document doc, String tag) {
        NodeList nl = doc.getElementsByTagName(tag);
        return nl.getLength() > 0 ? nl.item(0).getTextContent() : null;
    }

    private static String firstTextRequired(Document doc, String tag, String operation) {
        String v = firstText(doc, tag);
        if (v == null || v.isBlank()) {
            throw new ArcaTransportException(operation + " response missing element: " + tag);
        }
        return v.trim();
    }

    private static String textContentOf(org.w3c.dom.Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() > 0 ? nl.item(0).getTextContent() : null;
    }

    private static LocalDate parseDate(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.isBlank() || "NULL".equalsIgnoreCase(yyyyMMdd.trim())) {
            return null;
        }
        try {
            return LocalDate.parse(yyyyMMdd.trim(), DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }
}
