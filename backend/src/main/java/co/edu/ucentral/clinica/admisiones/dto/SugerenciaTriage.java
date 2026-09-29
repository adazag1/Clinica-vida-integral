package co.edu.ucentral.clinica.admisiones.dto;

import java.util.List;

/**
 * @param criterios    qué signos vitales llevaron a ese nivel
 * @param advertencias limitaciones de la sugerencia para este paciente (p. ej. pediátrico)
 */
public record SugerenciaTriage(int nivel, String descripcion, List<String> criterios, List<String> advertencias) {
}
