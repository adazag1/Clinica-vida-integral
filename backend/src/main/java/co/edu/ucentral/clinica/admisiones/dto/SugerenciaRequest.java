package co.edu.ucentral.clinica.admisiones.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Para ver el nivel sugerido mientras se digitan los signos, antes de guardar. */
public record SugerenciaRequest(@NotNull(message = "Ingrese los signos vitales") @Valid SignosVitales signos,
                                Integer edad) {
}
