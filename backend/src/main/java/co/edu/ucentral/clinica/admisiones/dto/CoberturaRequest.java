package co.edu.ucentral.clinica.admisiones.dto;

import co.edu.ucentral.clinica.admisiones.EstadoCobertura;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Resultado de la verificación con la aseguradora (RFADM-008). */
public record CoberturaRequest(
        @NotNull(message = "Seleccione el resultado de la verificación") EstadoCobertura resultado,
        @Size(max = 120, message = "Máximo 120 caracteres") String aseguradora,
        @Size(max = 120, message = "Máximo 120 caracteres") String plan,
        @Size(max = 60, message = "Máximo 60 caracteres") String numeroAutorizacion,
        @Size(max = 500, message = "Máximo 500 caracteres") String observacion) {
}
