package com.github.santiescobares.jarca.qr;

import com.github.santiescobares.jarca.Amounts;
import com.github.santiescobares.jarca.model.Comprobante;
import com.github.santiescobares.jarca.model.ResultadoEmision;

import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Objects;

/**
 * Builds the QR payload URL as defined by RG 4892.
 *
 * <p>The payload is a JSON object (version 1) Base64-encoded and appended to:
 * {@code https://www.arca.gob.ar/fe/qr/?p=}
 *
 * <p>The QR image itself is rendered by the caller (e.g. via ZXing); this class
 * only builds the URL string that is encoded into the QR.
 *
 * <p>Fields in the JSON payload:
 * <pre>
 * {
 *   "ver":       1,
 *   "fecha":     "YYYY-MM-DD",
 *   "cuit":      20123456789,
 *   "ptoVta":    1,
 *   "tipoCmp":   11,
 *   "nroCmp":    1,
 *   "importe":   1000.00,
 *   "moneda":    "PES",
 *   "ctz":       1.00,
 *   "tipoDocRec": 99,
 *   "nroDocRec": 0,
 *   "tipoCodAut": "E",
 *   "codAut":    12345678901234
 * }
 * </pre>
 */
public final class QrPayloadBuilder {

    private static final String QR_BASE_URL = "https://www.arca.gob.ar/fe/qr/?p=";

    /** RG 4892 version. */
    private static final int QR_VERSION = 1;

    /** CAE type code: "E" = CAE (Código de Autorización Electrónica). */
    private static final String TIPO_COD_AUT_CAE = "E";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private QrPayloadBuilder() {}

    /**
     * Builds the full QR URL from a comprobante and its emission result,
     * using the official ARCA base URL.
     *
     * @param cuit      CUIT del emisor (digits only, no hyphens)
     * @param cbte      comprobante emitido
     * @param resultado resultado de la emisión; must be approved and carry a non-null CAE
     * @return QR URL string ready to encode as QR
     * @throws IllegalArgumentException if resultado is not approved or CAE is missing
     */
    public static String build(String cuit, Comprobante cbte, ResultadoEmision resultado) {
        return build(QR_BASE_URL, cuit, cbte, resultado);
    }

    /**
     * Builds the full QR URL using a custom base URL.
     * Useful for testing or environments that proxy the ARCA QR endpoint.
     *
     * @param baseUrl   base URL (must end with {@code ?p=} or equivalent query separator)
     * @param cuit      CUIT del emisor (digits only, no hyphens)
     * @param cbte      comprobante emitido
     * @param resultado resultado de la emisión; must be approved and carry a non-null CAE
     * @return QR URL string ready to encode as QR
     * @throws IllegalArgumentException if resultado is not approved or CAE is missing
     */
    public static String build(String baseUrl, String cuit, Comprobante cbte, ResultadoEmision resultado) {
        Objects.requireNonNull(baseUrl,   "baseUrl");
        Objects.requireNonNull(cuit,      "cuit");
        Objects.requireNonNull(cbte,      "cbte");
        Objects.requireNonNull(resultado, "resultado");

        if (!resultado.isAprobado() || resultado.cae() == null) {
            throw new IllegalArgumentException(
                    "QR can only be built for an approved comprobante with a valid CAE");
        }

        String json = buildJson(cuit, cbte, resultado);
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return baseUrl + encoded;
    }

    // ── private ──────────────────────────────────────────────────────────────

    private static String buildJson(String cuit, Comprobante cbte, ResultadoEmision resultado) {
        long cuitLong = Long.parseLong(cuit.replaceAll("[^0-9]", ""));
        long codAut   = Long.parseLong(resultado.cae().codigo());
        String fecha  = cbte.getFechaCbte().format(DATE_FMT);
        long nroDocRec;
        try {
            nroDocRec = Long.parseLong(cbte.getDocNro().replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            nroDocRec = 0L;
        }

        return "{"
                + "\"ver\":" + QR_VERSION
                + ",\"fecha\":\"" + fecha + "\""
                + ",\"cuit\":" + cuitLong
                + ",\"ptoVta\":" + cbte.getPtoVta()
                + ",\"tipoCmp\":" + cbte.getTipo().getCodigo()
                + ",\"nroCmp\":" + resultado.cbteNro()
                + ",\"importe\":" + Amounts.toArca(cbte.getImpTotal())
                + ",\"moneda\":\"" + cbte.getMonId().getCodigo() + "\""
                + ",\"ctz\":" + Amounts.toArca(cbte.getMonCotiz())
                + ",\"tipoDocRec\":" + cbte.getDocTipo().getCodigo()
                + ",\"nroDocRec\":" + nroDocRec
                + ",\"tipoCodAut\":\"" + TIPO_COD_AUT_CAE + "\""
                + ",\"codAut\":" + codAut
                + "}";
    }
}
