package com.github.santiescobares.jarca.model;

import com.github.santiescobares.jarca.model.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Comprobante to be submitted to ARCA via WSFEv1.
 * All amount fields use scale 2 and HALF_UP rounding.
 * Build via {@link #builder()}.
 */
public final class Comprobante {

    private final InvoiceType tipo;
    private final int ptoVta;
    private final LocalDate fechaCbte;

    private final IdType docTipo;
    private final String docNro;
    private final IvaCondition condicionIvaReceptor;   // mandatory from RG 5616

    private final InvoiceConcept concepto;

    private final BigDecimal impNeto;       // ImpNeto: net taxable amount
    private final BigDecimal impOpEx;       // ImpOpEx: exempt operations
    private final BigDecimal impTotConc;    // ImpTotConc: total non-taxable concepts
    private final BigDecimal impIva;        // ImpIVA: total IVA amount
    private final BigDecimal impTrib;       // ImpTrib: other taxes
    private final BigDecimal impTotal;      // ImpTotal: grand total (must equal sum of above)

    private final Currency monId;
    private final BigDecimal monCotiz;

    private final List<AlicuotaIva> iva;            // empty for class C
    private final List<Tributo> tributos;
    private final List<CbteAsociado> cbtesAsoc;     // required for NC/ND

    // Service period — required when concepto is SERVICIOS or PRODUCTOS_Y_SERVICIOS
    private final LocalDate fchServDesde;
    private final LocalDate fchServHasta;
    private final LocalDate fchVtoPago;

    private Comprobante(Builder b) {
        this.tipo = Objects.requireNonNull(b.tipo, "tipo");
        this.ptoVta = b.ptoVta;
        this.fechaCbte = Objects.requireNonNull(b.fechaCbte, "fechaCbte");
        this.docTipo = Objects.requireNonNull(b.docTipo, "docTipo");
        this.docNro = Objects.requireNonNull(b.docNro, "docNro");
        this.condicionIvaReceptor = Objects.requireNonNull(b.condicionIvaReceptor, "condicionIvaReceptor");
        this.concepto = Objects.requireNonNull(b.concepto, "concepto");
        this.impNeto = Objects.requireNonNull(b.impNeto, "impNeto");
        this.impOpEx = b.impOpEx != null ? b.impOpEx : BigDecimal.ZERO.setScale(2);
        this.impTotConc = b.impTotConc != null ? b.impTotConc : BigDecimal.ZERO.setScale(2);
        this.impIva = b.impIva != null ? b.impIva : BigDecimal.ZERO.setScale(2);
        this.impTrib = b.impTrib != null ? b.impTrib : BigDecimal.ZERO.setScale(2);
        this.impTotal = Objects.requireNonNull(b.impTotal, "impTotal");
        this.monId = b.monId != null ? b.monId : Currency.PESOS;
        this.monCotiz = b.monCotiz != null ? b.monCotiz : BigDecimal.ONE.setScale(2);
        this.iva = b.iva != null ? List.copyOf(b.iva) : List.of();
        this.tributos = b.tributos != null ? List.copyOf(b.tributos) : List.of();
        this.cbtesAsoc = b.cbtesAsoc != null ? List.copyOf(b.cbtesAsoc) : List.of();
        this.fchServDesde = b.fchServDesde;
        this.fchServHasta = b.fchServHasta;
        this.fchVtoPago = b.fchVtoPago;
    }

    public static Builder builder() { return new Builder(); }

    public InvoiceType getTipo() { return tipo; }
    public int getPtoVta() { return ptoVta; }
    public LocalDate getFechaCbte() { return fechaCbte; }
    public IdType getDocTipo() { return docTipo; }
    public String getDocNro() { return docNro; }
    public IvaCondition getCondicionIvaReceptor() { return condicionIvaReceptor; }
    public InvoiceConcept getConcepto() { return concepto; }
    public BigDecimal getImpNeto() { return impNeto; }
    public BigDecimal getImpOpEx() { return impOpEx; }
    public BigDecimal getImpTotConc() { return impTotConc; }
    public BigDecimal getImpIva() { return impIva; }
    public BigDecimal getImpTrib() { return impTrib; }
    public BigDecimal getImpTotal() { return impTotal; }
    public Currency getMonId() { return monId; }
    public BigDecimal getMonCotiz() { return monCotiz; }
    public List<AlicuotaIva> getIva() { return iva; }
    public List<Tributo> getTributos() { return tributos; }
    public List<CbteAsociado> getCbtesAsoc() { return cbtesAsoc; }
    public LocalDate getFchServDesde() { return fchServDesde; }
    public LocalDate getFchServHasta() { return fchServHasta; }
    public LocalDate getFchVtoPago() { return fchVtoPago; }

    public static final class Builder {
        private InvoiceType tipo;
        private int ptoVta;
        private LocalDate fechaCbte;
        private IdType docTipo;
        private String docNro;
        private IvaCondition condicionIvaReceptor;
        private InvoiceConcept concepto;
        private BigDecimal impNeto;
        private BigDecimal impOpEx;
        private BigDecimal impTotConc;
        private BigDecimal impIva;
        private BigDecimal impTrib;
        private BigDecimal impTotal;
        private Currency monId;
        private BigDecimal monCotiz;
        private List<AlicuotaIva> iva;
        private List<Tributo> tributos;
        private List<CbteAsociado> cbtesAsoc;
        private LocalDate fchServDesde;
        private LocalDate fchServHasta;
        private LocalDate fchVtoPago;

        public Builder tipo(InvoiceType v) { this.tipo = v; return this; }
        public Builder ptoVta(int v) { this.ptoVta = v; return this; }
        public Builder fechaCbte(LocalDate v) { this.fechaCbte = v; return this; }
        public Builder docTipo(IdType v) { this.docTipo = v; return this; }
        public Builder docNro(String v) { this.docNro = v; return this; }
        public Builder condicionIvaReceptor(IvaCondition v) { this.condicionIvaReceptor = v; return this; }
        public Builder concepto(InvoiceConcept v) { this.concepto = v; return this; }
        public Builder impNeto(BigDecimal v) { this.impNeto = v; return this; }
        public Builder impOpEx(BigDecimal v) { this.impOpEx = v; return this; }
        public Builder impTotConc(BigDecimal v) { this.impTotConc = v; return this; }
        public Builder impIva(BigDecimal v) { this.impIva = v; return this; }
        public Builder impTrib(BigDecimal v) { this.impTrib = v; return this; }
        public Builder impTotal(BigDecimal v) { this.impTotal = v; return this; }
        public Builder monId(Currency v) { this.monId = v; return this; }
        public Builder monCotiz(BigDecimal v) { this.monCotiz = v; return this; }
        public Builder iva(List<AlicuotaIva> v) { this.iva = v; return this; }
        public Builder tributos(List<Tributo> v) { this.tributos = v; return this; }
        public Builder cbtesAsoc(List<CbteAsociado> v) { this.cbtesAsoc = v; return this; }
        public Builder fchServDesde(LocalDate v) { this.fchServDesde = v; return this; }
        public Builder fchServHasta(LocalDate v) { this.fchServHasta = v; return this; }
        public Builder fchVtoPago(LocalDate v) { this.fchVtoPago = v; return this; }

        public Comprobante build() { return new Comprobante(this); }
    }
}
