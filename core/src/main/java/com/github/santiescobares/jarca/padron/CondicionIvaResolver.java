package com.github.santiescobares.jarca.padron;

import com.github.santiescobares.jarca.model.enums.IvaCondition;

/**
 * Derives the ARCA {@link IvaCondition} (CondicionIVAReceptorId) from Padrón data.
 *
 * <p>Resolution order (first match wins):
 * <ol>
 *   <li>Has an active monotributo category → {@link IvaCondition#RESPONSABLE_MONOTRIBUTO} (6)</li>
 *   <li>IVA Responsable Inscripto → {@link IvaCondition#IVA_RESPONSABLE_INSCRIPTO} (1)</li>
 *   <li>IVA Exento → {@link IvaCondition#IVA_EXENTO} (4)</li>
 *   <li>Default → {@link IvaCondition#CONSUMIDOR_FINAL} (5)</li>
 * </ol>
 *
 * <p>This covers the main cases for Argentine B2C and B2B transactions.
 * Less-common conditions (SUJETO_NO_CATEGORIZADO, importadores, etc.) must be set
 * manually on the {@link com.github.santiescobares.jarca.model.Comprobante}.
 */
public final class CondicionIvaResolver {

    private CondicionIvaResolver() {}

    /**
     * Derives the IVA condition from Padrón data.
     *
     * @param persona taxpayer data returned by {@link PadronClient#getPersona}
     * @return the corresponding {@link IvaCondition}
     */
    public static IvaCondition resolve(PadronClient.PersonaData persona) {
        if (persona.categoriaMonotributo() != null) {
            return IvaCondition.RESPONSABLE_MONOTRIBUTO;
        }
        if (persona.responsableInscripto()) {
            return IvaCondition.IVA_RESPONSABLE_INSCRIPTO;
        }
        if (persona.exento()) {
            return IvaCondition.IVA_EXENTO;
        }
        return IvaCondition.CONSUMIDOR_FINAL;
    }
}
