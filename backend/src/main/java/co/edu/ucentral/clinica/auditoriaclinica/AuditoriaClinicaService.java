package co.edu.ucentral.clinica.auditoriaclinica;

import co.edu.ucentral.clinica.admisiones.ConsentimientoRepository;
import co.edu.ucentral.clinica.admisiones.Episodio;
import co.edu.ucentral.clinica.admisiones.EpisodioRepository;
import co.edu.ucentral.clinica.admisiones.dto.ConsentimientoDto;
import co.edu.ucentral.clinica.agenda.CalculadoraDisponibilidad;
import co.edu.ucentral.clinica.auditoriaclinica.dto.AuditoriaDtos.CambioCodificacionDto;
import co.edu.ucentral.clinica.auditoriaclinica.dto.AuditoriaDtos.CodificacionDto;
import co.edu.ucentral.clinica.auditoriaclinica.dto.AuditoriaDtos.ConteoDto;
import co.edu.ucentral.clinica.auditoriaclinica.dto.AuditoriaDtos.DevolucionDto;
import co.edu.ucentral.clinica.auditoriaclinica.dto.AuditoriaDtos.DiagnosticoCatalogoDto;
import co.edu.ucentral.clinica.auditoriaclinica.dto.AuditoriaDtos.ExpedienteDto;
import co.edu.ucentral.clinica.auditoriaclinica.dto.AuditoriaDtos.TableroDto;
import co.edu.ucentral.clinica.auditoriaclinica.dto.AuditoriaDtos.VerificacionDto;
import co.edu.ucentral.clinica.catalogos.CatalogoService;
import co.edu.ucentral.clinica.comun.excepciones.DatosInvalidosException;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.documentos.DocumentoService;
import co.edu.ucentral.clinica.facturacion.Cargo;
import co.edu.ucentral.clinica.facturacion.Cuenta;
import co.edu.ucentral.clinica.facturacion.CuentaRepository;
import co.edu.ucentral.clinica.facturacion.EstadoCuenta;
import co.edu.ucentral.clinica.facturacion.FacturacionService;
import co.edu.ucentral.clinica.facturacion.Glosa;
import co.edu.ucentral.clinica.facturacion.GlosaRepository;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.CuentaResumenDto;
import co.edu.ucentral.clinica.historia.Consulta;
import co.edu.ucentral.clinica.historia.ConsultaDiagnostico;
import co.edu.ucentral.clinica.historia.ConsultaRepository;
import co.edu.ucentral.clinica.historia.ConsultaService;
import co.edu.ucentral.clinica.historia.Diagnostico;
import co.edu.ucentral.clinica.historia.DiagnosticoRepository;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.ConsultaDto;
import co.edu.ucentral.clinica.imagenes.ImagenesService;
import co.edu.ucentral.clinica.laboratorio.LaboratorioService;
import co.edu.ucentral.clinica.notificaciones.NotificacionService;
import co.edu.ucentral.clinica.pacientes.Normalizador;
import co.edu.ucentral.clinica.patologia.PatologiaService;
import co.edu.ucentral.clinica.seguridad.Rol;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Auditoría clínica de cuentas: revisión integral, codificación, aprobación o devolución (RFAU-002 a RFAU-009). */
@Service
public class AuditoriaClinicaService {

    private static final java.util.regex.Pattern CIE10 = java.util.regex.Pattern.compile("^[A-Z]\\d{2}(\\.\\d{1,2})?$");
    /** Margen antes de abrir la cuenta para incluir lo que se documentó al ingreso. */
    private static final Duration MARGEN = Duration.ofDays(2);

    private final CuentaRepository cuentas;
    private final FacturacionService facturacion;
    private final CodificacionRepository codificaciones;
    private final CodificacionCambioRepository cambios;
    private final DevolucionRepository devoluciones;
    private final DiagnosticoRepository diagnosticos;
    private final ConsultaRepository consultas;
    private final ConsultaService consultaService;
    private final EpisodioRepository episodios;
    private final ConsentimientoRepository consentimientos;
    private final LaboratorioService laboratorio;
    private final ImagenesService imagenes;
    private final PatologiaService patologia;
    private final DocumentoService documentos;
    private final GlosaRepository glosas;
    private final SugeridorDiagnosticos sugeridor;
    private final CatalogoService catalogos;
    private final NotificacionService notificaciones;
    private final CalculadoraDisponibilidad calendario;
    private final AuditorAware<String> auditor;

