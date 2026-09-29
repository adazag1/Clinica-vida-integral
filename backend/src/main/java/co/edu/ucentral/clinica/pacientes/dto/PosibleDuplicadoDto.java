package co.edu.ucentral.clinica.pacientes.dto;

import java.util.List;

/**
 * Paciente existente que podría ser la misma persona (RFADM-003).
 *
 * @param puntaje      0 a 100; entre más alto, más probable que sea la misma persona
 * @param coincidencias qué datos coinciden, para que admisiones decida con información
 */
public record PosibleDuplicadoDto(PacienteResumenDto paciente, int puntaje, List<String> coincidencias) {
}
