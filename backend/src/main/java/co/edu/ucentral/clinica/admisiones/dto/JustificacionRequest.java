package co.edu.ucentral.clinica.admisiones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Texto obligatorio para excepciones de urgencia (RFADM-009) y anulaciones. */
public record JustificacionRequest(
        @NotBlank(message = "Ingrese la justificación") @Size(max = 500, message = "Máximo 500 caracteres")
        String justificacion) {
}
