package co.edu.ucentral.clinica.enfermeria;

import co.edu.ucentral.clinica.admisiones.Episodio;
import co.edu.ucentral.clinica.admisiones.EpisodioRepository;
import co.edu.ucentral.clinica.admisiones.EstadoEpisodio;
import co.edu.ucentral.clinica.admisiones.TipoAtencion;
import co.edu.ucentral.clinica.agenda.CalculadoraDisponibilidad;
import co.edu.ucentral.clinica.comun.excepciones.DatosInvalidosException;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.EjecucionDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.EntregaDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.EventoAdversoDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.HorarioDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.NotaDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.OrdenClinicaDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.OrdenesPacienteDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.PacienteCensoDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.ResumenTurnoDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaSolicitudes.DobleValidacionRequest;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaSolicitudes.EjecucionRequest;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaSolicitudes.EventoAdversoRequest;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaSolicitudes.OrdenClinicaRequest;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaSolicitudes.VerificacionRequest;
import co.edu.ucentral.clinica.historia.Antecedente;
import co.edu.ucentral.clinica.historia.AntecedenteRepository;
import co.edu.ucentral.clinica.historia.ConsultaRepository;
import co.edu.ucentral.clinica.historia.EvaluadorSignos;
import co.edu.ucentral.clinica.historia.HistoriaService;
import co.edu.ucentral.clinica.historia.Medicamento;
import co.edu.ucentral.clinica.historia.MedicamentoRepository;
import co.edu.ucentral.clinica.historia.ValidadorAlergias;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.ResumenClinicoDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.SignosDto;
import co.edu.ucentral.clinica.laboratorio.LaboratorioService;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.OrdenResumenDto;
import co.edu.ucentral.clinica.notificaciones.NotificacionService;
import co.edu.ucentral.clinica.pacientes.Normalizador;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;
import co.edu.ucentral.clinica.seguridad.Rol;
import co.edu.ucentral.clinica.seguridad.Usuario;
import co.edu.ucentral.clinica.seguridad.UsuarioRepository;
import org.springframework.data.domain.AuditorAware;
import co.edu.ucentral.clinica.facturacion.ServicioPrestado;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enfermería: órdenes médicas consolidadas, administración segura con los cinco correctos,
 * eventos adversos, notas y entrega de turno.
 */
@Service
public class EnfermeriaService {

    /** Tolerancia alrededor de la hora programada: fuera de ella el registro exige justificación. */
    static final Duration TOLERANCIA = Duration.ofMinutes(30);
    private static final Duration VISTA_ATRAS = Duration.ofHours(12);
    private static final Duration VISTA_ADELANTE = Duration.ofHours(12);
    private static final Duration PROXIMAS = Duration.ofHours(2);
    /** Pendientes que se dejan anunciados al turno siguiente. */
    private static final Duration SIGUIENTE_TURNO = Duration.ofHours(6);
    /** Familias de medicamentos de alto riesgo: exigen doble verificación independiente. */
    private static final Set<String> FAMILIAS_ALTO_RIESGO = Set.of("insulinas", "heparinas", "opioides");
    private static final Set<Rol> ROLES_VERIFICADORES = EnumSet.of(Rol.ENFERMERIA, Rol.MEDICO);
    private static final Set<EstadoEpisodio> EPISODIO_ABIERTO =
            EnumSet.of(EstadoEpisodio.EN_ADMISION, EstadoEpisodio.EN_ESPERA_CONSULTA, EstadoEpisodio.EN_ATENCION);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final OrdenClinicaRepository ordenes;
    private final EjecucionOrdenRepository ejecuciones;
    private final EventoAdversoRepository eventos;
    private final NotaEnfermeriaRepository notas;
    private final EntregaTurnoRepository entregas;
    private final MedicamentoRepository medicamentos;
    private final AntecedenteRepository antecedentes;
    private final ConsultaRepository consultas;
    private final EpisodioRepository episodios;
    private final UsuarioRepository usuarios;
    private final PacienteService pacienteService;
    private final HistoriaService historia;
    private final LaboratorioService laboratorio;
    private final ValidadorAlergias validadorAlergias;
    private final NotificacionService notificaciones;
    private final AuthenticationManager authenticationManager;
    private final CalculadoraDisponibilidad calendario;
    private final AuditorAware<String> auditor;
    private final ApplicationEventPublisher publicador;
    private final int maxIntentos;

