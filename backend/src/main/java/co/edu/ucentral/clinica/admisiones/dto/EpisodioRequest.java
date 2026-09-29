package co.edu.ucentral.clinica.admisiones.dto;

import co.edu.ucentral.clinica.admisiones.TipoAtencion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EpisodioRequest(
        @NotNull(message = "Seleccione el paciente") Long pacienteId,
        @NotNull(message = "Seleccione el tipo de atención") TipoAtencion tipoAtencion,
        @NotBlank(message = "Ingrese el motivo de consulta") @Size(max = 1000, message = "Máximo 1000 caracteres")
        String motivoConsulta) {
}
