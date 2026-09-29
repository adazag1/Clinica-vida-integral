package co.edu.ucentral.clinica.historia;

import co.edu.ucentral.clinica.admisiones.Episodio;
import co.edu.ucentral.clinica.admisiones.EpisodioRepository;
import co.edu.ucentral.clinica.admisiones.EstadoEpisodio;
import co.edu.ucentral.clinica.admisiones.NivelTriage;
import co.edu.ucentral.clinica.admisiones.Triage;
import co.edu.ucentral.clinica.admisiones.TriageRepository;
import co.edu.ucentral.clinica.agenda.CalculadoraDisponibilidad;
import co.edu.ucentral.clinica.agenda.Cita;
import co.edu.ucentral.clinica.agenda.CitaRepository;
import co.edu.ucentral.clinica.agenda.EventoCita;
import co.edu.ucentral.clinica.agenda.EventoCitaRepository;
import co.edu.ucentral.clinica.agenda.ProfesionalRepository;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.ConsultaDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.PacientePorAtenderDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.SignosDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.VerificacionFirmaDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.DiagnosticoRequest;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.IniciarConsultaRequest;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.NotasRequest;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.PrescripcionRequest;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;
import co.edu.ucentral.clinica.seguridad.Usuario;
import co.edu.ucentral.clinica.seguridad.UsuarioRepository;
import org.springframework.data.domain.AuditorAware;
import co.edu.ucentral.clinica.comun.seguridad.Reautenticador;
import co.edu.ucentral.clinica.documentos.DocumentoGenerado;
import co.edu.ucentral.clinica.documentos.TipoDocumento;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Consulta médica: lista de trabajo, notas, diagnósticos, fórmula y firma (RFMT-003, 007, 008, 011, 012). */
@Service
public class ConsultaService {

    private final ConsultaRepository consultas;
    private final DiagnosticoRepository diagnosticos;
    private final MedicamentoRepository medicamentos;
    private final AntecedenteRepository antecedentes;
    private final RegistroSignosRepository signos;
    private final EpisodioRepository episodios;
    private final TriageRepository triages;
    private final CitaRepository citas;
    private final EventoCitaRepository eventosCita;
    private final ProfesionalRepository profesionales;
    private final UsuarioRepository usuarios;
    private final PacienteService pacienteService;
    private final HistoriaService historia;
    private final ValidadorAlergias validadorAlergias;
    private final EvaluadorSignos evaluador;
    private final FirmaClinica firma;
    private final Reautenticador reautenticador;
    private final ApplicationEventPublisher publicador;
    private final CalculadoraDisponibilidad calendario;
    private final AuditorAware<String> auditor;

    public ConsultaService(ConsultaRepository consultas, DiagnosticoRepository diagnosticos,
                           MedicamentoRepository medicamentos, AntecedenteRepository antecedentes,
                           RegistroSignosRepository signos, EpisodioRepository episodios, TriageRepository triages,
                           CitaRepository citas, EventoCitaRepository eventosCita, ProfesionalRepository profesionales,
                           UsuarioRepository usuarios, PacienteService pacienteService, HistoriaService historia,
                           ValidadorAlergias validadorAlergias, EvaluadorSignos evaluador, FirmaClinica firma,
                           Reautenticador reautenticador, ApplicationEventPublisher publicador,
                           CalculadoraDisponibilidad calendario, AuditorAware<String> auditor) {
        this.consultas = consultas;
        this.diagnosticos = diagnosticos;
        this.medicamentos = medicamentos;
        this.antecedentes = antecedentes;
        this.signos = signos;
        this.episodios = episodios;
        this.triages = triages;
        this.citas = citas;
        this.eventosCita = eventosCita;
        this.profesionales = profesionales;
        this.usuarios = usuarios;
        this.pacienteService = pacienteService;
        this.historia = historia;
        this.validadorAlergias = validadorAlergias;
        this.evaluador = evaluador;
        this.firma = firma;
        this.reautenticador = reautenticador;
        this.publicador = publicador;
        this.calendario = calendario;
        this.auditor = auditor;
    }

    // ---------- Lista de trabajo del médico (RFMT-012) ----------