    public EnfermeriaService(OrdenClinicaRepository ordenes, EjecucionOrdenRepository ejecuciones,
                             EventoAdversoRepository eventos, NotaEnfermeriaRepository notas,
                             EntregaTurnoRepository entregas, MedicamentoRepository medicamentos,
                             AntecedenteRepository antecedentes, ConsultaRepository consultas,
                             EpisodioRepository episodios, UsuarioRepository usuarios, PacienteService pacienteService,
                             HistoriaService historia, LaboratorioService laboratorio,
                             ValidadorAlergias validadorAlergias, NotificacionService notificaciones,
                             AuthenticationManager authenticationManager, CalculadoraDisponibilidad calendario,
                             AuditorAware<String> auditor, ApplicationEventPublisher publicador,
                             @Value("${app.seguridad.max-intentos-fallidos}") int maxIntentos) {
        this.maxIntentos = maxIntentos;
        this.publicador = publicador;
        this.ordenes = ordenes;
        this.ejecuciones = ejecuciones;
        this.eventos = eventos;
        this.notas = notas;
        this.entregas = entregas;
        this.medicamentos = medicamentos;
        this.antecedentes = antecedentes;
        this.consultas = consultas;
        this.episodios = episodios;
        this.usuarios = usuarios;
        this.pacienteService = pacienteService;
        this.historia = historia;
        this.laboratorio = laboratorio;
        this.validadorAlergias = validadorAlergias;
        this.notificaciones = notificaciones;
        this.authenticationManager = authenticationManager;
        this.calendario = calendario;
        this.auditor = auditor;
    }

    // ---------- Órdenes del médico (RFENF-002 / RFENF-005) ----------

