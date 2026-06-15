package com.github.santiescobares.jarca.padron;

import com.github.santiescobares.jarca.auth.TicketAccess;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.error.ArcaException;
import com.github.santiescobares.jarca.error.ArcaTransportException;
import com.github.santiescobares.jarca.soap.SoapClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Client for the Padrón service ({@code ws_sr_constancia_inscripcion} / {@code personaServiceA5}).
 *
 * <p>Used to retrieve taxpayer data and derive their IVA condition via
 * {@link CondicionIvaResolver}. The derived condition is required in every
 * WSFEv1 request as {@code CondicionIVAReceptorId} (RG 5616).
 *
 * <p>SOAP operation: {@code getPersona_v2}
 * Namespace: {@code http://a5.soap.ws.server.puc.sr/}
 */
public class PadronClient {

    private static final System.Logger LOG = System.getLogger(PadronClient.class.getName());

    private static final String PADRON_NS = "http://a5.soap.ws.server.puc.sr/";
    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";

    /** IVA (Impuesto al Valor Agregado) id in the Padrón response. */
    private static final int ID_IMPUESTO_IVA = 30;

    protected final ArcaProperties props;
    private final SoapClient soapClient;

    public PadronClient(ArcaProperties props) {
        this.props = props;
        this.soapClient = new SoapClient(props);
    }

    /** Constructor for tests that inject a custom {@link SoapClient}. */
    PadronClient(ArcaProperties props, SoapClient soapClient) {
        this.props = props;
        this.soapClient = soapClient;
    }

    /**
     * Returns taxpayer data for the given CUIT from the Padrón service.
     *
     * <p>Fields populated:
     * <ul>
     *   <li>{@code cuit} — CUIT of the taxpayer</li>
     *   <li>{@code razonSocial} — full name or business name</li>
     *   <li>{@code categoriaMonotributo} — monotributo category letter (e.g. "B"), or null if not monotributista</li>
     *   <li>{@code responsableInscripto} — true if the taxpayer is IVA Responsable Inscripto</li>
     *   <li>{@code exento} — true if the taxpayer is IVA Exento</li>
     * </ul>
     *
     * @param ta   valid Ticket de Acceso for {@code ws_sr_constancia_inscripcion}
     * @param cuit CUIT to look up (digits only, no hyphens)
     * @throws ArcaException if the taxpayer is not found or their clave is inactive
     * @throws ArcaTransportException on network or parse error
     */
    public PersonaData getPersona(TicketAccess ta, String cuit) {
        String url = props.getServiceUrls().getPadronUrl();
        String envelope = buildEnvelope(ta, cuit);

        LOG.log(System.Logger.Level.DEBUG, "Padrón getPersona_v2: cuit={0}", cuit);
        Document doc = soapClient.post(url, "", envelope);

        return parsePersonaResponse(doc, cuit);
    }

    // ── private ──────────────────────────────────────────────────────────────

    private String buildEnvelope(TicketAccess ta, String cuit) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="%s"
                                  xmlns:a5="%s">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <a5:getPersona_v2>
                      <token>%s</token>
                      <sign>%s</sign>
                      <cuitRepresentada>%s</cuitRepresentada>
                      <idPersona>%s</idPersona>
                    </a5:getPersona_v2>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(SOAP_NS, PADRON_NS, ta.token(), ta.sign(), props.getCuit(), cuit);
    }

    private PersonaData parsePersonaResponse(Document doc, String queriedCuit) {
        // Check for service-level errors (errorConstancia)
        NodeList errorNodes = doc.getElementsByTagName("error");
        if (errorNodes.getLength() > 0) {
            String errorMsg = errorNodes.item(0).getTextContent().trim();
            if (!errorMsg.isBlank()) {
                throw new ArcaException("Padrón error for CUIT " + queriedCuit + ": " + errorMsg);
            }
        }

        // Extract datosGenerales
        Element datosGenerales = firstElement(doc, "datosGenerales");
        if (datosGenerales == null) {
            throw new ArcaTransportException("Padrón response missing datosGenerales for CUIT " + queriedCuit);
        }

        String idPersona = textContent(datosGenerales, "idPersona");
        String estadoClave = textContent(datosGenerales, "estadoClave");

        if (estadoClave != null && !"ACTIVO".equalsIgnoreCase(estadoClave.trim())) {
            throw new ArcaException("Taxpayer CUIT " + queriedCuit + " is not ACTIVO (estadoClave=" + estadoClave + ")");
        }

        // Resolve razonSocial: persona física = apellido + " " + nombre; jurídica = razonSocial
        String tipoPersona = textContent(datosGenerales, "tipoPersona");
        String razonSocial;
        if ("JURIDICA".equalsIgnoreCase(tipoPersona != null ? tipoPersona.trim() : "")) {
            razonSocial = textContent(datosGenerales, "razonSocial");
        } else {
            String apellido = textContent(datosGenerales, "apellido");
            String nombre = textContent(datosGenerales, "nombre");
            razonSocial = (apellido != null ? apellido.trim() : "")
                    + (nombre != null && !nombre.isBlank() ? " " + nombre.trim() : "");
        }

        // Monotributo: presence of datosMonotributo with active category
        String categoriaMonotributo = null;
        Element datosMonotributo = firstElement(doc, "datosMonotributo");
        if (datosMonotributo != null) {
            // Get the description of the last (most recent) active category
            NodeList categorias = datosMonotributo.getElementsByTagName("categoria");
            if (categorias.getLength() > 0) {
                // Last entry is typically the most recent
                Element lastCat = (Element) categorias.item(categorias.getLength() - 1);
                String desc = textContent(lastCat, "descripcionCategoria");
                if (desc != null && !desc.isBlank()) {
                    categoriaMonotributo = desc.trim();
                } else {
                    // Some responses use idCategoria instead
                    String idCat = textContent(lastCat, "idCategoria");
                    categoriaMonotributo = idCat != null ? idCat.trim() : "MONOTRIBUTO";
                }
            } else {
                categoriaMonotributo = "MONOTRIBUTO";
            }
        }

        // IVA Responsable Inscripto: datosRegimenGeneral/impuesto with idImpuesto=30 and estado=ACTIVO
        boolean responsableInscripto = false;
        boolean exento = false;
        Element datosRegimenGeneral = firstElement(doc, "datosRegimenGeneral");
        if (datosRegimenGeneral != null) {
            NodeList impuestos = datosRegimenGeneral.getElementsByTagName("impuesto");
            for (int i = 0; i < impuestos.getLength(); i++) {
                Element imp = (Element) impuestos.item(i);
                String idImpStr = textContent(imp, "idImpuesto");
                // The A5 service uses estadoImpuesto with values "AC" (active) and "EX" (exempt)
                String estadoImpuesto = textContent(imp, "estadoImpuesto");
                if (idImpStr != null) {
                    try {
                        int idImp = Integer.parseInt(idImpStr.trim());
                        if (idImp == ID_IMPUESTO_IVA) {
                            String est = estadoImpuesto != null ? estadoImpuesto.trim() : "";
                            if ("AC".equalsIgnoreCase(est)) {
                                responsableInscripto = true;
                            } else if ("EX".equalsIgnoreCase(est)) {
                                exento = true;
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        String resolvedCuit = idPersona != null ? idPersona.trim() : queriedCuit;
        return new PersonaData(resolvedCuit, razonSocial != null ? razonSocial.trim() : "",
                categoriaMonotributo, responsableInscripto, exento);
    }

    // ── DOM helpers ──────────────────────────────────────────────────────────

    private static Element firstElement(Document doc, String tag) {
        NodeList nl = doc.getElementsByTagName(tag);
        return nl.getLength() > 0 ? (Element) nl.item(0) : null;
    }

    private static String textContent(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() > 0 ? nl.item(0).getTextContent() : null;
    }

    // ── inner types ──────────────────────────────────────────────────────────

    /**
     * Raw taxpayer data returned by the Padrón service.
     *
     * @param cuit                  CUIT of the taxpayer
     * @param razonSocial           full name or business name
     * @param categoriaMonotributo  monotributo category (e.g. "B"), or null if not monotributista
     * @param responsableInscripto  true if IVA Responsable Inscripto (active)
     * @param exento                true if IVA Exento
     */
    public record PersonaData(String cuit, String razonSocial, String categoriaMonotributo,
                              boolean responsableInscripto, boolean exento) {}
}