    public AuditoriaClinicaService(CuentaRepository cuentas, FacturacionService facturacion, CodificacionRepository codificaciones,
                                   CodificacionCambioRepository cambios, DevolucionRepository devoluciones,
                                   DiagnosticoRepository diagnosticos, ConsultaRepository consultas, ConsultaService consultaService,
                                   EpisodioRepository episodios, ConsentimientoRepository consentimientos,
                                   LaboratorioService laboratorio, ImagenesService imagenes, PatologiaService patologia,
                                   DocumentoService documentos, GlosaRepository glosas, SugeridorDiagnosticos sugeridor,
                                   CatalogoService catalogos, NotificacionService notificaciones,
                                   CalculadoraDisponibilidad calendario, AuditorAware<String> auditor) {
        this.cuentas = cuentas;
        this.facturacion = facturacion;
        this.codificaciones = codificaciones;
        this.cambios = cambios;
        this.devoluciones = devoluciones;
        this.diagnosticos = diagnosticos;
        this.consultas = consultas;
        this.consultaService = consultaService;
        this.episodios = episodios;
        this.consentimientos = consentimientos;
        this.laboratorio = laboratorio;
        this.imagenes = imagenes;
        this.patologia = patologia;
        this.documentos = documentos;
        this.glosas = glosas;
        this.sugeridor = sugeridor;
        this.catalogos = catalogos;
        this.notificaciones = notificaciones;
        this.calendario = calendario;
        this.auditor = auditor;
    }

    // ---------- Cola ----------

    @Transactional(readOnly = true)
    public List<CuentaResumenDto> cola(EstadoCuenta estado) {
        Set<EstadoCuenta> estados = estado != null ? EnumSet.of(estado) : EnumSet.of(EstadoCuenta.EN_AUDITORIA);
        return cuentas.findByEstadoInOrderByAbiertaEnDesc(estados).stream().map(facturacion::resumen).toList();
    }

    // ---------- Expediente completo (RFAU-002) ----------

    @Transactional(readOnly = true)
    public ExpedienteDto expediente(Long cuentaId) {
        Cuenta c = facturacion.buscar(cuentaId);
        Instant desde = c.getAbiertaEn().minus(MARGEN);
        Optional<Episodio> episodio = c.getEpisodioId() == null ? Optional.empty() : episodios.findById(c.getEpisodioId());
        List<Consulta> delPeriodo = consultasDe(c);
        List<ConsultaDto> notas = delPeriodo.stream().map(x -> consultaService.obtener(x.getId())).toList();
        String textoEpisodio = episodio.map(e -> e.getNumero() + " · " + e.getTipoAtencion().getDescripcion() + " · "
                + e.getEstadoCobertura().getDescripcion() + (e.getNumeroAutorizacion() == null ? "" : " · autorización " + e.getNumeroAutorizacion()))
                .orElse("Atención ambulatoria (sin episodio de admisión)");
        List<ConsentimientoDto> cons = episodio.map(e -> consentimientos.findByEpisodioIdOrderByCreadoEn(e.getId()).stream()
                .map(ConsentimientoDto::de).toList()).orElse(List.of());
        return new ExpedienteDto(facturacion.obtener(cuentaId), textoEpisodio, cons, notas,
                laboratorio.dePaciente(c.getPacienteId()).stream().filter(o -> o.ordenadaEn().isAfter(desde)).toList(),
                imagenes.dePaciente(c.getPacienteId()).stream().filter(e -> e.ordenadoEn().isAfter(desde)).toList(),
                patologia.dePaciente(c.getPacienteId()).stream().filter(x -> x.solicitadoEn().isAfter(desde)).toList(),
                documentos.dePaciente(c.getPacienteId()).stream()
                        .filter(d -> !d.fechaDocumento().isBefore(LocalDate.ofInstant(desde, calendario.getZona()))).toList(),
                verificar(c, delPeriodo), codificacion(c.getId()), sugerencias(c, delPeriodo), devolucionesDe(c.getId()));
    }