    @Transactional
    public OrdenClinicaDto crearOrden(OrdenClinicaRequest r) {
        Paciente paciente = pacienteService.buscarEntidad(r.pacienteId());
        if (r.consultaId() != null) {
            consultas.findById(r.consultaId())
                    .filter(c -> c.getPacienteId().equals(paciente.getId()))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Consulta", r.consultaId()));
        }
        Instant ahora = Instant.now();
        Instant solicitado = r.inicio() != null ? r.inicio() : ahora;
        // Al minuto: las horas programadas se comparan exactas y así sobreviven el viaje a la base de datos
        Instant inicio = solicitado.truncatedTo(ChronoUnit.MINUTES);
        Map<String, String> errores = new LinkedHashMap<>();
        if (solicitado.isBefore(ahora.minus(Duration.ofHours(1)))) {
            errores.put("inicio", "No puede iniciar hace más de una hora");
        }
        Medicamento medicamento = null;
        String descripcion = limpio(r.descripcion());
        if (r.tipo() == OrdenClinica.Tipo.MEDICAMENTO) {
            if (r.medicamentoId() == null) errores.put("medicamentoId", "Seleccione el medicamento");
            if (limpio(r.dosis()) == null) errores.put("dosis", "Indique la dosis");
            if (limpio(r.via()) == null) errores.put("via", "Indique la vía");
        } else if (descripcion == null) {
            errores.put("descripcion", r.tipo() == OrdenClinica.Tipo.DIETA ? "Describa la dieta" : "Describa el cuidado");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        boolean altoRiesgo = false;
        if (r.tipo() == OrdenClinica.Tipo.MEDICAMENTO) {
            medicamento = medicamentos.findById(r.medicamentoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Medicamento", r.medicamentoId()));
            Medicamento m = medicamento;
            validadorAlergias.conflicto(alergias(paciente.getId()), m).ifPresent(a -> {
                throw new ReglaNegocioException("ALERGIA_MEDICAMENTO", "El paciente es alérgico a " + a.getDescripcion()
                        + ": no se puede ordenar " + m.getNombre() + ".");
            });
            altoRiesgo = FAMILIAS_ALTO_RIESGO.contains(Normalizador.texto(m.getFamilia()));
            descripcion = m.getNombre() + " · " + r.dosis().trim() + " · " + r.via().trim() + " · "
                    + (r.frecuenciaHoras() == null ? "dosis única" : "cada " + r.frecuenciaHoras() + " h");
        }
        String medico = usuario();
        String nombre = usuarios.findByUsernameIgnoreCase(medico).map(Usuario::getNombreCompleto).orElse(medico);
        OrdenClinica o = ordenes.save(new OrdenClinica(paciente.getId(), r.consultaId(), r.tipo(), descripcion, medicamento,
                limpio(r.dosis()), limpio(r.via()), r.frecuenciaHoras(), inicio,
                inicio.plus(Duration.ofHours(r.duracionHoras())), altoRiesgo, limpio(r.indicaciones()), medico, nombre));
        notificaciones.paraRol(Rol.ENFERMERIA, (altoRiesgo ? "⚠ Nueva orden de alto riesgo" : "Nueva orden") + " · "
                + paciente.getNombreCompleto(), o.getDescripcion() + " (" + nombre + ")", enlace(paciente.getId()));
        return armar(o, ahora, Map.of());
    }

    /** RFENF-009: una orden suspendida deja de mostrarse como ejecutable y enfermería se entera. */
    @Transactional
    public OrdenClinicaDto suspender(Long id, String motivo) {
        OrdenClinica o = buscar(id);
        o.suspender(motivo.trim(), usuario());
        notificaciones.paraRol(Rol.ENFERMERIA, "Orden suspendida · " + pacienteService.buscarEntidad(o.getPacienteId())
                .getNombreCompleto(), o.getDescripcion() + ". Motivo: " + motivo.trim(), enlace(o.getPacienteId()));
        return armar(o, Instant.now(), ejecucionesPor(List.of(o)));
    }

    @Transactional(readOnly = true)
    public List<OrdenClinicaDto> deConsulta(Long consultaId) {
        List<OrdenClinica> lista = ordenes.findByConsultaIdOrderByInicioDesc(consultaId);
        Instant ahora = Instant.now();
        Map<Long, List<EjecucionOrden>> porOrden = ejecucionesPor(lista);
        return lista.stream().map(o -> armar(o, ahora, porOrden)).toList();
    }

    /** RFENF-002: medicamentos, dietas, cuidados y exámenes del paciente en una sola vista. */
    @Transactional(readOnly = true)
    public OrdenesPacienteDto ordenesPaciente(Long pacienteId) {
        Paciente p = pacienteService.buscarEntidad(pacienteId);
        Instant ahora = Instant.now();
        List<OrdenClinica> lista = ordenes.recientesDePaciente(pacienteId, ahora.minus(Duration.ofHours(24)));
        Map<Long, List<EjecucionOrden>> porOrden = ejecucionesPor(lista);
        List<OrdenClinicaDto> dtos = lista.stream()
                .sorted(Comparator.comparing((OrdenClinica o) -> !o.isVigente(ahora))
                        .thenComparing(o -> !o.isAltoRiesgo())
                        .thenComparing(OrdenClinica::getTipo)
                        .thenComparing(OrdenClinica::getInicio))
                .map(o -> armar(o, ahora, porOrden)).toList();
        return new OrdenesPacienteDto(PacienteResumenDto.de(p),
                alergias(pacienteId).stream().map(EnfermeriaService::alergiaTexto).toList(), dtos,
                examenesRelevantes(pacienteId, ahora));
    }

    // ---------- Administración (RFENF-003 / 004 / 009) ----------

    /**
     * Registra lo que pasó con una dosis o cuidado programado. Una administración de medicamento
     * exige los cinco correctos; uno de alto riesgo además la doble verificación de otro profesional.
     * La hora real la pone el servidor.
     */
    @Transactional(noRollbackFor = ReglaNegocioException.class) // conserva el conteo de intentos fallidos
    public OrdenClinicaDto ejecutar(Long ordenId, EjecucionRequest r) {
        OrdenClinica o = buscar(ordenId);
        Instant ahora = Instant.now();
        String situacion = o.situacion(ahora);
        if (!o.isVigente(ahora)) {
            throw new ReglaNegocioException("ORDEN_NO_VIGENTE", "SUSPENDIDA".equals(situacion)
                    ? "La orden fue suspendida por " + o.getSuspendidaPor() + ": " + o.getMotivoSuspension() + ". No se puede ejecutar."
                    : "La orden venció el " + FECHA_HORA.format(o.getFin().atZone(calendario.getZona())) + ". Solicite al médico renovarla.");
        }
        if (o.getTipo() == OrdenClinica.Tipo.DIETA) {
            throw new ReglaNegocioException("DIETA_SIN_HORARIO", "Las dietas no se registran por horario; use una nota de enfermería.");
        }
        boolean esMedicamento = o.getTipo() == OrdenClinica.Tipo.MEDICAMENTO;
        EjecucionOrden.Resultado res = r.resultado();
        boolean realizada = res == EjecucionOrden.Resultado.ADMINISTRADA || res == EjecucionOrden.Resultado.REALIZADO;
        if (esMedicamento ? res == EjecucionOrden.Resultado.REALIZADO : res == EjecucionOrden.Resultado.ADMINISTRADA) {
            throw new DatosInvalidosException(Map.of("resultado", esMedicamento
                    ? "Un medicamento se registra como administrado" : "Un cuidado se registra como realizado"));
        }
        if (!o.esHoraProgramada(r.programadaPara())) {
            throw new ReglaNegocioException("HORA_NO_PROGRAMADA", "Esa hora no corresponde al horario de la orden.");
        }
        if (ejecuciones.existsByOrdenIdAndProgramadaPara(o.getId(), r.programadaPara())) {
            throw new ReglaNegocioException("YA_REGISTRADA", "La dosis de las "
                    + HORA.format(r.programadaPara().atZone(calendario.getZona())) + " ya fue registrada.");
        }
        if (ahora.isBefore(r.programadaPara().minus(TOLERANCIA))) {
            throw new ReglaNegocioException("AUN_NO_ES_HORA", "Aún no es la hora: la dosis está programada para las "
                    + HORA.format(r.programadaPara().atZone(calendario.getZona())) + ".");
        }
        Map<String, String> errores = new LinkedHashMap<>();
        boolean tarde = ahora.isAfter(r.programadaPara().plus(TOLERANCIA));
        if (realizada && tarde && limpio(r.justificacionRetraso()) == null) {
            errores.put("justificacionRetraso", "Han pasado más de " + TOLERANCIA.toMinutes()
                    + " minutos de la hora programada: justifique el retraso");
        }
        if (!realizada && limpio(r.motivo()) == null) {
            errores.put("motivo", "Registre el motivo");
        }
        if (esMedicamento && realizada && r.verificacion() == null) {
            errores.put("verificacion", "Confirme paciente, medicamento, dosis y vía");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }

        Paciente paciente = pacienteService.buscarEntidad(o.getPacienteId());
        String verificaciones = null;
        String segundo = null;
        if (esMedicamento && realizada) {
            verificarCorrectos(o, paciente, r.verificacion());
            validadorAlergias.conflicto(alergias(paciente.getId()), o.getMedicamento()).ifPresent(a -> {
                throw new ReglaNegocioException("ALERGIA_MEDICAMENTO", "El paciente tiene registrada alergia a "
                        + a.getDescripcion() + ". No administre " + o.getMedicamento().getNombre() + " y avise al médico.");
            });
            verificaciones = "PACIENTE,MEDICAMENTO,DOSIS,VIA,HORA";
            if (o.isAltoRiesgo()) {
                segundo = dobleValidacion(r.dobleValidacion());
            }
        }
        String usuario = usuario();
        EjecucionOrden ejecucion = ejecuciones.save(new EjecucionOrden(o.getId(), r.programadaPara(), res, limpio(r.motivo()),
                realizada && tarde ? limpio(r.justificacionRetraso()) : null, verificaciones, segundo, usuario));
        if (esMedicamento && realizada) {
            // RFRC-002: la dosis administrada genera el cargo del medicamento, con el registro como soporte
            publicador.publishEvent(new ServicioPrestado(paciente.getId(), o.getConsultaId(), null, "MED:" + o.getMedicamento().getId(),
                    1, "ENFERMERIA", "ejecucion-" + ejecucion.getId(), "Orden de " + o.getMedicoNombre() + "; administrada por "
                    + usuario + (segundo == null ? "" : ", verificó " + segundo), enlace(paciente.getId()), ejecucion.getRealizadaEn()));
        }
        if (esMedicamento && !realizada) {
            notificaciones.paraUsuario(o.getMedico(), "Dosis no administrada · " + paciente.getNombreCompleto(),
                    o.getDescripcion() + " de las " + HORA.format(r.programadaPara().atZone(calendario.getZona())) + ": "
                            + res.getDescripcion().toLowerCase() + " (" + r.motivo().trim() + ")", enlace(paciente.getId()));
        }
        ejecuciones.flush();
        return armar(o, ahora, ejecucionesPor(List.of(o)));
    }

    /** RFENF-003: paciente, medicamento, dosis y vía correctos (la hora la controla {@link #ejecutar}). */
    private void verificarCorrectos(OrdenClinica o, Paciente paciente, VerificacionRequest v) {
        List<String> fallas = new ArrayList<>();
        if (!compacto(v.documentoPaciente()).equals(compacto(paciente.getNumeroDocumento()))) {
            fallas.add("el documento no corresponde al paciente de la orden");
        }
        if (!v.medicamentoId().equals(o.getMedicamento().getId())) {
            fallas.add("el medicamento no es el ordenado (" + o.getMedicamento().getNombre() + ")");
        }
        if (!compacto(v.dosis()).equals(compacto(o.getDosis()))) {
            fallas.add("la dosis no coincide con la ordenada (" + o.getDosis() + ")");
        }
        if (!compacto(v.via()).equals(compacto(o.getVia()))) {
            fallas.add("la vía no coincide con la ordenada (" + o.getVia() + ")");
        }
        if (!fallas.isEmpty()) {
            throw new ReglaNegocioException("VERIFICACION_FALLIDA", "No administre: " + String.join("; ", fallas) + ".");
        }
    }

    /**
     * Medicamento de alto riesgo: un segundo profesional (enfermería o médico, distinto de quien
     * administra) confirma con su usuario y contraseña. Los errores son 422, no 401: la sesión de
     * quien administra sigue siendo válida.
     */
    private String dobleValidacion(DobleValidacionRequest d) {
        if (d == null) {
            throw new ReglaNegocioException("DOBLE_VALIDACION_REQUERIDA",
                    "Medicamento de alto riesgo: otro profesional debe verificar la administración.");
        }
        String quien = d.usuario().trim();
        if (quien.equalsIgnoreCase(usuario())) {
            throw new ReglaNegocioException("DOBLE_VALIDACION_INVALIDA", "La verificación debe hacerla otro profesional.");
        }
        ReglaNegocioException invalida = new ReglaNegocioException("DOBLE_VALIDACION_INVALIDA",
                "Credenciales de quien verifica incorrectas, o no es un profesional de enfermería o médico.");
        Usuario u = usuarios.findByUsernameIgnoreCase(quien)
                .filter(x -> ROLES_VERIFICADORES.contains(x.getRol()))
                .orElseThrow(() -> invalida);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(u.getUsername(), d.password()));
        } catch (BadCredentialsException e) {
            // Cuenta como intento fallido de esa cuenta: esta pantalla no puede usarse para adivinar contraseñas
            u.registrarIntentoFallido(maxIntentos);
            throw invalida;
        } catch (AuthenticationException e) {
            throw invalida;
        }
        return u.getUsername();
    }

