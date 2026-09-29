package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.agenda.dto.CitaDto;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.CancelacionRequest;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.CitaRequest;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.ReprogramacionRequest;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.notificaciones.NotificacionService;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import co.edu.ucentral.clinica.seguridad.Rol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Ciclo de vida de las citas. Toda reserva pasa por {@link #reservar}, que bloquea la agenda
 * del profesional antes de verificar choques (RFAGE-005 / RNFAGE-001).
 */
@Service
public class CitaService {

    static final String USUARIO_PACIENTE = "paciente (enlace)";
    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, h:mm a", Locale.forLanguageTag("es-CO"));

    private final CitaRepository citas;
    private final ProfesionalRepository profesionales;
    private final TipoCitaRepository tipos;
    private final HorarioRepository horarios;
    private final BloqueoRepository bloqueos;
    private final EventoCitaRepository eventos;
    private final RecordatorioRepository recordatorios;
    private final ListaEsperaRepository listaEspera;
    private final PacienteService pacienteService;
    private final NotificacionService notificaciones;
    private final CalculadoraDisponibilidad calculadora;
    private final AuditorAware<String> auditor;
    private final String frontendUrl;

    public CitaService(CitaRepository citas, ProfesionalRepository profesionales, TipoCitaRepository tipos,
                       HorarioRepository horarios, BloqueoRepository bloqueos, EventoCitaRepository eventos,
                       RecordatorioRepository recordatorios, ListaEsperaRepository listaEspera,
                       PacienteService pacienteService, NotificacionService notificaciones,
                       CalculadoraDisponibilidad calculadora, AuditorAware<String> auditor,
                       @Value("${app.frontend-url}") String frontendUrl) {
        this.citas = citas;
        this.profesionales = profesionales;
        this.tipos = tipos;
        this.horarios = horarios;
        this.bloqueos = bloqueos;
        this.eventos = eventos;
        this.recordatorios = recordatorios;
        this.listaEspera = listaEspera;
        this.pacienteService = pacienteService;
        this.notificaciones = notificaciones;
        this.calculadora = calculadora;
        this.auditor = auditor;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /** Resultado de liberar un cupo: la cita afectada y, si hubo, la nueva cita de la lista de espera. */
    public record ResultadoCupoLiberado(CitaDto cita, CitaDto asignadaListaEspera) {
    }

    @Transactional
    public CitaDto crear(CitaRequest r) {
        Paciente paciente = pacienteService.buscarEntidad(r.pacienteId());
        // RFAGE-002: solo se agenda a pacientes con datos base válidos
        if (paciente.isIdentificacionTemporal()) {
            throw new ReglaNegocioException("PACIENTE_SIN_DOCUMENTO",
                    "Registre el documento del paciente antes de agendarle una cita.");
        }
        TipoCita tipo = tipos.findById(r.tipoCitaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de cita", r.tipoCitaId()));
        Cita cita = reservar(paciente, r.profesionalId(), tipo, r.inicio(), r.prioridad(), limpio(r.motivo()), null);
        registrar(cita, EventoCita.Accion.CREADA, cita.getPrioridad() == PrioridadCita.NORMAL ? null
                : "Prioridad " + cita.getPrioridad().getDescripcion().toLowerCase());
        return detalle(cita);
    }

    /**
     * Reserva un horario con la agenda del profesional bloqueada: valida horario de atención,
     * franjas reservadas, bloqueos y que ni el profesional ni el paciente tengan otra cita a esa hora.
     */
    Cita reservar(Paciente paciente, Long profesionalId, TipoCita tipo, Instant inicioSolicitado,
                  PrioridadCita prioridad, String motivo, Long reprogramadaDesde) {
        Profesional profesional = profesionales.bloquearParaAgendar(profesionalId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Profesional", profesionalId));
        if (!profesional.isActivo()) {
            throw new ReglaNegocioException("PROFESIONAL_INACTIVO", "El profesional no tiene agenda activa.");
        }
        Instant inicio = inicioSolicitado.truncatedTo(ChronoUnit.MINUTES);
        Instant fin = inicio.plus(tipo.getDuracion());
        calculadora.validarHorario(inicio, fin,
                horarios.findByProfesionalIdOrderByDiaSemanaAscHoraInicioAsc(profesionalId), prioridad, Instant.now());

        bloqueos.enRango(List.of(profesionalId), inicio, fin).stream().findFirst().ifPresent(b -> {
            throw new ReglaNegocioException("AGENDA_BLOQUEADA",
                    "El profesional no atiende en ese horario (" + b.getMotivo().getDescripcion().toLowerCase() + ").");
        });
        if (!citas.ocupadasDelProfesional(profesionalId, inicio, fin, AgendaService.OCUPAN_CUPO).isEmpty()) {
            throw new ReglaNegocioException("HORARIO_OCUPADO",
                    "Ese horario ya está asignado. Actualice la agenda y elija otro cupo.");
        }
        if (!citas.ocupadasDelPaciente(paciente.getId(), inicio, fin, AgendaService.OCUPAN_CUPO).isEmpty()) {
            throw new ReglaNegocioException("PACIENTE_CON_CITA", "El paciente ya tiene otra cita a esa hora.");
        }
        Cita cita = citas.save(new Cita(paciente, profesional, tipo, inicio, prioridad, motivo, reprogramadaDesde));
        avisarPreparacion(cita);
        return cita;
    }

    /** RFAGE-013: el área que prepara al paciente se entera apenas se agenda. */
    private void avisarPreparacion(Cita cita) {
        TipoCita tipo = cita.getTipo();
        if (tipo.isRequierePreparacion() && tipo.getRolAreaPreparacion() != null) {
            notificaciones.paraRol(tipo.getRolAreaPreparacion(), "Preparación: " + tipo.getNombre(),
                    cita.getPaciente().getNombreCompleto() + " · " + fecha(cita.getInicio()) + " · "
                            + cita.getProfesional().getNombre() + ". " + tipo.getInstruccionesPreparacion(),
                    "/agenda?cita=" + cita.getId());
        }
    }

    @Transactional(readOnly = true)
    public CitaDto obtener(Long id) {
        return detalle(buscar(id));
    }

    @Transactional(readOnly = true)
    public List<CitaDto> dePaciente(Long pacienteId) {
        pacienteService.buscarEntidad(pacienteId);
        return citas.dePaciente(pacienteId).stream().map(CitaDto::resumen).toList();
    }

    @Transactional
    public CitaDto confirmar(Long id) {
        Cita cita = buscar(id);
        cita.confirmar();
        registrar(cita, EventoCita.Accion.CONFIRMADA, "Confirmada por el personal");
        return detalle(cita);
    }

    /** RFAGE-008 + RFAGE-007: cancela con motivo y ofrece el cupo a la lista de espera. */
    @Transactional
    public ResultadoCupoLiberado cancelar(Long id, CancelacionRequest r) {
        Cita cita = buscar(id);
        cita.cancelar(r.motivo(), limpio(r.detalle()));
        registrar(cita, EventoCita.Accion.CANCELADA, r.motivo().getDescripcion()
                + (limpio(r.detalle()) == null ? "" : ": " + r.detalle().trim()));
        Optional<Cita> asignada = reasignarDesdeListaEspera(cita);
        return new ResultadoCupoLiberado(detalle(cita), asignada.map(CitaDto::resumen).orElse(null));
    }

    /**
     * RFAGE-008: la cita original queda como REPROGRAMADA y la nueva enlazada a ella, así el
     * historial muestra de dónde viene. El cupo liberado se ofrece a la lista de espera.
     */
    @Transactional
    public ResultadoCupoLiberado reprogramar(Long id, ReprogramacionRequest r) {
        Cita original = buscar(id);
        Long profesionalId = r.profesionalId() != null ? r.profesionalId() : original.getProfesional().getId();
        // Primero se bloquea la agenda original, para que nadie tome ese cupo en el intermedio
        profesionales.bloquearParaAgendar(original.getProfesional().getId());
        original.marcarReprogramada();
        citas.flush();
        Cita nueva = reservar(original.getPaciente(), profesionalId, original.getTipo(), r.inicio(),
                original.getPrioridad(), original.getMotivo(), original.getId());
        String detalle = "De " + fecha(original.getInicio()) + " a " + fecha(nueva.getInicio())
                + (profesionalId.equals(original.getProfesional().getId()) ? "" : " con " + nueva.getProfesional().getNombre())
                + (limpio(r.detalle()) == null ? "" : ". " + r.detalle().trim());
        registrar(original, EventoCita.Accion.REPROGRAMADA, detalle);
        registrar(nueva, EventoCita.Accion.CREADA, "Reprogramación de la cita #" + original.getId() + ". " + detalle);
        Optional<Cita> asignada = reasignarDesdeListaEspera(original);
        return new ResultadoCupoLiberado(detalle(nueva), asignada.map(CitaDto::resumen).orElse(null));
    }

    @Transactional
    public CitaDto registrarAsistencia(Long id, boolean asistio) {
        Cita cita = buscar(id);
        cita.registrarAsistencia(asistio);
        registrar(cita, asistio ? EventoCita.Accion.ATENDIDA : EventoCita.Accion.NO_ASISTIO, null);
        return detalle(cita);
    }

    /**
     * RFAGE-007: entrega el cupo liberado al paciente de la lista de espera con mayor prioridad
     * (urgente, prioritaria, normal) y, a igual prioridad, al que lleva más tiempo esperando.
     * Solo si la especialidad coincide, el médico pedido (si pidió uno) es este y la duración cabe.
     */
    Optional<Cita> reasignarDesdeListaEspera(Cita liberada) {
        if (liberada.getInicio().isBefore(Instant.now())) {
            return Optional.empty();
        }
        Profesional p = liberada.getProfesional();
        Duration cupo = Duration.between(liberada.getInicio(), liberada.getFin());
        List<EntradaListaEspera> candidatos = listaEspera.activas().stream()
                .filter(e -> e.getEspecialidad().getId().equals(p.getEspecialidad().getId()))
                .filter(e -> e.getProfesional() == null || e.getProfesional().getId().equals(p.getId()))
                .filter(e -> e.getTipo().getDuracion().compareTo(cupo) <= 0)
                .filter(e -> !e.getPaciente().getId().equals(liberada.getPaciente().getId()))
                .sorted(Comparator.comparing(EntradaListaEspera::getPrioridad)
                        .thenComparing(EntradaListaEspera::getCreadoEn))
                .toList();
        for (EntradaListaEspera e : candidatos) {
            try {
                Cita cita = reservar(e.getPaciente(), p.getId(), e.getTipo(), liberada.getInicio(), e.getPrioridad(),
                        e.getMotivo(), null);
                e.asignar(cita.getId());
                registrar(cita, EventoCita.Accion.ASIGNADA_LISTA_ESPERA,
                        "Cupo liberado por la cita #" + liberada.getId() + ". Pendiente de confirmar con el paciente.");
                notificaciones.paraRol(Rol.AGENDAMIENTO, "Cupo asignado desde la lista de espera",
                        e.getPaciente().getNombreCompleto() + " · " + fecha(cita.getInicio()) + " con " + p.getNombre()
                                + ". Llame al paciente para confirmar.", "/agenda?cita=" + cita.getId());
                return Optional.of(cita);
            } catch (ReglaNegocioException choque) {
                // Este candidato no puede tomar el cupo (p. ej. tiene otra cita a esa hora): se prueba el siguiente
            }
        }
        return Optional.empty();
    }

    // ---------- RFAGE-010: confirmación digital por el paciente ----------

    public record CitaPublicaDto(String paciente, Instant inicio, Instant fin, String profesional, String especialidad,
                                String sede, String direccion, String consultorio, String tipo,
                                String preparacion, String estado, String estadoDescripcion, boolean puedeResponder) {
    }

    /** Lo mínimo para que el paciente reconozca su cita: sin documento ni datos clínicos. */
    @Transactional(readOnly = true)
    public CitaPublicaDto publica(String token) {
        Cita c = porToken(token);
        Profesional p = c.getProfesional();
        return new CitaPublicaDto(c.getPaciente().getNombres().split(" ")[0], c.getInicio(), c.getFin(), p.getNombre(),
                p.getEspecialidad().getNombre(), p.getSede().getNombre(), p.getSede().getDireccion(), p.getConsultorio(),
                c.getTipo().getNombre(), c.getTipo().getInstruccionesPreparacion(), c.getEstado().name(),
                c.getEstado().getDescripcion(), puedeResponder(c));
    }

    @Transactional
    public CitaPublicaDto confirmarPorPaciente(String token) {
        Cita c = porToken(token);
        exigirRespuestaPosible(c);
        if (c.getEstado() == EstadoCita.PROGRAMADA) {
            c.confirmar();
            eventos.save(new EventoCita(c.getId(), EventoCita.Accion.CONFIRMADA, "Confirmada por el paciente desde el enlace",
                    USUARIO_PACIENTE));
        }
        return publica(token);
    }

    @Transactional
    public CitaPublicaDto cancelarPorPaciente(String token) {
        Cita c = porToken(token);
        exigirRespuestaPosible(c);
        c.cancelar(MotivoCancelacion.SOLICITUD_PACIENTE, "Cancelada por el paciente desde el enlace");
        eventos.save(new EventoCita(c.getId(), EventoCita.Accion.CANCELADA,
                "Cancelada por el paciente desde el enlace", USUARIO_PACIENTE));
        notificaciones.paraRol(Rol.AGENDAMIENTO, "Cita cancelada por el paciente",
                c.getPaciente().getNombreCompleto() + " · " + fecha(c.getInicio()) + " con " + c.getProfesional().getNombre(),
                "/agenda?cita=" + c.getId());
        reasignarDesdeListaEspera(c);
        return publica(token);
    }

    private static boolean puedeResponder(Cita c) {
        return c.getEstado().isPendiente() && c.getInicio().isAfter(Instant.now());
    }

    private static void exigirRespuestaPosible(Cita c) {
        if (!puedeResponder(c)) {
            throw new ReglaNegocioException("CITA_NO_MODIFICABLE",
                    "Esta cita ya no se puede confirmar ni cancelar por este medio. Comuníquese con la clínica.");
        }
    }

    private Cita porToken(String token) {
        return citas.findByTokenPublico(token).orElseThrow(() -> new RecursoNoEncontradoException("Cita", "enlace"));
    }

    // ---------- utilidades ----------

    CitaDto detalle(Cita c) {
        return CitaDto.de(c,
                eventos.findByCitaIdOrderByFechaAscIdAsc(c.getId()).stream().map(CitaDto.EventoDto::de).toList(),
                recordatorios.findByCitaIdOrderByFechaDesc(c.getId()).stream().map(CitaDto.RecordatorioDto::de).toList(),
                enlacePaciente(c));
    }

    String enlacePaciente(Cita c) {
        return frontendUrl + "/cita/" + c.getTokenPublico();
    }

    String fecha(Instant instante) {
        return FORMATO.format(instante.atZone(calculadora.getZona()));
    }

    void registrar(Cita cita, EventoCita.Accion accion, String detalle) {
        eventos.save(new EventoCita(cita.getId(), accion, detalle, auditor.getCurrentAuditor().orElse("sistema")));
    }

    Cita buscar(Long id) {
        return citas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cita", id));
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