    /**
     * Sus consultas abiertas, los pacientes que admisiones dejó listos y sus citas de hoy.
     * Los críticos (triage I-II o signos críticos) van primero y se destacan en rojo.
     */
    @Transactional(readOnly = true)
    public List<PacientePorAtenderDto> porAtender() {
        String usuario = usuarioActual();
        List<PacientePorAtenderDto> lista = new ArrayList<>();
        Set<Long> episodiosEnConsulta = new HashSet<>();
        Set<Long> citasEnConsulta = new HashSet<>();

        for (Consulta c : consultas.findByMedicoIgnoreCaseAndEstado(usuario, Consulta.Estado.EN_CURSO)) {
            if (c.getEpisodioId() != null) episodiosEnConsulta.add(c.getEpisodioId());
            if (c.getCitaId() != null) citasEnConsulta.add(c.getCitaId());
            Paciente p = pacienteService.buscarEntidad(c.getPacienteId());
            lista.add(item("EN_CURSO", c.getEpisodioId(), c.getCitaId(), c.getId(), p, c.getMotivoConsulta(), c.getInicio(),
                    c.getEpisodioId() == null ? null : triages.findByEpisodioId(c.getEpisodioId()).map(Triage::getNivelAsignado).orElse(null)));
        }
        for (Episodio e : episodios.buscarPorEstados(EnumSet.of(EstadoEpisodio.EN_ESPERA_CONSULTA))) {
            if (!episodiosEnConsulta.contains(e.getId())) {
                lista.add(item(e.getTipoAtencion().name(), e.getId(), null, null, e.getPaciente(), e.getMotivoConsulta(),
                        e.getFechaListo(), triages.findByEpisodioId(e.getId()).map(Triage::getNivelAsignado).orElse(null)));
            }
        }
        Optional<Long> miAgenda = usuarios.findByUsernameIgnoreCase(usuario)
                .flatMap(u -> profesionales.findByUsuarioId(u.getId())).map(p -> p.getId());
        if (miAgenda.isPresent()) {
            LocalDate hoy = LocalDate.now(calendario.getZona());
            citas.agenda(List.of(miAgenda.get()), calendario.inicioDelDia(hoy), calendario.inicioDelDia(hoy.plusDays(1)))
                    .stream()
                    .filter(c -> c.getEstado().isPendiente() && !citasEnConsulta.contains(c.getId()))
                    .forEach(c -> lista.add(item("CITA", null, c.getId(), null, c.getPaciente(),
                            c.getMotivo() != null ? c.getMotivo() : c.getTipo().getNombre(), c.getInicio(), null)));
        }
        lista.sort(Comparator.comparingInt((PacientePorAtenderDto i) -> orden(i.prioridad()))
                .thenComparing(i -> "EN_CURSO".equals(i.origen()) ? 0 : 1)
                .thenComparing(i -> i.nivelTriage() == null ? 6 : i.nivelTriage())
                .thenComparing(PacientePorAtenderDto::desde, Comparator.nullsLast(Comparator.naturalOrder())));
        return lista;
    }

    private PacientePorAtenderDto item(String origen, Long episodioId, Long citaId, Long consultaId, Paciente p,
                                       String motivo, Instant desde, Integer nivelTriage) {
        List<String> motivos = new ArrayList<>();
        String prioridad = "NORMAL";
        if (nivelTriage != null && nivelTriage <= 2) {
            prioridad = "CRITICO";
            motivos.add(NivelTriage.descripcion(nivelTriage));
        } else if (nivelTriage != null && nivelTriage == 3) {
            prioridad = "ALERTA";
            motivos.add(NivelTriage.descripcion(nivelTriage));
        }
        SignosDto ultimos = historia.ultimosSignos(p.getId(), historia.triageActual(p.getId()));
        if (ultimos != null) {
            for (EvaluadorSignos.Alerta a : ultimos.alertas()) {
                if (a.nivel() == EvaluadorSignos.Nivel.CRITICO) {
                    prioridad = "CRITICO";
                    motivos.add(a.etiqueta() + " " + a.valor());
                } else if (!"CRITICO".equals(prioridad)) {
                    prioridad = "ALERTA";
                }
            }
        }
        int alergias = antecedentes.findByPacienteIdAndTipoAndActivoTrue(p.getId(), Antecedente.Tipo.ALERGIA).size();
        return new PacientePorAtenderDto(origen, episodioId, citaId, consultaId, PacienteResumenDto.de(p), motivo, desde,
                nivelTriage, prioridad, motivos, alergias);
    }

    private static int orden(String prioridad) {
        return switch (prioridad) {
            case "CRITICO" -> 0;
            case "ALERTA" -> 1;
            default -> 2;
        };
    }

    // ---------- Ciclo de la consulta ----------