    // ---------- Censo ----------

    /** Pacientes a cargo de enfermería: con órdenes vigentes o en urgencias / hospitalización. */
    @Transactional(readOnly = true)
    public List<PacienteCensoDto> censo() {
        Instant ahora = Instant.now();
        Map<Long, String> ubicacion = new LinkedHashMap<>();
        for (Episodio e : episodios.buscarPorEstados(EPISODIO_ABIERTO)) {
            if (e.getTipoAtencion() != TipoAtencion.CONSULTA_EXTERNA) {
                ubicacion.putIfAbsent(e.getPaciente().getId(), e.getTipoAtencion().getDescripcion() + " · " + e.getNumero());
            }
        }
        Set<Long> ids = new LinkedHashSet<>(ubicacion.keySet());
        ids.addAll(ordenes.pacientesConOrdenesVigentes(ahora));

        List<PacienteCensoDto> filas = new ArrayList<>();
        for (Long id : ids) {
            Paciente p = pacienteService.buscarEntidad(id);
            List<OrdenClinica> vigentes = ordenes.recientesDePaciente(id, ahora).stream().filter(o -> o.isVigente(ahora)).toList();
            Map<Long, List<EjecucionOrden>> porOrden = ejecucionesPor(vigentes);
            int atrasadas = 0;
            int proximas = 0;
            boolean altoRiesgoAtrasado = false;
            for (OrdenClinica o : vigentes) {
                Set<Instant> hechas = porOrden.getOrDefault(o.getId(), List.of()).stream()
                        .map(EjecucionOrden::getProgramadaPara).collect(Collectors.toSet());
                for (Instant t : o.horarios(ahora.minus(VISTA_ATRAS), ahora.plus(PROXIMAS))) {
                    if (hechas.contains(t)) continue;
                    if (t.plus(TOLERANCIA).isBefore(ahora)) {
                        atrasadas++;
                        altoRiesgoAtrasado |= o.isAltoRiesgo();
                    } else {
                        proximas++;
                    }
                }
            }
            List<String> alertas = new ArrayList<>();
            SignosDto signos = historia.ultimosSignos(id, historia.triageActual(id));
            boolean critico = false;
            boolean anormal = false;
            if (signos != null) {
                for (EvaluadorSignos.Alerta a : signos.alertas()) {
                    if (a.nivel() == EvaluadorSignos.Nivel.CRITICO) {
                        critico = true;
                        alertas.add(a.mensaje());
                    } else {
                        anormal = true;
                    }
                }
            }
            if (atrasadas > 0) {
                alertas.add(atrasadas + (atrasadas == 1 ? " dosis o cuidado atrasado" : " dosis o cuidados atrasados")
                        + (altoRiesgoAtrasado ? " (incluye alto riesgo)" : ""));
            }
            eventos.findByPacienteIdOrderByOcurridoEnDesc(id).stream()
                    .filter(e -> e.getOcurridoEn().isAfter(ahora.minus(Duration.ofHours(24))))
                    .forEach(e -> alertas.add("Evento adverso: " + e.getTipo().getDescripcion() + " ("
                            + e.getSeveridad().getDescripcion().toLowerCase() + ")"));
            List<Antecedente> alergias = alergias(id);
            String prioridad = critico || altoRiesgoAtrasado ? "CRITICO" : atrasadas > 0 || anormal ? "ALERTA" : "NORMAL";
            filas.add(new PacienteCensoDto(PacienteResumenDto.de(p), ubicacion.getOrDefault(id, "Con órdenes activas"),
                    atrasadas, proximas, alertas, alergias.size(), prioridad));
        }
        filas.sort(Comparator.comparing((PacienteCensoDto f) -> switch (f.prioridad()) {
                    case "CRITICO" -> 0;
                    case "ALERTA" -> 1;
                    default -> 2;
                })
                .thenComparing(PacienteCensoDto::dosisAtrasadas, Comparator.reverseOrder())
                .thenComparing(f -> f.paciente().nombreCompleto()));
        return filas;
    }

