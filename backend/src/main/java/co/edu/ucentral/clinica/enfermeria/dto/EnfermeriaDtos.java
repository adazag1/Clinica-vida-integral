package co.edu.ucentral.clinica.enfermeria.dto;

import co.edu.ucentral.clinica.enfermeria.EjecucionOrden;
import co.edu.ucentral.clinica.enfermeria.EntregaTurno;
import co.edu.ucentral.clinica.enfermeria.EventoAdverso;
import co.edu.ucentral.clinica.enfermeria.NotaEnfermeria;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.SignosDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.OrdenResumenDto;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;

import java.time.Instant;
import java.util.List;

/** Respuestas del módulo de enfermería. */
public final class EnfermeriaDtos {

    private EnfermeriaDtos() {
    }

    public record EjecucionDto(Instant programadaPara, Instant realizadaEn, String resultado, String resultadoDescripcion,
                               String motivo, String justificacionRetraso, String verificaciones,
                               String dobleValidacionPor, String usuario) {
        public static EjecucionDto de(EjecucionOrden e) {
            return new EjecucionDto(e.getProgramadaPara(), e.getRealizadaEn(), e.getResultado().name(),
                    e.getResultado().getDescripcion(), e.getMotivo(), e.getJustificacionRetraso(), e.getVerificaciones(),
                    e.getDobleValidacionPor(), e.getUsuario());
        }
    }

    /**
     * Una hora programada de la orden.
     *
     * @param estado PROGRAMADA (futura), PENDIENTE (dentro de su ventana), ATRASADA (pasó la ventana sin
     *               registro), NO_APLICA (orden ya no vigente) o el resultado registrado
     */
    public record HorarioDto(Instant programadaPara, String estado, EjecucionDto ejecucion) {
    }

    /** @param situacion ACTIVA, VENCIDA o SUSPENDIDA (RFENF-009) */
    public record OrdenClinicaDto(Long id, String tipo, String tipoDescripcion, String descripcion, Long medicamentoId,
                                  String medicamento, String dosis, String via, Integer frecuenciaHoras, Instant inicio,
                                  Instant fin, boolean altoRiesgo, String indicaciones, String situacion,
                                  String motivoSuspension, String suspendidaPor, Instant suspendidaEn, String medicoNombre,
                                  List<HorarioDto> horarios) {
    }

    /** RFENF-002: todo lo que hay que hacerle al paciente, en una sola vista. */
    public record OrdenesPacienteDto(PacienteResumenDto paciente, List<String> alergias, List<OrdenClinicaDto> ordenes,
                                     List<OrdenResumenDto> examenes) {
    }

    /** Fila del censo de enfermería, ordenado por lo que requiere atención primero. */
    public record PacienteCensoDto(PacienteResumenDto paciente, String ubicacion, int dosisAtrasadas, int proximasDosHoras,
                                   List<String> alertas, int alergias, String prioridad) {
    }

    public record EventoAdversoDto(Long id, Long pacienteId, String tipo, String tipoDescripcion, String severidad,
                                   String severidadDescripcion, Instant ocurridoEn, String lugar, String descripcion,
                                   String accionesInmediatas, String medicoNotificado, String reportadoPor,
                                   Instant reportadoEn) {
        public static EventoAdversoDto de(EventoAdverso e) {
            return new EventoAdversoDto(e.getId(), e.getPacienteId(), e.getTipo().name(), e.getTipo().getDescripcion(),
                    e.getSeveridad().name(), e.getSeveridad().getDescripcion(), e.getOcurridoEn(), e.getLugar(),
                    e.getDescripcion(), e.getAccionesInmediatas(), e.getMedicoNotificado(), e.getCreadoPor(),
                    e.getCreadoEn());
        }
    }

    public record NotaDto(Long id, String texto, String usuario, Instant fecha) {
        public static NotaDto de(NotaEnfermeria n) {
            return new NotaDto(n.getId(), n.getTexto(), n.getUsuario(), n.getFecha());
        }
    }

    /**
     * RFENF-008: estado del paciente en el turno, generado automáticamente.
     *
     * @param texto el mismo resumen en texto plano, que es lo que se guarda al entregar
     */
    public record ResumenTurnoDto(PacienteResumenDto paciente, String turno, Instant desde, Instant hasta,
                                  List<String> alergias, List<String> diagnosticos, SignosDto ultimosSignos,
                                  List<String> alertas, List<String> administrado, List<String> pendientes,
                                  List<String> ordenesActivas, List<String> examenes, List<String> eventos,
                                  List<String> notas, String texto) {
    }

    public record EntregaDto(Long id, String turno, String resumen, String nota, String entregadoPor, Instant fecha) {
        public static EntregaDto de(EntregaTurno e) {
            return new EntregaDto(e.getId(), e.getTurno().getDescripcion(), e.getResumen(), e.getNota(),
                    e.getEntregadoPor(), e.getFecha());
        }
    }
}
