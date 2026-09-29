package co.edu.ucentral.clinica.laboratorio.dto;

import co.edu.ucentral.clinica.laboratorio.Analito;
import co.edu.ucentral.clinica.laboratorio.ControlCalidad;
import co.edu.ucentral.clinica.laboratorio.EventoOrdenLab;
import co.edu.ucentral.clinica.laboratorio.ExamenLab;
import co.edu.ucentral.clinica.laboratorio.RangoReferencia;
import co.edu.ucentral.clinica.laboratorio.ReprocesoLab;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Respuestas del módulo de laboratorio. */
public final class LabDtos {

    private LabDtos() {
    }

    public record RangoDto(Long id, String sexo, int edadMin, int edadMax, BigDecimal refMin, BigDecimal refMax,
                           BigDecimal criticoMin, BigDecimal criticoMax, String modificadoPor, Instant modificadoEn) {
        public static RangoDto de(RangoReferencia r) {
            return new RangoDto(r.getId(), r.getSexo() == null ? null : r.getSexo().name(), r.getEdadMin(), r.getEdadMax(),
                    r.getRefMin(), r.getRefMax(), r.getCriticoMin(), r.getCriticoMax(), r.getModificadoPor(),
                    r.getModificadoEn());
        }
    }

    public record AnalitoDto(Long id, String codigo, String nombre, String unidad, int decimales,
                             BigDecimal deltaPorcentaje, List<RangoDto> rangos) {
        public static AnalitoDto de(Analito a) {
            return new AnalitoDto(a.getId(), a.getCodigo(), a.getNombre(), a.getUnidad(), a.getDecimales(),
                    a.getDeltaPorcentaje(), a.getRangos().stream().map(RangoDto::de).toList());
        }
    }

    public record ExamenDto(Long id, String codigo, String nombre, String area, String areaDescripcion,
                            String tipoMuestra, int tatUrgenteMin, int tatRutinaMin, List<AnalitoDto> analitos) {
        public static ExamenDto de(ExamenLab e) {
            return new ExamenDto(e.getId(), e.getCodigo(), e.getNombre(), e.getArea().name(),
                    e.getArea().getDescripcion(), e.getTipoMuestra(), e.getTatUrgenteMin(), e.getTatRutinaMin(),
                    e.getAnalitos().stream().map(AnalitoDto::de).toList());
        }
    }

    /** Fila de la cola del laboratorio y de las órdenes de un paciente. */
    public record OrdenResumenDto(Long id, String numero, PacienteResumenDto paciente, String medicoNombre,
                                  String prioridad, String prioridadDescripcion, String estado, String estadoDescripcion,
                                  List<String> examenes, Instant ordenadaEn, Instant limiteTat, boolean tatVencido,
                                  boolean tieneCriticos, Instant validadaEn) {
    }

    public record ReprocesoDto(BigDecimal valorAnterior, BigDecimal valorNuevo, String motivo, String motivoDescripcion,
                               String detalle, String usuario, Instant fecha) {
        public static ReprocesoDto de(ReprocesoLab r) {
            return new ReprocesoDto(r.getValorAnterior(), r.getValorNuevo(), r.getMotivo().name(),
                    r.getMotivo().getDescripcion(), r.getDetalle(), r.getUsuario(), r.getFecha());
        }
    }

    /**
     * Fila de la tabla "Resultados" del mockup. Sin valor aún, {@code valor} e {@code interpretacion}
     * vienen en null y {@code rangoTexto} muestra el rango que aplicará al paciente.
     */
    public record ResultadoFilaDto(Long analitoId, String codigo, String nombre, String unidad, int decimales,
                                   BigDecimal valor, String interpretacion, String interpretacionDescripcion,
                                   String rangoTexto, BigDecimal valorPrevio, Instant fechaPrevio, BigDecimal delta,
                                   boolean alertaDelta, int repeticiones, List<ReprocesoDto> reprocesos) {
    }

    public record OrdenExamenDto(Long id, Long examenId, String codigo, String nombre, String area,
                                 String areaDescripcion, String tipoMuestra, String equipo, String loteReactivo,
                                 String calibracion, List<ResultadoFilaDto> resultados) {
    }

    public record ControlDiaDto(String area, String areaDescripcion, Boolean aceptado, String equipo,
                                String registradoPor, Instant registradoEn) {
    }

    public record EventoDto(String accion, String detalle, String usuario, Instant fecha) {
        public static EventoDto de(EventoOrdenLab e) {
            return new EventoDto(e.getAccion(), e.getDetalle(), e.getUsuario(), e.getFecha());
        }
    }

    /** RFLC-013: lo clínico que el validador necesita ver antes de liberar. */
    public record ContextoClinicoDto(String sexo, Integer edad, String motivoConsulta, List<String> diagnosticos,
                                     List<String> alergias) {
    }

    /**
     * Orden completa para la pantalla del laboratorio (mockup) y para el médico.
     *
     * @param pendientesValidacion lo que impide validar (RFLC-007, 008, 010 y valores críticos)
     * @param advertencias         revisiones sugeridas que no bloquean (delta check, coherencia Hb/Hto)
     */
    public record OrdenDto(Long id, String numero, PacienteResumenDto paciente, Long consultaId, String medico,
                           String medicoNombre, String prioridad, String prioridadDescripcion, String indicaciones,
                           String estado, String estadoDescripcion, Instant ordenadaEn, Instant muestraTomadaEn,
                           String muestraTomadaPor, Instant muestraRecibidaEn, Instant procesoEn, Instant validadaEn,
                           String validadaPor, String motivoRechazo, String motivoRechazoDescripcion,
                           String detalleRechazo, String criticoContacto, String criticoMedio,
                           Instant criticoNotificadoEn, Instant limiteTat, boolean tatVencido, boolean tieneCriticos,
                           List<OrdenExamenDto> examenes, List<ControlDiaDto> controles,
                           List<String> pendientesValidacion, List<String> advertencias, List<EventoDto> historial,
                           ContextoClinicoDto contexto) {
    }

    public record ControlCalidadDto(Long id, String area, String areaDescripcion, String equipo, LocalDate fecha,
                                    String niveles, String loteControl, boolean aceptado, String observacion,
                                    String registradoPor, Instant registradoEn) {
        public static ControlCalidadDto de(ControlCalidad c) {
            return new ControlCalidadDto(c.getId(), c.getArea().name(), c.getArea().getDescripcion(), c.getEquipo(),
                    c.getFecha(), c.getNiveles(), c.getLoteControl(), c.isAceptado(), c.getObservacion(),
                    c.getRegistradoPor(), c.getRegistradoEn());
        }
    }

    /** RFMT-002 / RFMT-005: resultado validado para el resumen clínico del médico. */
    public record ResultadoRecienteDto(Long ordenId, String examen, String analito, BigDecimal valor, String unidad,
                                       int decimales, String interpretacion, String interpretacionDescripcion,
                                       String rangoTexto, Instant fecha) {
    }
}