    // ---------- Eventos adversos (RFENF-007) ----------

    @Transactional
    public EventoAdversoDto reportarEvento(Long pacienteId, EventoAdversoRequest r) {
        Paciente p = pacienteService.buscarEntidad(pacienteId);
        if (r.ordenId() != null && !buscar(r.ordenId()).getPacienteId().equals(pacienteId)) {
            throw new DatosInvalidosException(Map.of("ordenId", "La orden no es de este paciente"));
        }
        EventoAdverso e = eventos.save(new EventoAdverso(pacienteId, r.tipo(), r.severidad(), r.ocurridoEn(),
                limpio(r.lugar()), r.descripcion().trim(), r.accionesInmediatas().trim(), limpio(r.medicoNotificado()),
                r.ordenId()));
        String titulo = (r.severidad().isGrave() ? "⚠ Evento adverso grave · " : "Evento adverso · ") + p.getNombreCompleto();
        String detalle = r.tipo().getDescripcion() + " (" + r.severidad().getDescripcion().toLowerCase() + "): "
                + resumir(r.descripcion().trim());
        // Seguridad del paciente revisa todos los reportes; el médico se entera de inmediato si es grave
        notificaciones.paraRol(Rol.AUDITORIA, titulo, detalle, enlace(pacienteId));
        if (r.severidad().isGrave()) {
            notificaciones.paraRol(Rol.MEDICO, titulo, detalle, enlace(pacienteId));
        }
        return EventoAdversoDto.de(e);
    }

