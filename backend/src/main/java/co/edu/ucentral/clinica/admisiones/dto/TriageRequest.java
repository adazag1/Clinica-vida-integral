package co.edu.ucentral.clinica.admisiones.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param nivelAsignado       nivel final que decide quien clasifica (1 a 5)
 * @param justificacionAjuste obligatoria si el nivel asignado difiere del sugerido (RFADM-006)
 */
public record TriageRequest(
        @NotNull(message = "Ingrese los signos vitales") @Valid SignosVitales signos,
        @NotBlank(message = "Ingrese el síntoma principal") @Size(max = 500, message = "Máximo 500 caracteres")
        String sintomaPrincipal,
        @NotNull(message = "Seleccione el nivel de triage")
        @Min(value = 1, message = "Entre I y V") @Max(value = 5, message = "Entre I y V") Integer nivelAsignado,
        @Size(max = 500, message = "Máximo 500 caracteres") String justificacionAjuste) {
}