    /** Abre la consulta. Si el médico ya la tenía abierta para ese episodio o cita, la retoma. */
    @Transactional
    public ConsultaDto iniciar(IniciarConsultaRequest r) {
        String usuario = usuarioActual();
        Paciente paciente = pacienteService.buscarEntidad(r.pacienteId());
        String motivo = null;

        if (r.episodioId() != null) {
            Episodio e = episodios.findById(r.episodioId())
                    .filter(x -> x.getPaciente().getId().equals(paciente.getId()))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Episodio", r.episodioId()));
            Optional<Consulta> abierta = consultas.findFirstByEpisodioIdAndEstado(e.getId(), Consulta.Estado.EN_CURSO);
            if (abierta.isPresent()) {
                return retomar(abierta.get(), usuario);
            }
            e.iniciarAtencion();
            motivo = e.getMotivoConsulta();
        }
        if (r.citaId() != null) {
            Cita c = citas.findById(r.citaId())
                    .filter(x -> x.getPaciente().getId().equals(paciente.getId()))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Cita", r.citaId()));
            Optional<Consulta> abierta = consultas.findFirstByCitaIdAndEstado(c.getId(), Consulta.Estado.EN_CURSO);
            if (abierta.isPresent()) {
                return retomar(abierta.get(), usuario);
            }
            if (!c.getEstado().isPendiente()) {
                throw new ReglaNegocioException("CITA_NO_PENDIENTE",
                        "La cita está " + c.getEstado().getDescripcion().toLowerCase() + ".");
            }
            if (motivo == null) {
                motivo = c.getMotivo() != null ? c.getMotivo() : c.getTipo().getNombre();
            }
        }
        String nombre = usuarios.findByUsernameIgnoreCase(usuario).map(Usuario::getNombreCompleto).orElse(usuario);
        return armar(consultas.save(new Consulta(paciente.getId(), r.episodioId(), r.citaId(), usuario, nombre, motivo)));
    }

    private ConsultaDto retomar(Consulta c, String usuario) {
        if (!c.getMedico().equalsIgnoreCase(usuario)) {
            throw new ReglaNegocioException("EN_ATENCION_OTRO_MEDICO",
                    "El paciente ya está siendo atendido por " + c.getMedicoNombre() + ".");
        }
        return armar(c);
    }

    @Transactional(readOnly = true)
    public ConsultaDto obtener(Long id) {
        return armar(buscar(id));
    }