    @Transactional(readOnly = true)
    public List<EventoAdversoDto> eventosPaciente(Long pacienteId) {
        pacienteService.buscarEntidad(pacienteId);
        return eventos.findByPacienteIdOrderByOcurridoEnDesc(pacienteId).stream().map(EventoAdversoDto::de).toList();
    }

    @Transactional(readOnly = true)
    public List<EventoAdversoDto> eventosRecientes(int dias) {
        return eventos.findByOcurridoEnAfterOrderByOcurridoEnDesc(Instant.now().minus(Duration.ofDays(dias))).stream()
                .map(EventoAdversoDto::de).toList();
    }

    // ---------- Notas ----------

    @Transactional
    public NotaDto agregarNota(Long pacienteId, String texto) {
        pacienteService.buscarEntidad(pacienteId);
        return NotaDto.de(notas.save(new NotaEnfermeria(pacienteId, texto.trim(), usuario())));
    }

    @Transactional(readOnly = true)
    public List<NotaDto> notas(Long pacienteId) {
        pacienteService.buscarEntidad(pacienteId);
        return notas.findTop30ByPacienteIdOrderByFechaDesc(pacienteId).stream().map(NotaDto::de).toList();
    }

    // ---------- Entrega de turno (RFENF-008) ----------

    /** Estado del paciente en el turno actual, armado a partir de lo registrado: nada se digita de nuevo. */
    @Transactional(readOnly = true)
    public ResumenTurnoDto resumenTurno(Long pacienteId) {
        Instant ahora = Instant.now();
        ZonedDateTime local = ahora.atZone(calendario.getZona());
        EntregaTurno.Turno turno = EntregaTurno.Turno.de(local.toLocalTime());
        LocalDate dia = turno == EntregaTurno.Turno.NOCHE && local.toLocalTime().isBefore(LocalTime.of(7, 0))
                ? local.toLocalDate().minusDays(1) : local.toLocalDate();
        Instant desde = dia.atTime(turno.getInicio()).atZone(calendario.getZona()).toInstant();

        ResumenClinicoDto clinico = historia.resumen(pacienteId);
        List<String> alergiasTxt = clinico.alergias().stream()
                .map(a -> a.descripcion() + (a.severidad() == null ? "" : " (" + a.severidad().toLowerCase() + ")")).toList();
        List<String> dx = clinico.diagnosticosActivos().stream()
                .map(d -> (d.codigo() == null ? "" : d.codigo() + " ") + d.nombre()).distinct().toList();

        List<OrdenClinica> lista = ordenes.recientesDePaciente(pacienteId, desde);
        Map<Long, OrdenClinica> porId = lista.stream().collect(Collectors.toMap(OrdenClinica::getId, Function.identity()));
        Map<Long, List<EjecucionOrden>> porOrden = ejecucionesPor(lista);

        List<String> administrado = porOrden.values().stream().flatMap(List::stream)
                .filter(e -> e.getRealizadaEn().isAfter(desde))
                .sorted(Comparator.comparing(EjecucionOrden::getRealizadaEn))
                .map(e -> HORA.format(e.getProgramadaPara().atZone(calendario.getZona())) + " "
                        + porId.get(e.getOrdenId()).getDescripcion() + " — " + e.getResultado().getDescripcion().toLowerCase()
                        + " a las " + HORA.format(e.getRealizadaEn().atZone(calendario.getZona())) + " por " + e.getUsuario()
                        + (e.getMotivo() == null ? "" : " (" + e.getMotivo() + ")")
                        + (e.getJustificacionRetraso() == null ? "" : " (retraso: " + e.getJustificacionRetraso() + ")")
                        + (e.getDobleValidacionPor() == null ? "" : ", verificó " + e.getDobleValidacionPor()))
                .toList();

        List<String> pendientes = new ArrayList<>();
        for (OrdenClinica o : lista) {
            if (!o.isVigente(ahora)) continue;
            Set<Instant> hechas = porOrden.getOrDefault(o.getId(), List.of()).stream()
                    .map(EjecucionOrden::getProgramadaPara).collect(Collectors.toSet());
            for (Instant t : o.horarios(desde, ahora.plus(SIGUIENTE_TURNO))) {
                if (hechas.contains(t)) continue;
                boolean atrasada = t.plus(TOLERANCIA).isBefore(ahora);
                pendientes.add((atrasada ? "ATRASADA " : "") + HORA.format(t.atZone(calendario.getZona())) + " "
                        + o.getDescripcion() + (o.isAltoRiesgo() ? " [alto riesgo]" : ""));
            }
        }

        List<String> activas = lista.stream().filter(o -> o.isVigente(ahora))
                .map(o -> o.getTipo().getDescripcion() + ": " + o.getDescripcion()
                        + (o.getIndicaciones() == null ? "" : " — " + o.getIndicaciones()))
                .toList();
        List<String> suspendidas = lista.stream().filter(o -> o.getSuspendidaEn() != null && o.getSuspendidaEn().isAfter(desde))
                .map(o -> "Suspendida: " + o.getDescripcion() + " (" + o.getMotivoSuspension() + ")").toList();
        List<String> ordenesTxt = new ArrayList<>(activas);
        ordenesTxt.addAll(suspendidas);

        List<String> examenes = examenesRelevantes(pacienteId, ahora).stream()
                .map(x -> x.numero() + " " + String.join(", ", x.examenes()) + " — " + x.estadoDescripcion().toLowerCase()
                        + (x.tieneCriticos() ? " (con valores críticos)" : ""))
                .toList();
        List<String> eventosTxt = eventos.findByPacienteIdOrderByOcurridoEnDesc(pacienteId).stream()
                .filter(e -> e.getOcurridoEn().isAfter(desde))
                .map(e -> FECHA_HORA.format(e.getOcurridoEn().atZone(calendario.getZona())) + " " + e.getTipo().getDescripcion()
                        + " (" + e.getSeveridad().getDescripcion().toLowerCase() + "): " + resumir(e.getDescripcion()))
                .toList();
        List<String> notasTxt = notas.findByPacienteIdAndFechaAfterOrderByFecha(pacienteId, desde).stream()
                .map(n -> HORA.format(n.getFecha().atZone(calendario.getZona())) + " " + n.getUsuario() + ": " + n.getTexto())
                .toList();

        String texto = texto(clinico.paciente(), turno, alergiasTxt, dx, clinico.ultimosSignos(), clinico.alertas(),
                administrado, pendientes, ordenesTxt, examenes, eventosTxt, notasTxt);
        return new ResumenTurnoDto(clinico.paciente(), turno.getDescripcion(), desde, ahora, alergiasTxt, dx,
                clinico.ultimosSignos(), clinico.alertas(), administrado, pendientes, ordenesTxt, examenes, eventosTxt,
                notasTxt, texto);
    }

