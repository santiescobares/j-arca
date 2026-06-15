package com.github.santiescobares.jarca.wsfe;

import com.github.santiescobares.jarca.auth.TicketAccess;
import com.github.santiescobares.jarca.auth.WsaaClient;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.error.ArcaObservacion;
import com.github.santiescobares.jarca.error.ArcaRechazo;
import com.github.santiescobares.jarca.model.Cae;
import com.github.santiescobares.jarca.model.Comprobante;
import com.github.santiescobares.jarca.model.ResultadoEmision;
import com.github.santiescobares.jarca.model.enums.InvoiceResult;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level entry point for comprobante emission.
 *
 * <p>Orchestrates:
 * <ol>
 *   <li>Ticket de Acceso acquisition (via cache-backed {@link WsaaClient}).</li>
 *   <li>Last-authorised number lookup ({@code FECompUltimoAutorizado}).</li>
 *   <li>CAE request ({@code FECAESolicitar}).</li>
 *   <li>Error-10016 recovery: if ARCA reports a number out of sequence, the last
 *       authorised number is re-read and the request is retried once.</li>
 *   <li>Mapping the ARCA response to a {@link ResultadoEmision}.</li>
 * </ol>
 *
 * <p>Concurrent emission for the same {@code (ptoVta, cbteTipo)} is serialised with an
 * in-process lock to prevent duplicate comprobante numbers. For multi-process deployments,
 * an external coordination mechanism (e.g. a database sequence) is required.
 *
 * <p>Does not retry on transport failures (RN-07). The caller should use
 * {@code WsfevClient.feCompConsultar} to check idempotency before retrying manually.
 */
public class ComprobanteServiceImpl implements ComprobanteService {

    private static final System.Logger LOG = System.getLogger(ComprobanteServiceImpl.class.getName());

    /** ARCA error code: comprobante number is not the expected next number. */
    private static final int ERROR_NUMERO_FUERA_DE_SECUENCIA = 10016;

    private final ArcaProperties props;
    private final WsaaClient wsaaClient;
    private final WsfevClient wsfevClient;

    /**
     * Per-(ptoVta, cbteTipo) mutex objects to serialise in-process concurrent emissions.
     * The lock is held only for the duration of one emission; it is never released, so
     * GC retention is acceptable for the small number of distinct (ptoVta, tipo) pairs.
     */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public ComprobanteServiceImpl(ArcaProperties props, WsaaClient wsaaClient,
                                  WsfevClient wsfevClient) {
        this.props       = props;
        this.wsaaClient  = wsaaClient;
        this.wsfevClient = wsfevClient;
    }

    /**
     * {@inheritDoc}
     *
     * @throws ArcaRechazo           if ARCA explicitly rejects the comprobante
     * @throws com.github.santiescobares.jarca.error.ArcaTransportException on network failure
     * @throws com.github.santiescobares.jarca.error.ArcaException on pre-submission validation failure
     */
    @Override
    public ResultadoEmision emitir(Comprobante cbte) {
        String lockKey = cbte.getPtoVta() + ":" + cbte.getTipo().getCodigo();
        Object lock = locks.computeIfAbsent(lockKey, k -> new Object());

        synchronized (lock) {
            return doEmitir(cbte);
        }
    }

    // ── private ──────────────────────────────────────────────────────────────

    private ResultadoEmision doEmitir(Comprobante cbte) {
        TicketAccess ta = wsaaClient.obtener("wsfe");

        int ptoVta   = cbte.getPtoVta();
        int cbteTipo = cbte.getTipo().getCodigo();

        long lastNro  = wsfevClient.feCompUltimoAutorizado(ta, ptoVta, cbteTipo);
        long cbteNro  = lastNro + 1;

        LOG.log(System.Logger.Level.INFO,
                "Emitting {0} ptoVta={1} nro={2} cuit={3}",
                cbte.getTipo(), ptoVta, cbteNro, props.getCuit());

        String bodySoap = FeCaeMapper.buildBody(props.getCuit(), ta, cbte, cbteNro);
        WsfevClient.CaeResponse resp = wsfevClient.feCaeSolicitar(ta, bodySoap);

        // Error 10016: the assigned number is not the next expected one.
        // Re-read the last authorised number and retry once.
        if (hasError10016(resp)) {
            LOG.log(System.Logger.Level.WARNING,
                    "Error 10016 for ptoVta={0} tipo={1} — re-reading last authorised number",
                    ptoVta, cbteTipo);
            long correctedLast = wsfevClient.feCompUltimoAutorizado(ta, ptoVta, cbteTipo);
            cbteNro  = correctedLast + 1;
            bodySoap = FeCaeMapper.buildBody(props.getCuit(), ta, cbte, cbteNro);
            resp     = wsfevClient.feCaeSolicitar(ta, bodySoap);
        }

        return toResultado(resp);
    }

    private static boolean hasError10016(WsfevClient.CaeResponse resp) {
        // 10016 may appear in either the global Errors block or the per-comprobante Obs block
        return resp.errores().stream().anyMatch(e -> e.codigo() == ERROR_NUMERO_FUERA_DE_SECUENCIA)
                || resp.obs().stream().anyMatch(e -> e.codigo() == ERROR_NUMERO_FUERA_DE_SECUENCIA);
    }

    private static ResultadoEmision toResultado(WsfevClient.CaeResponse resp) {
        List<ArcaObservacion> obs     = resp.obs();
        List<ArcaObservacion> errores = resp.errores();

        if (!resp.resultado().isAprobado()) {
            throw new ArcaRechazo(errores, obs);
        }

        Cae cae = new Cae(resp.cae(), resp.caeFchVto());

        // Determine effective result: APROBADO vs APROBADO_CON_OBSERVACIONES
        InvoiceResult resultado = obs.isEmpty()
                ? InvoiceResult.APROBADO
                : InvoiceResult.APROBADO_CON_OBSERVACIONES;

        if (!obs.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING,
                    "CAE issued with observations for cbteNro={0}: {1}", resp.cbteNro(), obs);
        }

        return new ResultadoEmision(resultado, resp.cbteNro(), cae, obs, errores);
    }

    /**
     * Convenience method for callers that need to check idempotency after a transport failure.
     * Delegates directly to {@link WsfevClient#feCompConsultar}.
     *
     * @param ptoVta   punto de venta
     * @param cbteTipo ARCA CbteTipo code
     * @param cbteNro  comprobante number to look up
     */
    public Optional<ResultadoEmision> consultar(int ptoVta, int cbteTipo, long cbteNro) {
        TicketAccess ta = wsaaClient.obtener("wsfe");
        return wsfevClient.feCompConsultar(ta, ptoVta, cbteTipo, cbteNro)
                .map(r -> toResultado(r));
    }
}
