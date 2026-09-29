package co.edu.ucentral.clinica.admisiones.dto;

import java.util.List;

/**
 * RFADM-013.
 *
 * @param pendientesTriage urgencias sin clasificar, por orden de llegada
 * @param enAdmision       ya clasificados o no urgentes, con requisitos por completar
 * @param esperaConsulta   listos para el médico: primero el triage más grave, luego el que más lleva esperando
 */
public record ListaEsperaDto(List<EpisodioDto> pendientesTriage, List<EpisodioDto> enAdmision,
                             List<EpisodioDto> esperaConsulta) {
}