    /** Guarda el resumen tal como está ahora: lo que recibe el turno siguiente no cambia después. */
    @Transactional
    public EntregaDto entregarTurno(Long pacienteId, String nota) {
        ResumenTurnoDto r = resumenTurno(pacienteId);
        EntregaTurno.Turno turno = EntregaTurno.Turno.de(r.hasta().atZone(calendario.getZona()).toLocalTime());
        return EntregaDto.de(entregas.save(new EntregaTurno(pacienteId, turno, r.texto(), limpio(nota), usuario())));
    }

    @Transactional(readOnly = true)
    public List<EntregaDto> entregas(Long pacienteId) {
        pacienteService.buscarEntidad(pacienteId);
        return entregas.findTop10ByPacienteIdOrderByFechaDesc(pacienteId).stream().map(EntregaDto::de).toList();
    }

    private String texto(PacienteResumenDto p, EntregaTurno.Turno turno, List<String> alergias, List<String> dx,
                         SignosDto signos, List<String> alertas, List<String> administrado, List<String> pendientes,
                         List<String> ordenesTxt, List<String> examenes, List<String> eventosTxt, List<String> notasTxt) {
        StringBuilder sb = new StringBuilder();
        sb.append("Entrega de turno ").append(turno.getDescripcion().toLowerCase()).append(" · ").append(p.nombreCompleto())
                .append(" · ").append(p.tipoDocumento()).append(' ').append(p.numeroDocumento()).append('\n');
        seccion(sb, "Alergias", alergias.isEmpty() ? List.of("Sin alergias registradas") : alergias);
        seccion(sb, "Diagnósticos", dx);
        if (signos != null) {
            seccion(sb, "Últimos signos (" + FECHA_HORA.format(signos.fecha().atZone(calendario.getZona())) + ")",
                    List.of(signosTexto(signos)));
        }
        seccion(sb, "Alertas", alertas);
        seccion(sb, "Registrado en el turno", administrado);
        seccion(sb, "Pendiente", pendientes);
        seccion(sb, "Órdenes", ordenesTxt);
        seccion(sb, "Laboratorio", examenes);
        seccion(sb, "Eventos adversos", eventosTxt);
        seccion(sb, "Notas", notasTxt);
        return sb.toString().trim();
    }

