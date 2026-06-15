package com.github.santiescobares.jarca.cdc;

import com.github.santiescobares.jarca.Amounts;
import com.github.santiescobares.jarca.auth.TicketAccess;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.error.ArcaTransportException;
import com.github.santiescobares.jarca.model.enums.Currency;
import com.github.santiescobares.jarca.model.enums.InvoiceResult;
import com.github.santiescobares.jarca.soap.SoapClient;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Client for WSCDC (Web Service de Constatación de Comprobantes).
 *
 * <p>Used to verify that a received comprobante was actually authorised by ARCA.
 * Typical use case: validate comprobantes received from suppliers before registering them
 * as deductible expenses.
 *
 * <p>SOAP operation: {@code ComprobanteConstatar}
 * Namespace: {@code http://ar.gov.afip.dif.wscdc.service/}
 */
public class WscdcClient {

    private static final System.Logger LOG = System.getLogger(WscdcClient.class.getName());

    private static final String WSCDC_NS = "http://ar.gov.afip.dif.wscdc.service/";
    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP_ACTION = "";

    /** CbteModo: "CAE" for comprobantes authorised via WSFEv1. */
    private static final String CBTE_MODO_CAE = "CAE";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    protected final ArcaProperties props;
    private final SoapClient soapClient;

    public WscdcClient(ArcaProperties props) {
        this.props = props;
        this.soapClient = new SoapClient(props);
    }

    /** Constructor for tests that inject a custom {@link SoapClient}. */
    WscdcClient(ArcaProperties props, SoapClient soapClient) {
        this.props = props;
        this.soapClient = soapClient;
    }

    /**
     * Verifies a received comprobante against ARCA's database.
     *
     * @param ta      valid Ticket de Acceso for {@code wscdc}
     * @param request comprobante details to verify
     * @return {@link InvoiceResult#APROBADO} if ARCA confirms it,
     *         {@link InvoiceResult#RECHAZADO} if ARCA does not recognise it
     * @throws ArcaTransportException on network or parse error
     */
    public InvoiceResult constatar(TicketAccess ta, ConstatarRequest request) {
        String url = props.getServiceUrls().getWscdcUrl();
        String envelope = buildEnvelope(ta, request);

        LOG.log(System.Logger.Level.DEBUG,
                "WSCDC ComprobanteConstatar: emisor={0} tipo={1} pto={2} nro={3}",
                request.cuitEmisor(), request.cbteTipo(),
                request.ptoVta(), request.nroCbte());

        Document doc = soapClient.post(url, SOAP_ACTION, envelope);

        return parseResult(doc);
    }

    // ── private ──────────────────────────────────────────────────────────────

    private String buildEnvelope(TicketAccess ta, ConstatarRequest req) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="%s"
                                  xmlns:wscdc="%s">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <wscdc:ComprobanteConstatar>
                      <wscdc:Auth>
                        <wscdc:Token>%s</wscdc:Token>
                        <wscdc:Sign>%s</wscdc:Sign>
                        <wscdc:Cuit>%s</wscdc:Cuit>
                      </wscdc:Auth>
                      <wscdc:CmpReq>
                        <wscdc:CuitEmisor>%s</wscdc:CuitEmisor>
                        <wscdc:CbteTipo>%d</wscdc:CbteTipo>
                        <wscdc:PtoVta>%d</wscdc:PtoVta>
                        <wscdc:CbteNro>%d</wscdc:CbteNro>
                        <wscdc:CbteModo>%s</wscdc:CbteModo>
                        <wscdc:CodAutorizacion>%s</wscdc:CodAutorizacion>
                        <wscdc:DocTipoReceptor>%d</wscdc:DocTipoReceptor>
                        <wscdc:DocNroReceptor>%d</wscdc:DocNroReceptor>
                        <wscdc:CbteFch>%s</wscdc:CbteFch>
                        <wscdc:ImpTotal>%s</wscdc:ImpTotal>
                        <wscdc:MonId>%s</wscdc:MonId>
                      </wscdc:CmpReq>
                    </wscdc:ComprobanteConstatar>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(
                SOAP_NS, WSCDC_NS,
                ta.token(), ta.sign(), props.getCuit(),
                req.cuitEmisor(),
                req.cbteTipo(),
                req.ptoVta(),
                req.nroCbte(),
                CBTE_MODO_CAE,
                req.cae(),
                req.docTipoReceptor(),
                req.docNroReceptor(),
                req.fechaEmision().format(DATE_FMT),
                Amounts.toArca(req.importe()),
                req.moneda().getCodigo()
        );
    }

    private static InvoiceResult parseResult(Document doc) {
        NodeList nodes = doc.getElementsByTagName("Resultado");
        if (nodes.getLength() == 0) {
            throw new ArcaTransportException("WSCDC response missing 'Resultado' element");
        }
        String codigo = nodes.item(0).getTextContent().trim();
        return InvoiceResult.fromCodigo(codigo);
    }

    // ── inner types ──────────────────────────────────────────────────────────

    /**
     * Parameters for the {@code ComprobanteConstatar} operation.
     *
     * @param cuitEmisor       CUIT of the comprobante issuer (digits only)
     * @param cbteTipo         ARCA CbteTipo code (e.g. 6 for Factura B)
     * @param ptoVta           punto de venta of the comprobante
     * @param nroCbte          comprobante number
     * @param cae              14-digit CAE code on the comprobante
     * @param fechaEmision     emission date of the comprobante
     * @param importe          grand total ({@code ImpTotal}) of the comprobante
     * @param moneda           currency of the comprobante
     * @param docTipoReceptor  ARCA DocTipo code of the receiver (e.g. 96 = DNI, 80 = CUIT)
     * @param docNroReceptor   document number of the receiver; 0 for consumers without ID
     */
    public record ConstatarRequest(
            String cuitEmisor,
            int cbteTipo,
            int ptoVta,
            long nroCbte,
            String cae,
            LocalDate fechaEmision,
            BigDecimal importe,
            Currency moneda,
            int docTipoReceptor,
            long docNroReceptor
    ) {}
}