    /** Consultas de la atención: las del episodio, o las del paciente desde que se abrió la cuenta. */
    private List<Consulta> consultasDe(Cuenta c) {
        Instant desde = c.getAbiertaEn().minus(MARGEN);
        return consultas.findByPacienteIdOrderByInicioDesc(c.getPacienteId()).stream()
                .filter(x -> c.getEpisodioId() != null ? c.getEpisodioId().equals(x.getEpisodioId()) : x.getInicio().isAfter(desde))
                .toList();
    }

    /** RFAU-007: lo que debe cumplirse para aprobar. */
    List<VerificacionDto> verificar(Cuenta c, List<Consulta> delPeriodo) {
        List<VerificacionDto> v = new ArrayList<>();
        List<Cargo> activos = c.activos();
        long sinSoporte = activos.stream().filter(x -> !x.isTieneSoporte()).count();
        v.add(new VerificacionDto("Cada cargo tiene orden médica o soporte clínico", sinSoporte == 0,
                sinSoporte == 0 ? activos.size() + " cargo(s) con soporte" : sinSoporte + " cargo(s) sin soporte: "
                        + activos.stream().filter(x -> !x.isTieneSoporte()).map(x -> x.getServicio().getNombre()).collect(Collectors.joining(", "))));
        long sinFirmar = delPeriodo.stream().filter(x -> x.getEstado() != Consulta.Estado.FIRMADA).count();
        v.add(new VerificacionDto("Las notas médicas están firmadas", sinFirmar == 0,
                delPeriodo.isEmpty() ? "La atención no tiene consulta médica (servicios con orden)"
                        : sinFirmar == 0 ? delPeriodo.size() + " consulta(s) firmada(s)" : sinFirmar + " consulta(s) sin firmar"));
        boolean exigeAutorizacion = activos.stream().anyMatch(x -> x.isRequiereAutorizacion() && !x.isACargoPaciente());
        boolean autorizada = !exigeAutorizacion || c.isUrgencia() || c.getNumeroAutorizacion() != null;
        v.add(new VerificacionDto("Autorización de la aseguradora", autorizada, !exigeAutorizacion ? "Ningún servicio la exige"
                : c.isUrgencia() ? "Atención de urgencias: no requiere autorización previa"
                : c.getNumeroAutorizacion() != null ? "Autorización " + c.getNumeroAutorizacion() : "Hay servicios que la exigen y no está registrada"));
        Optional<Codificacion> cod = codificaciones.findByCuentaId(c.getId());
        boolean codificada = cod.map(Codificacion::getDiagnosticoPrincipal).isPresent();
        v.add(new VerificacionDto("Diagnóstico principal codificado", codificada,
                codificada ? cod.get().getDiagnosticoPrincipal() : "Falta codificar el diagnóstico principal"));
        return v;
    }

    private List<SugeridorDiagnosticos.Sugerencia> sugerencias(Cuenta c, List<Consulta> delPeriodo) {
        List<String> textos = delPeriodo.stream().flatMap(x -> Stream.of(x.getMotivoConsulta(), x.getEnfermedadActual(),
                x.getExamenFisico(), x.getPlan())).filter(Objects::nonNull).toList();
        Set<String> delMedico = delPeriodo.stream().flatMap(x -> x.getDiagnosticos().stream())
                .map(d -> d.getDiagnostico().getCodigo()).collect(Collectors.toSet());
        List<String> todos = new ArrayList<>(textos);
        imagenes.dePaciente(c.getPacienteId()).stream().filter(e -> e.impresion() != null && e.ordenadoEn().isAfter(c.getAbiertaEn().minus(MARGEN)))
                .forEach(e -> todos.add(e.impresion()));
        return sugeridor.sugerir(todos, diagnosticos.findAllByOrderByCodigo(), delMedico);
    }

    // ---------- Codificación (RFAU-003 / RFAU-009) ----------

