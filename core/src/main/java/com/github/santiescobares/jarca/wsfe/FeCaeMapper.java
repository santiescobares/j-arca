package com.github.santiescobares.jarca.wsfe;

import com.github.santiescobares.jarca.Amounts;
import com.github.santiescobares.jarca.auth.TicketAccess;
import com.github.santiescobares.jarca.error.ArcaException;
import com.github.santiescobares.jarca.model.AlicuotaIva;
import com.github.santiescobares.jarca.model.CbteAsociado;
import com.github.santiescobares.jarca.model.Comprobante;
import com.github.santiescobares.jarca.model.Tributo;
import com.github.santiescobares.jarca.model.enums.IvaCondition;
import com.github.santiescobares.jarca.model.enums.InvoiceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a {@link Comprobante} domain object to the WSFEv1 {@code FECAESolicitar} body XML.
 *
 * <p>Class-specific rules:
 * <ul>
 *   <li><b>Class C</b>: ImpTotConc, ImpOpEx, ImpIVA are set to 0; no Iva array.</li>
 *   <li><b>Class A/B</b>: Iva array is emitted; amounts include IVA breakdown.</li>
 *   <li><b>NC/ND</b>: CbtesAsoc array is required and must be non-empty.</li>
 * </ul>
 *
 * <p>The returned string is the content for the {@code ar:Body} element inside the
 * SOAP envelope built by {@link com.github.santiescobares.jarca.soap.SoapMessageBuilder#wsfeRequest}.
 */
public final class FeCaeMapper {

    private static final String AR_NS = "http://ar.gov.afip.dif.FEV1/";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private FeCaeMapper() {}

    /**
     * Builds the {@code FECAESolicitar} XML body (without SOAP envelope).
     *
     * @param cuit    tenant CUIT (must match the TA)
     * @param ta      valid Ticket de Acceso for {@code wsfe}
     * @param cbte    comprobante to emit
     * @param cbteNro assigned comprobante number ({@code lastAuthorised + 1})
     * @return body XML ready to wrap in a SOAP envelope
     * @throws ArcaException if the comprobante fails pre-submission validation
     */
    public static String buildBody(String cuit, TicketAccess ta, Comprobante cbte, long cbteNro) {
        validate(cbte);

        char clase = cbte.getTipo().getClase();
        boolean isClassC = clase == 'C';
        boolean hasIva = !isClassC && !cbte.getIva().isEmpty();

        StringBuilder sb = new StringBuilder(512);
        sb.append("<ar:FECAESolicitar xmlns:ar=\"").append(AR_NS).append("\">\n");
        sb.append("  <ar:Auth>\n");
        sb.append("    <ar:Token>").append(ta.token()).append("</ar:Token>\n");
        sb.append("    <ar:Sign>").append(ta.sign()).append("</ar:Sign>\n");
        sb.append("    <ar:Cuit>").append(cuit).append("</ar:Cuit>\n");
        sb.append("  </ar:Auth>\n");
        sb.append("  <ar:FeCAEReq>\n");
        sb.append("    <ar:FeCabReq>\n");
        sb.append("      <ar:CantReg>1</ar:CantReg>\n");
        sb.append("      <ar:PtoVta>").append(cbte.getPtoVta()).append("</ar:PtoVta>\n");
        sb.append("      <ar:CbteTipo>").append(cbte.getTipo().getCodigo()).append("</ar:CbteTipo>\n");
        sb.append("    </ar:FeCabReq>\n");
        sb.append("    <ar:FeDetReq>\n");
        sb.append("      <ar:FECAEDetRequest>\n");
        sb.append("        <ar:Concepto>").append(cbte.getConcepto().getCodigo()).append("</ar:Concepto>\n");
        sb.append("        <ar:DocTipo>").append(cbte.getDocTipo().getCodigo()).append("</ar:DocTipo>\n");
        sb.append("        <ar:DocNro>").append(cbte.getDocNro()).append("</ar:DocNro>\n");
        sb.append("        <ar:CbteDesde>").append(cbteNro).append("</ar:CbteDesde>\n");
        sb.append("        <ar:CbteHasta>").append(cbteNro).append("</ar:CbteHasta>\n");
        sb.append("        <ar:CbteFch>").append(cbte.getFechaCbte().format(DATE_FMT)).append("</ar:CbteFch>\n");

        // Amounts — class C zeros out ImpTotConc, ImpOpEx, ImpIVA (they must be 0)
        BigDecimal impTotConc = isClassC ? BigDecimal.ZERO.setScale(2) : cbte.getImpTotConc();
        BigDecimal impOpEx    = isClassC ? BigDecimal.ZERO.setScale(2) : cbte.getImpOpEx();
        BigDecimal impIva     = isClassC ? BigDecimal.ZERO.setScale(2) : cbte.getImpIva();

        sb.append("        <ar:ImpTotal>").append(fmt(cbte.getImpTotal())).append("</ar:ImpTotal>\n");
        sb.append("        <ar:ImpTotConc>").append(fmt(impTotConc)).append("</ar:ImpTotConc>\n");
        sb.append("        <ar:ImpNeto>").append(fmt(cbte.getImpNeto())).append("</ar:ImpNeto>\n");
        sb.append("        <ar:ImpOpEx>").append(fmt(impOpEx)).append("</ar:ImpOpEx>\n");
        sb.append("        <ar:ImpIVA>").append(fmt(impIva)).append("</ar:ImpIVA>\n");
        sb.append("        <ar:ImpTrib>").append(fmt(cbte.getImpTrib())).append("</ar:ImpTrib>\n");

        // Service period — required when Concepto is 2 (Servicios) or 3 (Productos y Servicios)
        if (cbte.getConcepto().requiresServiceDates()) {
            sb.append("        <ar:FchServDesde>").append(date(cbte.getFchServDesde())).append("</ar:FchServDesde>\n");
            sb.append("        <ar:FchServHasta>").append(date(cbte.getFchServHasta())).append("</ar:FchServHasta>\n");
            sb.append("        <ar:FchVtoPago>").append(date(cbte.getFchVtoPago())).append("</ar:FchVtoPago>\n");
        }

        sb.append("        <ar:MonId>").append(cbte.getMonId().getCodigo()).append("</ar:MonId>\n");
        sb.append("        <ar:MonCotiz>").append(fmt(cbte.getMonCotiz())).append("</ar:MonCotiz>\n");
        sb.append("        <ar:CondicionIVAReceptorId>").append(cbte.getCondicionIvaReceptor().getCodigo())
                .append("</ar:CondicionIVAReceptorId>\n");

        // Iva array — class A and B only; aggregated by rate when multiple entries share the same type
        if (hasIva) {
            sb.append("        <ar:Iva>\n");
            for (AlicuotaIva a : aggregateIva(cbte.getIva())) {
                sb.append("          <ar:AlicIva>\n");
                sb.append("            <ar:Id>").append(a.tipo().getCodigo()).append("</ar:Id>\n");
                sb.append("            <ar:BaseImp>").append(fmt(a.baseImponible())).append("</ar:BaseImp>\n");
                sb.append("            <ar:Importe>").append(fmt(a.importe())).append("</ar:Importe>\n");
                sb.append("          </ar:AlicIva>\n");
            }
            sb.append("        </ar:Iva>\n");
        }

        // Tributos (other taxes)
        if (!cbte.getTributos().isEmpty()) {
            sb.append("        <ar:Tributos>\n");
            for (Tributo t : cbte.getTributos()) {
                sb.append("          <ar:Tributo>\n");
                sb.append("            <ar:Id>").append(t.id()).append("</ar:Id>\n");
                sb.append("            <ar:Desc>").append(escapeXml(t.descripcion())).append("</ar:Desc>\n");
                sb.append("            <ar:BaseImp>").append(fmt(t.baseImponible())).append("</ar:BaseImp>\n");
                sb.append("            <ar:Alic>").append(fmt(t.alicuota())).append("</ar:Alic>\n");
                sb.append("            <ar:Importe>").append(fmt(t.importe())).append("</ar:Importe>\n");
                sb.append("          </ar:Tributo>\n");
            }
            sb.append("        </ar:Tributos>\n");
        }

        // CbtesAsoc — required for NC/ND
        if (!cbte.getCbtesAsoc().isEmpty()) {
            sb.append("        <ar:CbtesAsoc>\n");
            for (CbteAsociado a : cbte.getCbtesAsoc()) {
                sb.append("          <ar:CbteAsoc>\n");
                sb.append("            <ar:Tipo>").append(a.tipo().getCodigo()).append("</ar:Tipo>\n");
                sb.append("            <ar:PtoVta>").append(a.ptoVta()).append("</ar:PtoVta>\n");
                sb.append("            <ar:Nro>").append(a.nroCbte()).append("</ar:Nro>\n");
                sb.append("          </ar:CbteAsoc>\n");
            }
            sb.append("        </ar:CbtesAsoc>\n");
        }

        sb.append("      </ar:FECAEDetRequest>\n");
        sb.append("    </ar:FeDetReq>\n");
        sb.append("  </ar:FeCAEReq>\n");
        sb.append("</ar:FECAESolicitar>");
        return sb.toString();
    }

    // ── validation ───────────────────────────────────────────────────────────

    private static void validate(Comprobante cbte) {
        InvoiceType tipo = cbte.getTipo();

        // NC/ND must have at least one associated comprobante
        if ((tipo.isNotaCredito() || tipo.isNotaDebito()) && cbte.getCbtesAsoc().isEmpty()) {
            throw new ArcaException(tipo + " requires at least one CbteAsociado (CbtesAsoc)");
        }

        // Class A requires DocTipo CUIT (80)
        if (tipo.getClase() == 'A' && cbte.getDocTipo().getCodigo() != 80) {
            throw new ArcaException("Clase A comprobantes require DocTipo=CUIT (80), got: " + cbte.getDocTipo());
        }

        // Service period fields are required when Concepto demands them
        if (cbte.getConcepto().requiresServiceDates()) {
            if (cbte.getFchServDesde() == null || cbte.getFchServHasta() == null || cbte.getFchVtoPago() == null) {
                throw new ArcaException("Concepto " + cbte.getConcepto()
                        + " requires FchServDesde, FchServHasta and FchVtoPago");
            }
        }

        // Class C: IVA array must be empty (amounts must be zero per ARCA rules)
        if (tipo.getClase() == 'C' && !cbte.getIva().isEmpty()) {
            throw new ArcaException("Clase C comprobantes must not include an Iva array");
        }

        // Clase A → receptor must be IVA Responsable Inscripto (code 1)
        if (tipo.getClase() == 'A'
                && cbte.getCondicionIvaReceptor() != IvaCondition.IVA_RESPONSABLE_INSCRIPTO) {
            throw new ArcaException(
                    "Clase A comprobantes require CondicionIVAReceptor=IVA_RESPONSABLE_INSCRIPTO (1), got: "
                    + cbte.getCondicionIvaReceptor());
        }

        // Clase C → receptor must NOT be IVA Responsable Inscripto
        if (tipo.getClase() == 'C'
                && cbte.getCondicionIvaReceptor() == IvaCondition.IVA_RESPONSABLE_INSCRIPTO) {
            throw new ArcaException(
                    "Clase C comprobantes cannot have CondicionIVAReceptor=IVA_RESPONSABLE_INSCRIPTO (1)");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Aggregates IVA entries by rate type: if the caller provides duplicate rates they are summed.
     * ARCA rejects requests with duplicate AlicIva.Id entries (error 10040-style).
     */
    private static List<AlicuotaIva> aggregateIva(List<AlicuotaIva> iva) {
        Map<Integer, AlicuotaIva> aggregated = new LinkedHashMap<>();
        for (AlicuotaIva a : iva) {
            int id = a.tipo().getCodigo();
            if (aggregated.containsKey(id)) {
                AlicuotaIva existing = aggregated.get(id);
                aggregated.put(id, new AlicuotaIva(
                        a.tipo(),
                        existing.baseImponible().add(a.baseImponible()),
                        existing.importe().add(a.importe())
                ));
            } else {
                aggregated.put(id, a);
            }
        }
        return List.copyOf(aggregated.values());
    }

    private static String fmt(BigDecimal v) {
        return Amounts.toArca(v);
    }

    private static String date(LocalDate d) {
        return d != null ? d.format(DATE_FMT) : "";
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
