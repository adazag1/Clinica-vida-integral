package co.edu.ucentral.clinica.admisiones.dto;

import co.edu.ucentral.clinica.admisiones.Episodio;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;

import java.time.Instant;
import java.util.List;

/**
 * Episodio con todo lo necesario para la pantalla de admisión y la lista de espera.
 *
 * @param pendientes vacío = el paciente puede pasar a consulta
 */
public record EpisodioDto(Long id, String numero, PacienteResumenDto paciente, String tipoAtencion,
                          String tipoAtencionDescripcion, String motivoConsulta, String estado,
                          String estadoDescripcion, Instant fechaIngreso, Instant fechaListo, Instant fechaCierre,
                          String motivoAnulacion, String admitidoPor, String estadoCobertura,
                          String estadoCoberturaDescripcion, String aseguradora, String planCobertura,
                          String numeroAutorizacion, String observacionCobertura, String coberturaVerificadaPor,
                          Instant coberturaVerificadaEn, TriageDto triage, List<ConsentimientoDto> consentimientos,
                          List<Pendiente> pendientes) {

    public static EpisodioDto de(Episodio e, TriageDto triage, List<ConsentimientoDto> consentimientos,
                                 List<Pendiente> pendientes) {
        return new EpisodioDto(e.getId(), e.getNumero(), PacienteResumenDto.de(e.getPaciente()),
                e.getTipoAtencion().name(), e.getTipoAtencion().getDescripcion(), e.getMotivoConsulta(),
                e.getEstado().name(), e.getEstado().getDescripcion(), e.getFechaIngreso(), e.getFechaListo(),
                e.getFechaCierre(), e.getMotivoAnulacion(), e.getCreadoPor(), e.getEstadoCobertura().name(),
                e.getEstadoCobertura().getDescripcion(), e.getAseguradora(), e.getPlanCobertura(),
                e.getNumeroAutorizacion(), e.getObservacionCobertura(), e.getCoberturaVerificadaPor(),
                e.getCoberturaVerificadaEn(), triage, consentimientos, pendientes);
    }
}