    @Transactional
    public CodificacionDto codificar(Long cuentaId, String principal, List<String> relacionados, String motivo) {
        Cuenta c = facturacion.buscar(cuentaId);
        if (c.getEstado() != EstadoCuenta.EN_AUDITORIA && c.getEstado() != EstadoCuenta.DEVUELTA) {
            throw new ReglaNegocioException("CUENTA_ESTADO_INVALIDO", "Solo se codifica una cuenta en auditoría.");
        }
        String p = validarCodigo(principal, "diagnosticoPrincipal");
        List<String> rel = new ArrayList<>(new LinkedHashSet<>(relacionados == null ? List.of() : relacionados.stream()
                .map(x -> validarCodigo(x, "relacionados")).filter(x -> !x.equals(p)).toList()));
        if (rel.size() > 4) throw new DatosInvalidosException(Map.of("relacionados", "Máximo 4 diagnósticos relacionados"));
        Codificacion cod = codificaciones.findByCuentaId(cuentaId).orElseGet(() -> codificaciones.save(new Codificacion(cuentaId)));
        String yo = usuario();
        String anteriorRel = cod.getDiagnosticosRelacionados();
        String nuevoRel = rel.isEmpty() ? null : String.join(",", rel);
        boolean cambiaPrincipal = !p.equals(cod.getDiagnosticoPrincipal());
        boolean cambiaRel = !Objects.equals(anteriorRel, nuevoRel);
        if (!cambiaPrincipal && !cambiaRel) return codificacion(cuentaId);
        // Cambiar un código ya asignado exige motivo; el primero no
        boolean correccion = (cambiaPrincipal && cod.getDiagnosticoPrincipal() != null) || (cambiaRel && anteriorRel != null);
        if (correccion && limpio(motivo) == null) {
            throw new DatosInvalidosException(Map.of("motivo", "Explique por qué cambia la codificación"));
        }
        if (cambiaPrincipal) cambios.save(new CodificacionCambio(cuentaId, "Diagnóstico principal", cod.getDiagnosticoPrincipal(), p, limpio(motivo), yo));
        if (cambiaRel) cambios.save(new CodificacionCambio(cuentaId, "Diagnósticos relacionados", anteriorRel, nuevoRel, limpio(motivo), yo));
        cod.actualizar(p, rel, yo);
        facturacion.evento(c, "CODIFICACION", "Principal " + p + (rel.isEmpty() ? "" : " · relacionados " + String.join(", ", rel)));
        return codificacion(cuentaId);
    }

    private String validarCodigo(String codigo, String campo) {
        String cod = codigo == null ? "" : codigo.trim().toUpperCase();
        Diagnostico d = diagnosticos.findByCodigoIgnoreCase(cod)
                .orElseThrow(() -> new DatosInvalidosException(Map.of(campo, "El código " + cod + " no está en el catálogo CIE-10")));
        if (!d.isActivo()) throw new DatosInvalidosException(Map.of(campo, "El código " + cod + " está inactivo en el catálogo"));
        return d.getCodigo();
    }

    @Transactional(readOnly = true)
    public CodificacionDto codificacion(Long cuentaId) {
        Optional<Codificacion> c = codificaciones.findByCuentaId(cuentaId);
        Map<String, String> nombres = diagnosticos.findAllByOrderByCodigo().stream()
                .collect(Collectors.toMap(Diagnostico::getCodigo, Diagnostico::getNombre));
        List<String> rel = c.map(Codificacion::relacionados).orElse(List.of());
        String principal = c.map(Codificacion::getDiagnosticoPrincipal).orElse(null);
        return new CodificacionDto(principal, principal == null ? null : nombres.get(principal), rel,
                rel.stream().map(x -> nombres.getOrDefault(x, x)).toList(), c.map(Codificacion::getModificadoPor).orElse(null),
                c.map(Codificacion::getModificadoEn).orElse(null),
                cambios.findByCuentaIdOrderByFechaDescIdDesc(cuentaId).stream().map(CambioCodificacionDto::de).toList());
    }

    // ---------- Aprobación y devolución (RFAU-004 / RFAU-007) ----------

