package co.edu.ucentral.clinica.laboratorio;

import co.edu.ucentral.clinica.agenda.CalculadoraDisponibilidad;
import co.edu.ucentral.clinica.comun.excepciones.DatosInvalidosException;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.historia.Antecedente;
import co.edu.ucentral.clinica.historia.AntecedenteRepository;
import co.edu.ucentral.clinica.historia.Consulta;
import co.edu.ucentral.clinica.historia.ConsultaRepository;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.ContextoClinicoDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.ControlCalidadDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.ControlDiaDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.EventoDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.ExamenDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.OrdenDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.OrdenExamenDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.OrdenResumenDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.RangoDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.ReprocesoDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.ResultadoFilaDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.ResultadoRecienteDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.ControlCalidadRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.MuestraRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.OrdenRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.RangoRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.RechazoRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.RepeticionRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.ResultadosRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.TrazabilidadRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.ValidacionRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.ValorRequest;
import co.edu.ucentral.clinica.notificaciones.NotificacionService;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;
import co.edu.ucentral.clinica.seguridad.Rol;
import co.edu.ucentral.clinica.seguridad.Usuario;
import co.edu.ucentral.clinica.seguridad.UsuarioRepository;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Flujo del laboratorio clínico: orden → muestra → resultados → validación → médico. */
@Service
public class LaboratorioService {

    private static final int MAX_RESULTADOS_RESUMEN = 12;
    /** Coherencia interna: el hematocrito suele ser unas tres veces la hemoglobina. */
    private static final BigDecimal MARGEN_HB_HTO = BigDecimal.valueOf(6);

    private final ExamenLabRepository examenes;
    private final RangoReferenciaRepository rangos;
    private final OrdenLabRepository ordenes;
    private final ResultadoLabRepository resultados;
    private final ReprocesoLabRepository reprocesos;
    private final ControlCalidadRepository controles;
    private final EventoOrdenLabRepository eventos;
    private final ConsultaRepository consultas;
    private final AntecedenteRepository antecedentes;
    private final UsuarioRepository usuarios;
    private final PacienteService pacienteService;
    private final InterpretadorResultados interpretador;
    private final NotificacionService notificaciones;
    private final CalculadoraDisponibilidad calendario;
    private final AuditorAware<String> auditor;

    public LaboratorioService(ExamenLabRepository examenes, RangoReferenciaRepository rangos,
                              OrdenLabRepository ordenes, ResultadoLabRepository resultados,
                              ReprocesoLabRepository reprocesos, ControlCalidadRepository controles,
                              EventoOrdenLabRepository eventos, ConsultaRepository consultas,
                              AntecedenteRepository antecedentes, UsuarioRepository usuarios,
                              PacienteService pacienteService, InterpretadorResultados interpretador,
                              NotificacionService notificaciones, CalculadoraDisponibilidad calendario,
                              AuditorAware<String> auditor) {
        this.examenes = examenes;
        this.rangos = rangos;
        this.ordenes = ordenes;
        this.resultados = resultados;
        this.reprocesos = reprocesos;
        this.controles = controles;
        this.eventos = eventos;
        this.consultas = consultas;
        this.antecedentes = antecedentes;
        this.usuarios = usuarios;
        this.pacienteService = pacienteService;
        this.interpretador = interpretador;
        this.notificaciones = notificaciones;
        this.calendario = calendario;
        this.auditor = auditor;
    }

    // ---------- Catálogo y parametrización (RFLC-014) ----------

    @Transactional(readOnly = true)
    public List<ExamenDto> catalogo() {
        return examenes.findAllByOrderByNombre().stream().map(ExamenDto::de).toList();
    }

    @Transactional
    public RangoDto actualizarRango(Long id, RangoRequest r) {
        RangoReferencia rango = rangos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Rango", id));
        Map<String, String> errores = new HashMap<>();
        if (menor(r.refMax(), r.refMin())) errores.put("refMax", "Debe ser mayor que el mínimo de referencia");
        if (menor(r.criticoMax(), r.criticoMin())) errores.put("criticoMax", "Debe ser mayor que el crítico mínimo");
        if (menor(r.refMin(), r.criticoMin())) errores.put("criticoMin", "El crítico bajo debe quedar por debajo del rango normal");
        if (menor(r.criticoMax(), r.refMax())) errores.put("criticoMax", "El crítico alto debe quedar por encima del rango normal");
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        rango.actualizar(r.refMin(), r.refMax(), r.criticoMin(), r.criticoMax(), usuario());
        return RangoDto.de(rango);
    }

