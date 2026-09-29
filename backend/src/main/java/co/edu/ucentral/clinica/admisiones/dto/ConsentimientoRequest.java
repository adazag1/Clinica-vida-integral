package co.edu.ucentral.clinica.admisiones.dto;

import co.edu.ucentral.clinica.admisiones.TipoConsentimiento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param otorgadoPor quien firma: el paciente o su acompañante
 * @param parentesco  si firma un acompañante (madre, hijo, cónyuge...)
 */
public record ConsentimientoRequest(
        @NotNull(message = "Seleccione el tipo de consentimiento") TipoConsentimiento tipo,
        @NotBlank(message = "Ingrese quién otorga el consentimiento") @Size(max = 150, message = "Máximo 150 caracteres")
        String otorgadoPor,
        @Size(max = 60, message = "Máximo 60 caracteres") String parentesco,
        @NotNull(message = "Indique si fue aceptado") Boolean aceptado,
        @Size(max = 500, message = "Máximo 500 caracteres") String observaciones) {
}