    @Transactional
    public ExpedienteDto aprobar(Long cuentaId) {
        Cuenta c = facturacion.buscar(cuentaId);
        List<VerificacionDto> pendientes = verificar(c, consultasDe(c)).stream().filter(v -> !v.cumple()).toList();
        if (!pendientes.isEmpty()) {
            throw new ReglaNegocioException("AUDITORIA_BLOQUEADA", "No se puede aprobar: " + pendientes.stream()
                    .map(v -> v.punto().toLowerCase() + " (" + v.detalle() + ")").collect(Collectors.joining("; ")) + ".");
        }
        String yo = usuario();
        // RNFAU-003: quien registró cargos a mano en la cuenta no puede aprobarla
        if (c.activos().stream().anyMatch(x -> x.getOrigenModulo() == null && x.getRegistradoPor().equalsIgnoreCase(yo))) {
            throw new ReglaNegocioException("SEGREGACION", "Usted registró cargos en esta cuenta: la debe aprobar otro auditor.");
        }
        c.aprobarAuditoria(yo);
        facturacion.evento(c, "AUDITADA", "Aprobada por auditoría clínica");
        notificaciones.paraRol(Rol.FACTURACION, "Cuenta auditada " + c.getNumero(), "Lista para facturar", "/facturacion/cuentas/" + c.getId());
        return expediente(cuentaId);
    }

    @Transactional
    public ExpedienteDto devolver(Long cuentaId, Devolucion.Motivo motivo, String detalle, Devolucion.Destino destino) {
        Cuenta c = facturacion.buscar(cuentaId);
        c.devolver();
        devoluciones.save(new Devolucion(cuentaId, motivo, detalle.trim(), destino, usuario()));
        facturacion.evento(c, "DEVUELTA", motivo.getDescripcion() + ": " + detalle.trim() + " → " + destino.name().toLowerCase());
        Rol rol = switch (destino) {
            case FACTURACION -> Rol.FACTURACION;
            case MEDICO -> Rol.MEDICO;
            case ADMISIONES -> Rol.ADMISIONES;
        };
        notificaciones.paraRol(rol, "Cuenta devuelta por auditoría " + c.getNumero(), motivo.getDescripcion() + ": " + detalle.trim(),
                destino == Devolucion.Destino.FACTURACION ? "/facturacion/cuentas/" + c.getId() : "/historia/" + c.getPacienteId());
        return expediente(cuentaId);
    }

    /** La cuenta volvió corregida: se cierran sus devoluciones abiertas y queda medido el tiempo. */
    @EventListener
    public void alCorregirse(FacturacionService.CuentaCorregida e) {
        devoluciones.findByCuentaIdAndCorregidaEnIsNull(e.cuentaId()).forEach(d -> d.corregir(e.usuario(), e.respuesta()));
    }

    private List<DevolucionDto> devolucionesDe(Long cuentaId) {
        return devoluciones.findByCuentaIdOrderByDevueltaEnDesc(cuentaId).stream().map(DevolucionDto::de).toList();
    }

    // ---------- Tablero (RFAU-006) ----------

