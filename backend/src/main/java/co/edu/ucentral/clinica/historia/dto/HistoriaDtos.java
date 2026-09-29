package co.edu.ucentral.clinica.historia.dto;

import co.edu.ucentral.clinica.historia.Antecedente;
import co.edu.ucentral.clinica.historia.Consulta;
import co.edu.ucentral.clinica.historia.ConsultaDiagnostico;
import co.edu.ucentral.clinica.historia.Diagnostico;
import co.edu.ucentral.clinica.historia.EvaluadorSignos;
import co.edu.ucentral.clinica.historia.Medicamento;
import co.edu.ucentral.clinica.historia.Prescripcion;
import co.edu.ucentral.clinica.historia.RegistroSignos;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Respuestas del módulo de historia clínica. */
public final class HistoriaDtos {

    private HistoriaDtos() {
    }

    public record DiagnosticoDto(Long id, String codigo, String nombre) {
        public static DiagnosticoDto de(Diagnostico d) {
            return new DiagnosticoDto(d.getId(), d.getCodigo(), d.getNombre());
        }
    }

    public record MedicamentoDto(Long id, String nombre, String principioActivo, String familia, String presentacion,
                                 String via) {
        public static MedicamentoDto de(Medicamento m) {
            return new MedicamentoDto(m.getId(), m.getNombre(), m.getPrincipioActivo(), m.getFamilia(),
                    m.getPresentacion(), m.getVia());
        }
    }

    public record AntecedenteDto(Long id, String tipo, String tipoDescripcion, String descripcion, String detalle,
                                 String severidad, boolean activo, String motivoInactivacion, String registradoPor,
                                 Instant registradoEn, String modificadoPor, Instant modificadoEn) {
        public static AntecedenteDto de(Antecedente a) {
            return new AntecedenteDto(a.getId(), a.getTipo().name(), a.getTipo().getDescripcion(), a.getDescripcion(),
                    a.getDetalle(), a.getSeveridad() == null ? null : a.getSeveridad().name(), a.isActivo(),
                    a.getMotivoInactivacion(), a.getCreadoPor(), a.getCreadoEn(), a.getModificadoPor(), a.getModificadoEn());
        }
    }

    /** @param origen TRIAGE (admisión) o REGISTRO (toma de signos en consulta o enfermería) */
    public record SignosDto(Long id, String origen, Integer presionSistolica, Integer presionDiastolica,
                            Integer frecuenciaCardiaca, Integer frecuenciaRespiratoria, BigDecimal temperatura,
                            Integer saturacionOxigeno, BigDecimal peso, Integer talla, String registradoPor,
                            Instant fecha, List<EvaluadorSignos.Alerta> alertas) {
        public static SignosDto de(RegistroSignos s, List<EvaluadorSignos.Alerta> alertas) {
            return new SignosDto(s.getId(), "REGISTRO", s.getPresionSistolica(), s.getPresionDiastolica(),
                    s.getFrecuenciaCardiaca(), s.getFrecuenciaRespiratoria(), s.getTemperatura(), s.getSaturacionOxigeno(),
                    s.getPeso(), s.getTalla(), s.getRegistradoPor(), s.getFecha(), alertas);
        }
    }

    public record DiagnosticoConsultaDto(Long diagnosticoId, String codigo, String nombre, boolean principal,
                                         boolean confirmado) {
        public static DiagnosticoConsultaDto de(ConsultaDiagnostico d) {
            return new DiagnosticoConsultaDto(d.getDiagnostico().getId(), d.getDiagnostico().getCodigo(),
                    d.getDiagnostico().getNombre(), d.isPrincipal(), d.isConfirmado());
        }
    }

    public record PrescripcionDto(Long id, Long medicamentoId, String medicamento, String presentacion, String via,
                                  String dosis, String frecuencia, int duracionDias, String indicaciones) {
        public static PrescripcionDto de(Prescripcion p) {
            Medicamento m = p.getMedicamento();
            return new PrescripcionDto(p.getId(), m.getId(), m.getNombre(), m.getPresentacion(), m.getVia(), p.getDosis(),
                    p.getFrecuencia(), p.getDuracionDias(), p.getIndicaciones());
        }
    }