    private static void seccion(StringBuilder sb, String titulo, List<String> lineas) {
        if (lineas.isEmpty()) return;
        sb.append('\n').append(titulo).append(":\n");
        lineas.forEach(l -> sb.append("- ").append(l).append('\n'));
    }

    private static String signosTexto(SignosDto s) {
        List<String> partes = new ArrayList<>();
        if (s.presionSistolica() != null && s.presionDiastolica() != null) {
            partes.add("PA " + s.presionSistolica() + "/" + s.presionDiastolica());
        }
        if (s.frecuenciaCardiaca() != null) partes.add("FC " + s.frecuenciaCardiaca());
        if (s.frecuenciaRespiratoria() != null) partes.add("FR " + s.frecuenciaRespiratoria());
        if (s.temperatura() != null) partes.add("T " + s.temperatura().stripTrailingZeros().toPlainString().replace('.', ',') + " °C");
        if (s.saturacionOxigeno() != null) partes.add("SpO₂ " + s.saturacionOxigeno() + " %");
        return String.join(" · ", partes);
    }

    // ---------- armado ----------

    private OrdenClinicaDto armar(OrdenClinica o, Instant ahora, Map<Long, List<EjecucionOrden>> porOrden) {
        List<EjecucionOrden> hechas = porOrden.getOrDefault(o.getId(), List.of());
        Map<Instant, EjecucionOrden> porHora = hechas.stream()
                .collect(Collectors.toMap(EjecucionOrden::getProgramadaPara, Function.identity(), (a, b) -> a));
        Set<Instant> horas = new TreeSet<>(o.horarios(ahora.minus(VISTA_ATRAS), ahora.plus(VISTA_ADELANTE)));
        // Lo registrado fuera de la ventana visible también se muestra (p. ej. la dosis única de ayer)
        horas.addAll(porHora.keySet());
        boolean vigente = o.isVigente(ahora);
        List<HorarioDto> horarios = horas.stream().map(t -> {
            EjecucionOrden e = porHora.get(t);
            String estado;
            if (e != null) estado = e.getResultado().name();
            else if (!vigente) estado = "NO_APLICA";
            else if (t.plus(TOLERANCIA).isBefore(ahora)) estado = "ATRASADA";
            else if (!ahora.isBefore(t.minus(TOLERANCIA))) estado = "PENDIENTE";
            else estado = "PROGRAMADA";
            return new HorarioDto(t, estado, e == null ? null : EjecucionDto.de(e));
        }).toList();
        Medicamento m = o.getMedicamento();
        return new OrdenClinicaDto(o.getId(), o.getTipo().name(), o.getTipo().getDescripcion(), o.getDescripcion(),
                m == null ? null : m.getId(), m == null ? null : m.getNombre(), o.getDosis(), o.getVia(),
                o.getFrecuenciaHoras(), o.getInicio(), o.getFin(), o.isAltoRiesgo(), o.getIndicaciones(),
                o.situacion(ahora), o.getMotivoSuspension(), o.getSuspendidaPor(), o.getSuspendidaEn(), o.getMedicoNombre(),
                horarios);
    }

    private Map<Long, List<EjecucionOrden>> ejecucionesPor(List<OrdenClinica> lista) {
        if (lista.isEmpty()) return Map.of();
        return ejecuciones.findByOrdenIdInOrderByRealizadaEnDesc(lista.stream().map(OrdenClinica::getId).toList()).stream()
                .collect(Collectors.groupingBy(EjecucionOrden::getOrdenId));
    }

    /** Exámenes en curso y los validados en las últimas 24 h. */
    private List<OrdenResumenDto> examenesRelevantes(Long pacienteId, Instant ahora) {
        return laboratorio.dePaciente(pacienteId).stream()
                .filter(x -> !"VALIDADA".equals(x.estado()) || x.validadaEn().isAfter(ahora.minus(Duration.ofHours(24))))
                .limit(10).toList();
    }

    private List<Antecedente> alergias(Long pacienteId) {
        return antecedentes.findByPacienteIdAndTipoAndActivoTrue(pacienteId, Antecedente.Tipo.ALERGIA);
    }

    private static String alergiaTexto(Antecedente a) {
        return a.getDescripcion() + (a.getSeveridad() == null ? "" : " (" + a.getSeveridad().name().toLowerCase() + ")");
    }

    private OrdenClinica buscar(Long id) {
        return ordenes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Orden", id));
    }

    private static String enlace(Long pacienteId) {
        return "/enfermeria/pacientes/" + pacienteId;
    }

    /** Para comparar lo digitado con lo ordenado: sin tildes, mayúsculas, espacios ni puntos. */
    private static String compacto(String s) {
        return s == null ? "" : Normalizador.texto(s).replaceAll("[\\s.]", "");
    }

    private static String resumir(String s) {
        return s.length() <= 160 ? s : s.substring(0, 157) + "…";
    }

    private String usuario() {
        return auditor.getCurrentAuditor().orElse("sistema");
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