    @Transactional(readOnly = true)
    public TableroDto tablero(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) throw new DatosInvalidosException(Map.of("hasta", "Debe ser posterior a la fecha inicial"));
        Instant ini = desde.atStartOfDay(calendario.getZona()).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(calendario.getZona()).toInstant();
        List<Cuenta> todas = cuentas.findAll();
        long enAuditoria = todas.stream().filter(c -> c.getEstado() == EstadoCuenta.EN_AUDITORIA).count();
        long devueltas = todas.stream().filter(c -> c.getEstado() == EstadoCuenta.DEVUELTA).count();
        long auditadas = todas.stream().filter(c -> c.getAuditadaEn() != null && !c.getAuditadaEn().isBefore(ini) && c.getAuditadaEn().isBefore(fin)).count();
        List<Devolucion> periodo = devoluciones.findByDevueltaEnGreaterThanEqualAndDevueltaEnLessThan(ini, fin);
        Map<Devolucion.Motivo, List<Devolucion>> porMotivo = periodo.stream().collect(Collectors.groupingBy(Devolucion::getMotivo,
                () -> new java.util.EnumMap<>(Devolucion.Motivo.class), Collectors.toList()));
        Double horas = periodo.stream().filter(d -> d.getCorregidaEn() != null).mapToLong(d -> d.tiempoCorreccion().toMinutes())
                .average().stream().boxed().findFirst().map(m -> Math.round(m / 6.0) / 10.0).orElse(null);
        long sinFirmar = consultas.findAll().stream().filter(c -> c.getEstado() != Consulta.Estado.FIRMADA
                && c.getInicio().isBefore(Instant.now().minus(Duration.ofHours(24)))).count();
        long sinSoporte = todas.stream().filter(c -> EstadoCuenta.EDITABLES.contains(c.getEstado()) || c.getEstado() == EstadoCuenta.EN_AUDITORIA)
                .flatMap(c -> c.activos().stream()).filter(x -> !x.isTieneSoporte()).count();
        List<Glosa> glosasPeriodo = glosas.findByRecibidaEnBetween(desde, hasta);
        Map<Glosa.Concepto, List<Glosa>> porConcepto = glosasPeriodo.stream().collect(Collectors.groupingBy(Glosa::getConcepto,
                () -> new java.util.EnumMap<>(Glosa.Concepto.class), Collectors.toList()));
        List<Glosa> clinicas = glosasPeriodo.stream().filter(g -> g.getConcepto().isClinica()).toList();
        return new TableroDto(enAuditoria, devueltas, auditadas, periodo.size(),
                auditadas + periodo.size() == 0 ? null : Math.round(1000.0 * periodo.size() / (auditadas + periodo.size())) / 10.0,
                horas,
                porMotivo.entrySet().stream().map(e -> new ConteoDto(e.getKey().name(), e.getKey().getDescripcion(), e.getValue().size(), null,
                        e.getValue().stream().filter(d -> d.getCorregidaEn() != null).mapToLong(d -> d.tiempoCorreccion().toMinutes())
                                .average().stream().boxed().findFirst().map(m -> Math.round(m / 6.0) / 10.0).orElse(null))).toList(),
                sinFirmar, sinSoporte,
                porConcepto.entrySet().stream().map(e -> new ConteoDto(e.getKey().name(), e.getKey().getDescripcion()
                        + (e.getKey().isClinica() ? " (causa clínica)" : ""), e.getValue().size(),
                        e.getValue().stream().map(Glosa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add), null)).toList(),
                clinicas.size(), clinicas.stream().map(Glosa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // ---------- Catálogo CIE-10 con versiones (RFAU-005) ----------

    @Transactional(readOnly = true)
    public List<DiagnosticoCatalogoDto> diagnosticos(String texto) {
        String t = Normalizador.texto(texto);
        return diagnosticos.findAllByOrderByCodigo().stream()
                .filter(d -> t.isEmpty() || Normalizador.texto(d.getCodigo() + " " + d.getNombre()).contains(t)
                        || d.getCodigo().toLowerCase().startsWith(t.replace(" ", ".")))
                .map(d -> new DiagnosticoCatalogoDto(d.getId(), d.getCodigo(), d.getNombre(), d.isActivo())).toList();
    }

    @Transactional
    public DiagnosticoCatalogoDto crearDiagnostico(String codigo, String nombre) {
        String cod = codigo.trim().toUpperCase();
        if (!CIE10.matcher(cod).matches()) {
            throw new DatosInvalidosException(Map.of("codigo", "Formato CIE-10: letra, dos dígitos y opcionalmente punto y 1-2 dígitos (p. ej. J18.9)"));
        }
        if (diagnosticos.findByCodigoIgnoreCase(cod).isPresent()) {
            throw new DatosInvalidosException(Map.of("codigo", "Ese código ya existe"));
        }
        Diagnostico d = diagnosticos.save(new Diagnostico(cod, nombre.trim()));
        catalogos.registrarCambio(CatalogoService.DIAGNOSTICOS, cod, "CREADO", nombre.trim());
        return new DiagnosticoCatalogoDto(d.getId(), d.getCodigo(), d.getNombre(), d.isActivo());
    }

    @Transactional
    public DiagnosticoCatalogoDto actualizarDiagnostico(Long id, String nombre, boolean activo, String motivo) {
        Diagnostico d = diagnosticos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Diagnóstico", id));
        String accion = activo == d.isActivo() ? "MODIFICADO" : activo ? "REACTIVADO" : "INACTIVADO";
        String detalle = (nombre.trim().equals(d.getNombre()) ? "" : "«" + d.getNombre() + "» → «" + nombre.trim() + "». ") + motivo.trim();
        d.renombrar(nombre.trim());
        d.cambiarActivo(activo);
        catalogos.registrarCambio(CatalogoService.DIAGNOSTICOS, d.getCodigo(), accion, detalle);
        return new DiagnosticoCatalogoDto(d.getId(), d.getCodigo(), d.getNombre(), d.isActivo());
    }

    private String usuario() {
        return auditor.getCurrentAuditor().orElse("sistema");
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