    /**
     * @param pendientes    lo que falta para poder firmar (RFMT-007); vacío = se puede finalizar
     * @param inicioCita    hora de la cita que originó la consulta, si la hay
     * @param numeroEpisodio número de atención de admisiones, si la hay
     */
    public record ConsultaDto(Long id, PacienteResumenDto paciente, Long episodioId, String numeroEpisodio, Long citaId,
                              Instant inicioCita, String medico, String medicoNombre, String estado,
                              String estadoDescripcion, String motivoConsulta, String enfermedadActual,
                              String examenFisico, String plan, Instant inicio, Instant firmadaEn, String firmaHash,
                              List<DiagnosticoConsultaDto> diagnosticos, List<PrescripcionDto> prescripciones,
                              List<SignosDto> signos, List<String> pendientes, String modificadoPor,
                              Instant modificadoEn) {
        public static ConsultaDto de(Consulta c, PacienteResumenDto paciente, String numeroEpisodio, Instant inicioCita,
                                     List<SignosDto> signos, List<String> pendientes) {
            return new ConsultaDto(c.getId(), paciente, c.getEpisodioId(), numeroEpisodio, c.getCitaId(), inicioCita,
                    c.getMedico(), c.getMedicoNombre(), c.getEstado().name(), c.getEstado().getDescripcion(),
                    c.getMotivoConsulta(), c.getEnfermedadActual(), c.getExamenFisico(), c.getPlan(), c.getInicio(),
                    c.getFirmadaEn(), c.getFirmaHash(),
                    c.getDiagnosticos().stream().map(DiagnosticoConsultaDto::de).toList(),
                    c.getPrescripciones().stream().map(PrescripcionDto::de).toList(), signos, pendientes,
                    c.getModificadoPor(), c.getModificadoEn());
        }
    }

    /** Medicamento que el paciente toma: registrado como antecedente o formulado en una consulta vigente. */
    public record MedicamentoActualDto(String nombre, String detalle, String origen, Instant desde) {
    }

    public record DiagnosticoActivoDto(String codigo, String nombre, String origen, Instant fecha) {
    }

    public record TriageActualDto(int nivel, String descripcion, String sintomaPrincipal, Instant fecha) {
    }

    /**
     * RFMT-002: lo que el médico necesita ver en los primeros minutos, en una sola vista.
     *
     * @param alertas            resumen de lo crítico (alergias severas, signos críticos, triage alto)
     * @param resultadosRecientes últimos resultados de laboratorio validados, con semáforo
     */
    public record ResumenClinicoDto(PacienteResumenDto paciente, List<AntecedenteDto> alergias,
                                    List<MedicamentoActualDto> medicamentosActuales,
                                    List<DiagnosticoActivoDto> diagnosticosActivos,
                                    List<AntecedenteDto> antecedentesQuirurgicos, List<AntecedenteDto> otrosAntecedentes,
                                    SignosDto ultimosSignos, TriageActualDto triage, List<String> alertas,
                                    List<LabDtos.ResultadoRecienteDto> resultadosRecientes) {
    }

    /** Resumen de una consulta para el historial (RFMT-009). */
    public record ConsultaHistorialDto(Long id, Instant inicio, Instant firmadaEn, String estado, String medicoNombre,
                                       String motivoConsulta, String plan, List<DiagnosticoConsultaDto> diagnosticos,
                                       List<PrescripcionDto> prescripciones) {
        public static ConsultaHistorialDto de(Consulta c) {
            return new ConsultaHistorialDto(c.getId(), c.getInicio(), c.getFirmadaEn(), c.getEstado().name(),
                    c.getMedicoNombre(), c.getMotivoConsulta(), c.getPlan(),
                    c.getDiagnosticos().stream().map(DiagnosticoConsultaDto::de).toList(),
                    c.getPrescripciones().stream().map(PrescripcionDto::de).toList());
        }
    }

    /**
     * Paciente en la lista de trabajo del médico (RFMT-012).
     *
     * @param prioridad CRITICO, ALERTA o NORMAL: define el color y el orden
     * @param motivosPrioridad por qué se destaca (triage I, SpO₂ crítica, alergia severa...)
     */
    public record PacientePorAtenderDto(String origen, Long episodioId, Long citaId, Long consultaId,
                                        PacienteResumenDto paciente, String motivo, Instant desde, Integer nivelTriage,
                                        String prioridad, List<String> motivosPrioridad, int alergias) {
    }

    public record VerificacionFirmaDto(boolean valida, String firmadaPor, Instant firmadaEn, String huellaGuardada,
                                       String huellaCalculada) {
    }
}