    /** "Guardar" del mockup: notas y diagnósticos. Se puede guardar incompleta; firmar no. */
    @Transactional
    public ConsultaDto guardar(Long id, NotasRequest r) {
        Consulta c = buscar(id);
        c.exigirEditable(usuarioActual());
        if (r.diagnosticos().stream().filter(DiagnosticoRequest::principal).count() > 1) {
            throw new ReglaNegocioException("VARIOS_PRINCIPALES", "Solo puede haber un diagnóstico principal.");
        }
        Set<Long> vistos = new HashSet<>();
        List<ConsultaDiagnostico> nuevos = new ArrayList<>();
        for (DiagnosticoRequest d : r.diagnosticos()) {
            if (!vistos.add(d.diagnosticoId())) {
                continue;
            }
            Diagnostico dx = diagnosticos.findById(d.diagnosticoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Diagnóstico", d.diagnosticoId()));
            nuevos.add(new ConsultaDiagnostico(dx, d.principal(), d.confirmado()));
        }
        c.actualizarNotas(limpio(r.motivoConsulta()), limpio(r.enfermedadActual()), limpio(r.examenFisico()), limpio(r.plan()));
        c.reemplazarDiagnosticos(nuevos);
        consultas.flush();
        return armar(c);
    }

    /** RFMT-003: la alergia registrada bloquea la prescripción; no hay forma de saltarse la alerta. */
    @Transactional
    public ConsultaDto agregarPrescripcion(Long id, PrescripcionRequest r) {
        Consulta c = buscar(id);
        c.exigirEditable(usuarioActual());
        Medicamento m = medicamentos.findById(r.medicamentoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Medicamento", r.medicamentoId()));
        validadorAlergias.conflicto(antecedentes.findByPacienteIdAndTipoAndActivoTrue(c.getPacienteId(),
                Antecedente.Tipo.ALERGIA), m).ifPresent(a -> {
            throw new ReglaNegocioException("ALERGIA_MEDICAMENTO", "Prescripción bloqueada: el paciente es alérgico a "
                    + a.getDescripcion() + (a.getSeveridad() == null ? "" : " (" + a.getSeveridad().name().toLowerCase() + ")")
                    + (a.getDetalle() == null ? "" : ", reacción: " + a.getDetalle()) + ". " + m.getNombre()
                    + " pertenece a " + m.getFamilia() + ".");
        });
        if (c.getPrescripciones().stream().anyMatch(p -> p.getMedicamento().getId().equals(m.getId()))) {
            throw new ReglaNegocioException("MEDICAMENTO_REPETIDO", m.getNombre() + " ya está en la fórmula.");
        }
        c.agregarPrescripcion(new Prescripcion(m, r.dosis().trim(), r.frecuencia().trim(), r.duracionDias(),
                limpio(r.indicaciones()), usuarioActual()));
        consultas.flush();
        return armar(c);
    }

    @Transactional
    public ConsultaDto quitarPrescripcion(Long id, Long prescripcionId) {
        Consulta c = buscar(id);
        c.exigirEditable(usuarioActual());
        c.quitarPrescripcion(prescripcionId);
        return armar(c);
    }

    /**
     * RFMT-007 + RFMT-011: no se firma sin los campos obligatorios; el médico confirma su identidad
     * con la contraseña. Al firmar se cierra el episodio de admisión y la cita queda atendida.
     */
    @Transactional(noRollbackFor = ReglaNegocioException.class) // conserva el conteo de contraseñas erradas
    public ConsultaDto finalizar(Long id, String password) {
        String usuario = usuarioActual();
        Consulta c = buscar(id);
        c.exigirEditable(usuario);
        List<String> pendientes = pendientes(c);
        if (!pendientes.isEmpty()) {
            throw new ReglaNegocioException("CONSULTA_INCOMPLETA",
                    "No se puede finalizar. Falta: " + String.join(", ", pendientes) + ".");
        }
        reautenticador.confirmar(usuario, password, "Contraseña incorrecta. La consulta no se firmó.");
        c.firmar(firma::huella);
        // RFGAC-009: la nota firmada queda registrada en la gestión documental
        publicador.publishEvent(new DocumentoGenerado(c.getPacienteId(), c.getEpisodioId(), TipoDocumento.HISTORIA_CLINICA,
                "Consulta médica · " + c.getMedicoNombre(), "Consulta médica", c.getMedicoNombre(), "CONSULTA",
                String.valueOf(c.getId()), "/consulta/" + c.getId(), LocalDate.now(calendario.getZona()), null));

        if (c.getEpisodioId() != null) {
            episodios.findById(c.getEpisodioId()).ifPresent(Episodio::cerrar);
        }
        if (c.getCitaId() != null) {
            citas.findById(c.getCitaId()).filter(x -> x.getEstado().isPendiente()).ifPresent(x -> {
                x.marcarAtendida();
                eventosCita.save(new EventoCita(x.getId(), EventoCita.Accion.ATENDIDA,
                        "Consulta firmada por " + c.getMedicoNombre(), usuario));
            });
        }
        return armar(c);
    }

    /** Recalcula la huella: si alguien alteró la consulta después de firmada, no coincide. */
    @Transactional(readOnly = true)
    public VerificacionFirmaDto verificarFirma(Long id) {
        Consulta c = buscar(id);
        if (c.getEstado() != Consulta.Estado.FIRMADA) {
            throw new ReglaNegocioException("CONSULTA_SIN_FIRMA", "La consulta aún no ha sido firmada.");
        }
        String calculada = firma.huella(c);
        return new VerificacionFirmaDto(calculada.equals(c.getFirmaHash()), c.getMedicoNombre(), c.getFirmadaEn(),
                c.getFirmaHash(), calculada);
    }

    // ---------- utilidades ----------

    static List<String> pendientes(Consulta c) {
        List<String> faltan = new ArrayList<>();
        if (vacio(c.getMotivoConsulta())) faltan.add("motivo de consulta");
        if (vacio(c.getEnfermedadActual())) faltan.add("enfermedad actual");
        if (vacio(c.getExamenFisico())) faltan.add("examen físico");
        if (c.getDiagnosticos().stream().noneMatch(ConsultaDiagnostico::isPrincipal)) faltan.add("diagnóstico principal");
        if (vacio(c.getPlan())) faltan.add("plan o conducta");
        return faltan;
    }

    private ConsultaDto armar(Consulta c) {
        Paciente p = pacienteService.buscarEntidad(c.getPacienteId());
        String numeroEpisodio = c.getEpisodioId() == null ? null
                : episodios.findById(c.getEpisodioId()).map(Episodio::getNumero).orElse(null);
        Instant inicioCita = c.getCitaId() == null ? null : citas.findById(c.getCitaId()).map(Cita::getInicio).orElse(null);
        List<SignosDto> tomas = c.getId() == null ? List.of() : signos.findByConsultaIdOrderByFechaDesc(c.getId()).stream()
                .map(s -> SignosDto.de(s, evaluador.evaluar(s))).toList();
        return ConsultaDto.de(c, PacienteResumenDto.de(p), numeroEpisodio, inicioCita, tomas,
                c.getEstado() == Consulta.Estado.FIRMADA ? List.of() : pendientes(c));
    }

    private Consulta buscar(Long id) {
        return consultas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Consulta", id));
    }

    private String usuarioActual() {
        return auditor.getCurrentAuditor().orElse("sistema");
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static String limpio(String s) {
        return vacio(s) ? null : s.trim();
    }
}