    /** true si ambos existen y a &lt; b. */
    private static boolean menor(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) < 0;
    }

    // ---------- Órdenes (RFMT-006 / RFLC-015) ----------

    /** El médico ordena desde la consulta: la orden llega al laboratorio sin volver a digitarla. */
    @Transactional
    public OrdenDto crearOrden(OrdenRequest r) {
        Paciente paciente = pacienteService.buscarEntidad(r.pacienteId());
        if (r.consultaId() != null) {
            consultas.findById(r.consultaId())
                    .filter(c -> c.getPacienteId().equals(paciente.getId()))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Consulta", r.consultaId()));
        }
        List<ExamenLab> elegidos = new LinkedHashSet<>(r.examenIds()).stream()
                .map(id -> examenes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Examen", id)))
                .toList();
        String medico = usuario();
        String nombre = usuarios.findByUsernameIgnoreCase(medico).map(Usuario::getNombreCompleto).orElse(medico);
        String numero = "ORD-%d-%06d".formatted(Year.now().getValue(), ordenes.siguienteNumero());
        OrdenLab orden = ordenes.save(new OrdenLab(numero, paciente.getId(), r.consultaId(), medico, nombre, r.prioridad(),
                limpio(r.indicaciones()), elegidos));
        evento(orden, "ORDENADA", elegidos.stream().map(ExamenLab::getNombre).collect(Collectors.joining(", ")));
        if (r.prioridad() == PrioridadLab.URGENTE) {
            notificaciones.paraRol(Rol.LABORATORIO, "Orden urgente " + numero,
                    paciente.getNombreCompleto() + ": " + elegidos.stream().map(ExamenLab::getNombre)
                            .collect(Collectors.joining(", ")), "/laboratorio/ordenes/" + orden.getId());
        }
        return armar(orden);
    }

    /** RFLC-002 / RFLC-005: la cola, con urgentes primero y luego por antigüedad. */
    @Transactional(readOnly = true)
    public List<OrdenResumenDto> cola(EstadoOrden estado, PrioridadLab prioridad) {
        List<OrdenLab> lista = estado == EstadoOrden.VALIDADA
                ? ordenes.findByEstadoAndValidadaEnAfterOrderByValidadaEnDesc(EstadoOrden.VALIDADA,
                        Instant.now().minus(Duration.ofDays(2)))
                : ordenes.findByEstadoInOrderByOrdenadaEn(estado != null ? EnumSet.of(estado)
                        : EnumSet.of(EstadoOrden.ORDENADA, EstadoOrden.MUESTRA_RECIBIDA, EstadoOrden.EN_PROCESO,
                        EstadoOrden.RECHAZADA));
        Instant ahora = Instant.now();
        return lista.stream()
                .filter(o -> prioridad == null || o.getPrioridad() == prioridad)
                .sorted(estado == EstadoOrden.VALIDADA ? Comparator.comparing(OrdenLab::getValidadaEn).reversed()
                        : Comparator.comparing(OrdenLab::getPrioridad).thenComparing(OrdenLab::getOrdenadaEn))
                .map(o -> resumen(o, ahora))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrdenResumenDto> dePaciente(Long pacienteId) {
        pacienteService.buscarEntidad(pacienteId);
        Instant ahora = Instant.now();
        return ordenes.findByPacienteIdOrderByOrdenadaEnDesc(pacienteId).stream().map(o -> resumen(o, ahora)).toList();
    }

    @Transactional(readOnly = true)
    public OrdenDto obtener(Long id) {
        return armar(buscar(id));
    }

    // ---------- Pre-analítica ----------

    @Transactional
    public OrdenDto recibirMuestra(Long id, MuestraRequest r) {
        OrdenLab o = buscar(id);
        Instant tomada = r.tomadaEn() != null ? r.tomadaEn() : Instant.now();
        if (tomada.isAfter(Instant.now().plus(Duration.ofMinutes(5))) || tomada.isBefore(o.getOrdenadaEn().minus(Duration.ofHours(1)))) {
            throw new DatosInvalidosException(Map.of("tomadaEn", "La hora de toma no es coherente con la orden"));
        }
        o.recibirMuestra(tomada, r.tomadaPor().trim());
        evento(o, "MUESTRA_RECIBIDA", "Tomada por " + r.tomadaPor().trim());
        return armar(o);
    }

    /** RFLC-011: el rechazo queda clasificado y el médico se entera de que habrá demora. */
    @Transactional
    public OrdenDto rechazarMuestra(Long id, RechazoRequest r) {
        OrdenLab o = buscar(id);
        o.rechazarMuestra(r.motivo(), limpio(r.detalle()));
        evento(o, "MUESTRA_RECHAZADA", r.motivo().getDescripcion() + (limpio(r.detalle()) == null ? "" : ": " + r.detalle().trim()));
        notificaciones.paraUsuario(o.getMedico(), "Muestra rechazada " + o.getNumero(),
                pacienteService.buscarEntidad(o.getPacienteId()).getNombreCompleto() + ": " + r.motivo().getDescripcion()
                        + ". Se requiere una nueva muestra.", "/laboratorio/ordenes/" + o.getId());
        return armar(o);
    }

    @Transactional
    public OrdenDto solicitarNuevaMuestra(Long id) {
        OrdenLab o = buscar(id);
        o.solicitarNuevaMuestra();
        evento(o, "NUEVA_MUESTRA", "Orden de nuevo pendiente de toma de muestra");
        return armar(o);
    }

    // ---------- Analítica ----------

    /**
     * Registra trazabilidad y valores. Un valor ya registrado no se sobreescribe en silencio:
     * cambiarlo es una repetición con motivo (RFLC-009).
     */
    @Transactional
    public OrdenDto registrarResultados(Long id, ResultadosRequest r) {
        OrdenLab o = buscar(id);
        o.marcarEnProceso();
        Map<Long, OrdenExamen> porId = o.getExamenes().stream().collect(Collectors.toMap(OrdenExamen::getId, Function.identity()));
        for (TrazabilidadRequest t : r.trazabilidad()) {
            OrdenExamen oe = Optional.ofNullable(porId.get(t.ordenExamenId()))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Examen de la orden", t.ordenExamenId()));
            oe.registrarTrazabilidad(limpio(t.equipo()), limpio(t.loteReactivo()), limpio(t.calibracion()));
        }
        Map<Long, Analito> analitos = analitosDe(o);
        Map<Long, ResultadoLab> actuales = o.getResultados().stream()
                .collect(Collectors.toMap(x -> x.getAnalito().getId(), Function.identity()));
        Paciente paciente = pacienteService.buscarEntidad(o.getPacienteId());
        for (ValorRequest v : r.valores()) {
            Analito a = Optional.ofNullable(analitos.get(v.analitoId()))
                    .orElseThrow(() -> new ReglaNegocioException("ANALITO_AJENO", "El analito no pertenece a esta orden."));
            ResultadoLab existente = actuales.get(a.getId());
            if (existente != null) {
                if (existente.getValor().compareTo(v.valor()) != 0) {
                    throw new ReglaNegocioException("USAR_REPETICION", "Para cambiar el resultado de " + a.getNombre()
                            + " use «Repetir» y registre el motivo.");
                }
                continue;
            }
            ResultadoLab nuevo = new ResultadoLab(o, a);
            aplicarValor(nuevo, a, v.valor(), paciente, o);
            o.getResultados().add(nuevo);
        }
        evento(o, "RESULTADOS", r.valores().size() + " valor(es) registrados");
        ordenes.flush();
        return armar(o);
    }

    /** RFLC-009: repite la prueba conservando el valor anterior y el motivo. */
    @Transactional
    public OrdenDto repetir(Long id, Long analitoId, RepeticionRequest r) {
        OrdenLab o = buscar(id);
        if (o.getEstado() != EstadoOrden.EN_PROCESO) {
            throw new ReglaNegocioException("ORDEN_ESTADO_INVALIDO", "Solo se repiten pruebas de órdenes en proceso.");
        }
        ResultadoLab res = o.getResultados().stream().filter(x -> x.getAnalito().getId().equals(analitoId)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Resultado", analitoId));
        BigDecimal anterior = res.getValor();
        aplicarValor(res, res.getAnalito(), r.valor(), pacienteService.buscarEntidad(o.getPacienteId()), o);
        res.contarRepeticion();
        reprocesos.save(new ReprocesoLab(res.getId(), anterior, r.valor(), r.motivo(), limpio(r.detalle()), usuario()));
        evento(o, "REPETICION", res.getAnalito().getNombre() + ": " + anterior.stripTrailingZeros().toPlainString() + " → "
                + r.valor().stripTrailingZeros().toPlainString() + " (" + r.motivo().getDescripcion() + ")");
        return armar(o);
    }

    private void aplicarValor(ResultadoLab res, Analito a, BigDecimal valor, Paciente p, OrdenLab o) {
        Optional<RangoReferencia> rango = rangoPara(a, p);
        Interpretacion interp = interpretador.interpretar(valor, rango.orElse(null));
        Optional<ResultadoLab> previo = resultados.previos(a.getId(), p.getId(), o.getId(), PageRequest.of(0, 1)).stream().findFirst();
        BigDecimal valorPrevio = previo.map(ResultadoLab::getValor).orElse(null);
        BigDecimal delta = interpretador.delta(valor, valorPrevio);
        res.registrar(valor, interp, rango.orElse(null), valorPrevio, previo.map(x -> x.getOrden().getValidadaEn()).orElse(null),
                delta, interpretador.superaDelta(delta, a.getDeltaPorcentaje()), usuario());
    }

    private Optional<RangoReferencia> rangoPara(Analito a, Paciente p) {
        if (p.getSexo() == null || p.getEdad() == null) {
            return Optional.empty();
        }
        return interpretador.rango(a.getRangos(), p.getSexo(), p.getEdad());
    }

    // ---------- Post-analítica: validación ----------

    /**
     * Libera los resultados solo si se cumple todo lo que el laboratorio exige; si hay valores
     * críticos se registra a quién se informó y el médico recibe el aviso de inmediato (RFMT-004).
     */
    @Transactional
    public OrdenDto validar(Long id, ValidacionRequest r) {
        OrdenLab o = buscar(id);
        List<String> pendientes = pendientes(o, limpio(r.contactoCritico()));
        if (!pendientes.isEmpty()) {
            throw new ReglaNegocioException("VALIDACION_BLOQUEADA", "No se puede validar: " + String.join("; ", pendientes) + ".");
        }
        List<ResultadoLab> criticos = o.getResultados().stream().filter(x -> x.getInterpretacion().isCritico()).toList();
        String contacto = criticos.isEmpty() ? null : limpio(r.contactoCritico());
        o.validar(usuario(), contacto, contacto == null ? null : Objects.requireNonNullElse(limpio(r.medioCritico()), "Llamada"));
        evento(o, "VALIDADA", criticos.isEmpty() ? null : "Valor crítico informado a " + contacto);

        Paciente p = pacienteService.buscarEntidad(o.getPacienteId());
        String enlace = "/laboratorio/ordenes/" + o.getId();
        if (!criticos.isEmpty()) {
            notificaciones.paraUsuario(o.getMedico(), "⚠ Resultado crítico · " + p.getNombreCompleto(),
                    criticos.stream().map(x -> x.getAnalito().getNombre() + " " + x.getValor().stripTrailingZeros().toPlainString()
                            + " " + x.getAnalito().getUnidad() + " (" + x.getInterpretacion().getDescripcion().toLowerCase() + ")")
                            .collect(Collectors.joining("; ")), enlace);
        } else {
            notificaciones.paraUsuario(o.getMedico(), "Resultados disponibles " + o.getNumero(),
                    p.getNombreCompleto() + ": " + o.getExamenes().stream().map(e -> e.getExamen().getNombre())
                            .collect(Collectors.joining(", ")), enlace);
        }
        return armar(o);
    }

    /** Lo que impide validar, en el lenguaje del laboratorio. */
    List<String> pendientes(OrdenLab o, String contactoCritico) {
        List<String> faltan = new ArrayList<>();
        if (o.getEstado() != EstadoOrden.EN_PROCESO) {
            faltan.add("la orden debe estar en proceso");
            return faltan;
        }
        Paciente p = pacienteService.buscarEntidad(o.getPacienteId());
        // RFLC-010: datos obligatorios del paciente y de la muestra
        if (p.getSexo() == null || p.getFechaNacimiento() == null) {
            faltan.add("faltan el sexo o la fecha de nacimiento del paciente (se necesitan para el rango de referencia)");
        }
        if (o.getMuestraTomadaEn() == null || o.getMuestraTomadaPor() == null) {
            faltan.add("faltan los datos de la toma de muestra");
        }
        Set<Long> conValor = o.getResultados().stream().map(x -> x.getAnalito().getId()).collect(Collectors.toSet());
        List<String> sinValor = analitosDe(o).values().stream().filter(a -> !conValor.contains(a.getId()))
                .map(Analito::getNombre).toList();
        if (!sinValor.isEmpty()) {
            faltan.add("faltan resultados de " + String.join(", ", sinValor));
        }
        // RFLC-008: trazabilidad técnica
        o.getExamenes().stream().filter(e -> !e.isTrazabilidadCompleta())
                .forEach(e -> faltan.add("falta equipo, lote de reactivo o calibración de " + e.getExamen().getNombre()));
        // RFLC-007: control de calidad aceptado hoy en cada área
        LocalDate hoy = LocalDate.now(calendario.getZona());
        o.getExamenes().stream().map(e -> e.getExamen().getArea()).distinct().forEach(area -> {
            Optional<ControlCalidad> c = controles.findFirstByFechaAndAreaOrderByRegistradoEnDesc(hoy, area);
            if (c.isEmpty()) {
                faltan.add("no hay control de calidad registrado hoy en " + area.getDescripcion());
            } else if (!c.get().isAceptado()) {
                faltan.add("el último control de calidad de " + area.getDescripcion() + " fue rechazado");
            }
        });
        // Valores críticos: se confirman repitiendo y se informa al médico antes de liberar
        List<ResultadoLab> criticos = o.getResultados().stream().filter(x -> x.getInterpretacion().isCritico()).toList();
        criticos.stream().filter(x -> x.getRepeticiones() == 0)
                .forEach(x -> faltan.add("confirme el valor crítico de " + x.getAnalito().getNombre() + " repitiendo la prueba"));
        if (!criticos.isEmpty() && contactoCritico == null) {
            faltan.add("registre a quién informó el valor crítico");
        }
        return faltan;
    }

    /** Revisiones sugeridas que no bloquean: delta check y coherencia hemoglobina/hematocrito. */
    private List<String> advertencias(OrdenLab o) {
        List<String> avisos = new ArrayList<>();
        o.getResultados().stream().filter(ResultadoLab::isAlertaDelta).forEach(x -> avisos.add(
                x.getAnalito().getNombre() + " cambió " + x.getDeltaPorcentaje().stripTrailingZeros().toPlainString()
                        + " % frente al resultado previo (" + x.getValorPrevio().stripTrailingZeros().toPlainString() + ")"));
        Map<String, BigDecimal> valores = o.getResultados().stream()
                .collect(Collectors.toMap(x -> x.getAnalito().getCodigo(), ResultadoLab::getValor));
        BigDecimal hgb = valores.get("HGB");
        BigDecimal hct = valores.get("HCT");
        if (hgb != null && hct != null && hct.subtract(hgb.multiply(BigDecimal.valueOf(3))).abs().compareTo(MARGEN_HB_HTO) > 0) {
            avisos.add("Hemoglobina y hematocrito no son coherentes (el hematocrito suele ser ~3 veces la hemoglobina)");
        }
        return avisos;
    }

    // ---------- Control de calidad (RFLC-007) ----------

    @Transactional
    public ControlCalidadDto registrarControl(ControlCalidadRequest r) {
        return ControlCalidadDto.de(controles.save(new ControlCalidad(r.area(), r.equipo().trim(),
                LocalDate.now(calendario.getZona()), r.niveles().trim(), r.loteControl().trim(), r.aceptado(),
                limpio(r.observacion()), usuario())));
    }

    @Transactional(readOnly = true)
    public List<ControlCalidadDto> controlesDeHoy() {
        return controles.findByFechaOrderByRegistradoEnDesc(LocalDate.now(calendario.getZona())).stream()
                .map(ControlCalidadDto::de).toList();
    }

    // ---------- RFLC-012: alertas de TAT ----------

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    @Transactional
    public void alertarTatVencidos() {
        Instant ahora = Instant.now();
        ordenes.findByEstadoInOrderByOrdenadaEn(EnumSet.of(EstadoOrden.ORDENADA, EstadoOrden.MUESTRA_RECIBIDA,
                        EstadoOrden.EN_PROCESO)).stream()
                .filter(o -> !o.isTatAlertado() && o.tatVencido(ahora))
                .forEach(o -> {
                    o.marcarTatAlertado();
                    notificaciones.paraRol(Rol.LABORATORIO, "Tiempo de respuesta excedido " + o.getNumero(),
                            o.getPrioridad().getDescripcion() + " · " + o.getEstado().getDescripcion() + " · ordenada hace "
                                    + Duration.between(o.getOrdenadaEn(), ahora).toMinutes() + " min",
                            "/laboratorio/ordenes/" + o.getId());
                });
    }

    // ---------- Para el resumen clínico del médico (RFMT-002 / RFMT-005) ----------

    @Transactional(readOnly = true)
    public List<ResultadoRecienteDto> resultadosRecientes(Long pacienteId) {
        return resultados.validadosDePaciente(pacienteId, PageRequest.of(0, MAX_RESULTADOS_RESUMEN)).stream()
                .map(r -> new ResultadoRecienteDto(r.getOrden().getId(), r.getAnalito().getExamen().getNombre(),
                        r.getAnalito().getNombre(), r.getValor(), r.getAnalito().getUnidad(), r.getAnalito().getDecimales(),
                        r.getInterpretacion().name(), r.getInterpretacion().getDescripcion(),
                        rangoTexto(r.getRefMin(), r.getRefMax()), r.getOrden().getValidadaEn()))
                .toList();
    }

    // ---------- armado de respuestas ----------

    private OrdenResumenDto resumen(OrdenLab o, Instant ahora) {
        return new OrdenResumenDto(o.getId(), o.getNumero(), PacienteResumenDto.de(pacienteService.buscarEntidad(o.getPacienteId())),
                o.getMedicoNombre(), o.getPrioridad().name(), o.getPrioridad().getDescripcion(), o.getEstado().name(),
                o.getEstado().getDescripcion(), o.getExamenes().stream().map(e -> e.getExamen().getNombre()).toList(),
                o.getOrdenadaEn(), o.limiteTat(), o.tatVencido(ahora),
                o.getResultados().stream().anyMatch(x -> x.getInterpretacion().isCritico()), o.getValidadaEn());
    }

    private OrdenDto armar(OrdenLab o) {
        Paciente p = pacienteService.buscarEntidad(o.getPacienteId());
        Map<Long, ResultadoLab> porAnalito = o.getResultados().stream()
                .collect(Collectors.toMap(x -> x.getAnalito().getId(), Function.identity()));
        List<Long> idsResultados = o.getResultados().stream().map(ResultadoLab::getId).filter(Objects::nonNull).toList();
        Map<Long, List<ReprocesoDto>> reprocesosPor = idsResultados.isEmpty() ? Map.of()
                : reprocesos.findByResultadoIdInOrderByFecha(idsResultados).stream()
                .collect(Collectors.groupingBy(ReprocesoLab::getResultadoId, Collectors.mapping(ReprocesoDto::de, Collectors.toList())));

        List<OrdenExamenDto> items = o.getExamenes().stream().map(oe -> {
            ExamenLab e = oe.getExamen();
            List<ResultadoFilaDto> filas = e.getAnalitos().stream().map(a -> {
                ResultadoLab res = porAnalito.get(a.getId());
                if (res == null) {
                    Optional<RangoReferencia> rango = rangoPara(a, p);
                    return new ResultadoFilaDto(a.getId(), a.getCodigo(), a.getNombre(), a.getUnidad(), a.getDecimales(),
                            null, null, null, rango.map(x -> rangoTexto(x.getRefMin(), x.getRefMax())).orElse("—"),
                            null, null, null, false, 0, List.of());
                }
                return new ResultadoFilaDto(a.getId(), a.getCodigo(), a.getNombre(), a.getUnidad(), a.getDecimales(),
                        res.getValor(), res.getInterpretacion().name(), res.getInterpretacion().getDescripcion(),
                        rangoTexto(res.getRefMin(), res.getRefMax()), res.getValorPrevio(), res.getFechaPrevio(),
                        res.getDeltaPorcentaje(), res.isAlertaDelta(), res.getRepeticiones(),
                        reprocesosPor.getOrDefault(res.getId(), List.of()));
            }).toList();
            return new OrdenExamenDto(oe.getId(), e.getId(), e.getCodigo(), e.getNombre(), e.getArea().name(),
                    e.getArea().getDescripcion(), e.getTipoMuestra(), oe.getEquipo(), oe.getLoteReactivo(),
                    oe.getCalibracion(), filas);
        }).toList();

        LocalDate hoy = LocalDate.now(calendario.getZona());
        List<ControlDiaDto> controlesDia = o.getExamenes().stream().map(e -> e.getExamen().getArea()).distinct()
                .map(area -> controles.findFirstByFechaAndAreaOrderByRegistradoEnDesc(hoy, area)
                        .map(c -> new ControlDiaDto(area.name(), area.getDescripcion(), c.isAceptado(), c.getEquipo(),
                                c.getRegistradoPor(), c.getRegistradoEn()))
                        .orElse(new ControlDiaDto(area.name(), area.getDescripcion(), null, null, null, null)))
                .toList();

        boolean criticos = o.getResultados().stream().anyMatch(x -> x.getInterpretacion().isCritico());
        List<String> pendientes = o.getEstado() == EstadoOrden.EN_PROCESO ? pendientes(o, "-") : List.of();
        return new OrdenDto(o.getId(), o.getNumero(), PacienteResumenDto.de(p), o.getConsultaId(), o.getMedico(),
                o.getMedicoNombre(), o.getPrioridad().name(), o.getPrioridad().getDescripcion(), o.getIndicaciones(),
                o.getEstado().name(), o.getEstado().getDescripcion(), o.getOrdenadaEn(), o.getMuestraTomadaEn(),
                o.getMuestraTomadaPor(), o.getMuestraRecibidaEn(), o.getProcesoEn(), o.getValidadaEn(), o.getValidadaPor(),
                o.getMotivoRechazo() == null ? null : o.getMotivoRechazo().name(),
                o.getMotivoRechazo() == null ? null : o.getMotivoRechazo().getDescripcion(), o.getDetalleRechazo(),
                o.getCriticoContacto(), o.getCriticoMedio(), o.getCriticoNotificadoEn(), o.limiteTat(),
                o.tatVencido(Instant.now()), criticos, items, controlesDia, pendientes, advertencias(o),
                eventos.findByOrdenIdOrderByFechaAscIdAsc(o.getId()).stream().map(EventoDto::de).toList(), contexto(o, p));
    }

    /** RFLC-013: motivo, diagnósticos y alergias de la consulta que originó la orden. */
    private ContextoClinicoDto contexto(OrdenLab o, Paciente p) {
        Optional<Consulta> consulta = o.getConsultaId() == null ? Optional.empty() : consultas.findById(o.getConsultaId());
        List<String> dx = consulta.map(c -> c.getDiagnosticos().stream()
                .map(d -> d.getDiagnostico().getCodigo() + " " + d.getDiagnostico().getNombre()).toList()).orElse(List.of());
        List<String> alergias = antecedentes.findByPacienteIdAndTipoAndActivoTrue(p.getId(), Antecedente.Tipo.ALERGIA)
                .stream().map(Antecedente::getDescripcion).toList();
        return new ContextoClinicoDto(p.getSexo() == null ? null : p.getSexo().name(), p.getEdad(),
                consulta.map(Consulta::getMotivoConsulta).orElse(null), dx, alergias);
    }

    private static Map<Long, Analito> analitosDe(OrdenLab o) {
        Map<Long, Analito> mapa = new LinkedHashMap<>();
        o.getExamenes().forEach(e -> e.getExamen().getAnalitos().forEach(a -> mapa.put(a.getId(), a)));
        return mapa;
    }

    static String rangoTexto(BigDecimal min, BigDecimal max) {
        if (min != null && max != null) return num(min) + " – " + num(max);
        if (max != null) return "≤ " + num(max);
        if (min != null) return "≥ " + num(min);
        return "—";
    }

    private static String num(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private void evento(OrdenLab o, String accion, String detalle) {
        eventos.save(new EventoOrdenLab(o.getId(), accion, detalle, usuario()));
    }

    private OrdenLab buscar(Long id) {
        return ordenes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Orden de laboratorio", id));
    }

    private String usuario() {
        return auditor.getCurrentAuditor().orElse("sistema");
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
