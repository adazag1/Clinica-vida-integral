package co.edu.ucentral.clinica.agenda.dto;

import co.edu.ucentral.clinica.agenda.Cita;
import co.edu.ucentral.clinica.agenda.EventoCita;
import co.edu.ucentral.clinica.agenda.Recordatorio;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;

import java.time.Instant;
import java.util.List;

/**
 * Cita para la agenda y el panel de detalle. {@code historial}, {@code recordatorios} y
 * {@code enlacePaciente} solo vienen al consultar una cita puntual.
 *
 * @param enlacePaciente enlace para que el paciente confirme o cancele (RFAGE-010)
 */
public record CitaDto(Long id, PacienteResumenDto paciente, ProfesionalDto profesional, CatalogosDto.TipoCitaDto tipo,
                      Instant inicio, Instant fin, String estado, String estadoDescripcion, String prioridad,
                      String prioridadDescripcion, String motivo, String motivoCancelacion,
                      String motivoCancelacionDescripcion, String detalleCancelacion, Long reprogramadaDesdeId,
                      Instant confirmadaEn, Instant recordatorioEnviadoEn, String creadoPor, Instant creadoEn,
                      String modificadoPor, Instant modificadoEn, List<EventoDto> historial,
                      List<RecordatorioDto> recordatorios, String enlacePaciente) {

    public record EventoDto(String accion, String descripcion, String detalle, String usuario, Instant fecha) {
        public static EventoDto de(EventoCita e) {
            return new EventoDto(e.getAccion().name(), e.getAccion().getDescripcion(), e.getDetalle(), e.getUsuario(),
                    e.getFecha());
        }
    }

    public record RecordatorioDto(String canal, String destino, String resultado, String detalle, Instant fecha) {
        public static RecordatorioDto de(Recordatorio r) {
            return new RecordatorioDto(r.getCanal(), r.getDestino(), r.getResultado(), r.getDetalle(), r.getFecha());
        }
    }

    public static CitaDto resumen(Cita c) {
        return de(c, List.of(), List.of(), null);
    }

    public static CitaDto de(Cita c, List<EventoDto> historial, List<RecordatorioDto> recordatorios,
                             String enlacePaciente) {
        return new CitaDto(c.getId(), PacienteResumenDto.de(c.getPaciente()), ProfesionalDto.de(c.getProfesional()),
                CatalogosDto.TipoCitaDto.de(c.getTipo()), c.getInicio(), c.getFin(), c.getEstado().name(),
                c.getEstado().getDescripcion(), c.getPrioridad().name(), c.getPrioridad().getDescripcion(),
                c.getMotivo(), c.getMotivoCancelacion() == null ? null : c.getMotivoCancelacion().name(),
                c.getMotivoCancelacion() == null ? null : c.getMotivoCancelacion().getDescripcion(),
                c.getDetalleCancelacion(), c.getReprogramadaDesdeId(), c.getConfirmadaEn(),
                c.getRecordatorioEnviadoEn(), c.getCreadoPor(), c.getCreadoEn(), c.getModificadoPor(),
                c.getModificadoEn(), historial, recordatorios, enlacePaciente);
    }
}
