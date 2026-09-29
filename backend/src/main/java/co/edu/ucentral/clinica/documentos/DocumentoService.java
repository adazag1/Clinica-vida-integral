package co.edu.ucentral.clinica.documentos;

import co.edu.ucentral.clinica.admisiones.EpisodioRepository;
import co.edu.ucentral.clinica.agenda.CalculadoraDisponibilidad;
import co.edu.ucentral.clinica.archivos.AlmacenArchivos;
import co.edu.ucentral.clinica.archivos.Archivo;
import co.edu.ucentral.clinica.comun.excepciones.DatosInvalidosException;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.documentos.dto.DocumentoDtos.ConteoDto;
import co.edu.ucentral.clinica.documentos.dto.DocumentoDtos.DocumentoDto;
import co.edu.ucentral.clinica.documentos.dto.DocumentoDtos.DocumentoResumenDto;
import co.edu.ucentral.clinica.documentos.dto.DocumentoDtos.EventoDto;
import co.edu.ucentral.clinica.documentos.dto.DocumentoDtos.ReporteDto;
import co.edu.ucentral.clinica.documentos.dto.DocumentoDtos.SolicitudDto;
import co.edu.ucentral.clinica.documentos.dto.DocumentoDtos.VersionDto;
import co.edu.ucentral.clinica.documentos.dto.DocumentoSolicitudes.CargaRequest;
import co.edu.ucentral.clinica.documentos.dto.DocumentoSolicitudes.SolicitudRequest;
import co.edu.ucentral.clinica.documentos.dto.DocumentoSolicitudes.UbicacionRequest;
import co.edu.ucentral.clinica.notificaciones.NotificacionService;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteRepository;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;
import co.edu.ucentral.clinica.seguridad.Rol;
import co.edu.ucentral.clinica.seguridad.Usuario;
import co.edu.ucentral.clinica.seguridad.UsuarioRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Gestión documental de la historia clínica (RFGAC-002 a RFGAC-011). */
@Service
public class DocumentoService {

    /** Filtros de la búsqueda avanzada (RFGAC-003); todos opcionales. */
    public record Busqueda(Long pacienteId, String documentoPaciente, TipoDocumento tipo, LocalDate desde, LocalDate hasta,
                           String profesional, String servicio, String texto, Documento.Estado estado) {
    }

    private static final int MAX_PAGINA = 100;

    private final DocumentoRepository documentos;
    private final DocumentoVersionRepository versiones;
    private final DocumentoEventoRepository eventos;
    private final SolicitudArchivoRepository solicitudes;
    private final PacienteService pacienteService;
    private final PacienteRepository pacientes;
    private final EpisodioRepository episodios;
    private final UsuarioRepository usuarios;
    private final AlmacenArchivos almacen;
    private final NotificacionService notificaciones;
    private final CalculadoraDisponibilidad calendario;
    private final AuditorAware<String> auditor;
    private final int anosRetencion;

    public DocumentoService(DocumentoRepository documentos, DocumentoVersionRepository versiones,
                            DocumentoEventoRepository eventos, SolicitudArchivoRepository solicitudes,
                            PacienteService pacienteService, PacienteRepository pacientes, EpisodioRepository episodios,
                            UsuarioRepository usuarios, AlmacenArchivos almacen, NotificacionService notificaciones,
                            CalculadoraDisponibilidad calendario, AuditorAware<String> auditor,
                            @Value("${app.documentos.retencion-anos:15}") int anosRetencion) {
        this.documentos = documentos;
        this.versiones = versiones;
        this.eventos = eventos;
        this.solicitudes = solicitudes;
        this.pacienteService = pacienteService;
        this.pacientes = pacientes;
        this.episodios = episodios;
        this.usuarios = usuarios;
        this.almacen = almacen;
        this.notificaciones = notificaciones;
        this.calendario = calendario;
        this.auditor = auditor;
        this.anosRetencion = anosRetencion;
    }

    // ---------- Registro y carga (RFGAC-002 / 004 / 007 / 010) ----------

    @Transactional
    public DocumentoDto cargar(CargaRequest r, MultipartFile archivo) {
        exigirTipoPermitido(r.tipo());
        Paciente p = pacienteService.buscarEntidad(r.pacienteId());
        if (r.episodioId() != null) {
            episodios.findById(r.episodioId()).filter(e -> e.getPaciente().getId().equals(p.getId()))
                    .orElseThrow(() -> new DatosInvalidosException(Map.of("episodioId", "La atención no es de este paciente")));
        }
        boolean hayArchivo = archivo != null && !archivo.isEmpty();
        if (r.soporte() == Documento.Soporte.DIGITAL && !hayArchivo) {
            throw new DatosInvalidosException(Map.of("archivo", "Adjunte el archivo del documento digital"));
        }
        if (r.soporte() == Documento.Soporte.FISICO && (limpio(r.sala()) == null || limpio(r.estanteria()) == null)) {
            throw new DatosInvalidosException(Map.of("sala", "Para un documento físico indique sala y estantería"));
        }
        Documento d = documentos.save(new Documento(p.getId(), r.episodioId(), r.tipo(), r.titulo().trim(),
                limpio(r.descripcion()), r.fechaDocumento(), r.servicio().trim(), limpio(r.profesional()), r.soporte(),
                Documento.Origen.CARGADO, r.fechaDocumento().plusYears(anosRetencion)));
        if (limpio(r.sala()) != null) {
            d.ubicar(limpio(r.sala()), limpio(r.estanteria()), limpio(r.seccion()), limpio(r.caja()));
        }
        evento(d, DocumentoEvento.Accion.REGISTRADO, d.getTipo().getDescripcion() + (hayArchivo ? "" : " (solo físico)"));
        if (hayArchivo) {
            agregarVersion(d, archivo, null);
        }
        return armar(d);
    }

    /** RFGAC-005: reemplazar el archivo conserva la versión anterior con su huella. */
    @Transactional
    public DocumentoDto nuevaVersion(Long id, MultipartFile archivo, String motivo) {
        Documento d = buscar(id);
        exigirTipoPermitido(d.getTipo());
        if (d.getOrigen() == Documento.Origen.GENERADO) {
            throw new ReglaNegocioException("DOCUMENTO_GENERADO", "Este documento lo genera " + d.getModuloOrigen().toLowerCase()
                    + ": las correcciones se hacen allí y quedan versionadas.");
        }
        agregarVersion(d, archivo, motivo.trim());
        return armar(d);
    }

    private void agregarVersion(Documento d, MultipartFile archivo, String motivo) {
        String usuario = usuario();
        Archivo a = almacen.guardar(archivo, AlmacenArchivos.DOCUMENTOS, usuario);
        int version = d.nuevaVersion(a);
        versiones.save(new DocumentoVersion(d.getId(), version, a, motivo, usuario));
        evento(d, DocumentoEvento.Accion.NUEVA_VERSION, "Versión " + version + " · " + a.getNombre()
                + (motivo == null ? "" : " · " + motivo));
    }

    /** RFGAC-009: registra en la historia lo que otro módulo firmó o validó. */
    @EventListener
    @Transactional
    public void alGenerarse(DocumentoGenerado g) {
        Optional<Documento> existente = documentos.findByModuloOrigenAndReferenciaOrigen(g.modulo(), g.referencia());
        if (existente.isPresent()) {
            Documento d = existente.get();
            int v = d.registrarCambioOrigen(g.titulo());
            evento(d, DocumentoEvento.Accion.NUEVA_VERSION, "Versión " + v + (g.motivoVersion() == null ? "" : " · " + g.motivoVersion()));
            return;
        }
        Documento d = documentos.save(new Documento(g.pacienteId(), g.episodioId(), g.tipo(), g.titulo(), null, g.fecha(),
                g.servicio(), g.profesional(), Documento.Soporte.DIGITAL, Documento.Origen.GENERADO,
                g.fecha().plusYears(anosRetencion)));
        d.vincularOrigen(g.modulo(), g.referencia(), g.enlace());
        d.registrarCambioOrigen(g.titulo());
        evento(d, DocumentoEvento.Accion.REGISTRADO, "Generado por " + g.modulo().toLowerCase());
    }

    // ---------- Consulta y búsqueda (RFGAC-003) ----------

    @Transactional(readOnly = true)
    public Page<DocumentoResumenDto> buscar(Busqueda b, int pagina, int tamano) {
        Specification<Documento> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (b.pacienteId() != null) ps.add(cb.equal(root.get("pacienteId"), b.pacienteId()));
            if (limpio(b.documentoPaciente()) != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                var pac = sq.from(Paciente.class);
                sq.select(pac.get("id")).where(cb.equal(pac.get("numeroDocumento"), b.documentoPaciente().trim()));
                ps.add(root.get("pacienteId").in(sq));
            }
            if (b.tipo() != null) ps.add(cb.equal(root.get("tipo"), b.tipo()));
            if (b.desde() != null) ps.add(cb.greaterThanOrEqualTo(root.get("fechaDocumento"), b.desde()));
            if (b.hasta() != null) ps.add(cb.lessThanOrEqualTo(root.get("fechaDocumento"), b.hasta()));
            if (limpio(b.profesional()) != null) ps.add(cb.like(cb.lower(root.get("profesional")), contiene(b.profesional())));
            if (limpio(b.servicio()) != null) ps.add(cb.like(cb.lower(root.get("servicio")), contiene(b.servicio())));
            if (b.estado() != null) ps.add(cb.equal(root.get("estado"), b.estado()));
            if (limpio(b.texto()) != null) {
                for (String palabra : b.texto().trim().toLowerCase().split("\\s+")) {
                    String patron = "%" + palabra + "%";
                    ps.add(cb.or(cb.like(cb.lower(root.get("titulo")), patron),
                            cb.like(cb.lower(cb.coalesce(root.get("descripcion"), "")), patron)));
                }
            }
            if (rol() == Rol.ADMISIONES) {
                ps.add(root.get("tipo").in(Arrays.stream(TipoDocumento.values()).filter(TipoDocumento::isAdministrativo).toList()));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<Documento> resultado = documentos.findAll(spec, PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, MAX_PAGINA),
                Sort.by(Sort.Order.desc("fechaDocumento"), Sort.Order.desc("id"))));
        Map<Long, PacienteResumenDto> pacientesPorId = pacientesDe(resultado.getContent().stream().map(Documento::getPacienteId).toList());
        return resultado.map(d -> DocumentoResumenDto.de(d, pacientesPorId.get(d.getPacienteId())));
    }

    @Transactional(readOnly = true)
    public List<DocumentoResumenDto> dePaciente(Long pacienteId) {
        PacienteResumenDto p = PacienteResumenDto.de(pacienteService.buscarEntidad(pacienteId));
        boolean soloAdministrativos = rol() == Rol.ADMISIONES;
        return documentos.findByPacienteIdOrderByFechaDocumentoDescIdDesc(pacienteId).stream()
                .filter(d -> !soloAdministrativos || d.getTipo().isAdministrativo())
                .map(d -> DocumentoResumenDto.de(d, p)).toList();
    }

    /** Ver el detalle queda registrado (RNFGAC-006). */
    @Transactional
    public DocumentoDto obtener(Long id) {
        Documento d = buscar(id);
        exigirTipoPermitido(d.getTipo());
        evento(d, DocumentoEvento.Accion.VER, null);
        return armar(d);
    }

    public record Contenido(String nombre, String tipoMime, byte[] bytes) {
    }

    @Transactional
    public Contenido descargar(Long id, Integer version) {
        Documento d = buscar(id);
        exigirTipoPermitido(d.getTipo());
        if (d.getEstado() == Documento.Estado.ELIMINADO) {
            throw new ReglaNegocioException("DOCUMENTO_ELIMINADO", "El contenido fue eliminado según la política de retención.");
        }
        Archivo a;
        if (version == null) {
            a = Optional.ofNullable(d.getArchivo())
                    .orElseThrow(() -> new ReglaNegocioException("SIN_ARCHIVO", "Este documento no tiene archivo digital."));
        } else {
            a = versiones.findByDocumentoIdAndVersion(id, version).map(DocumentoVersion::getArchivo)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Versión", version.longValue()));
        }
        evento(d, DocumentoEvento.Accion.DESCARGAR, "Versión " + (version == null ? d.getVersionActual() : version));
        return new Contenido(a.getNombre(), a.getTipoMime(), almacen.contenido(a.getId()));
    }

    // ---------- Ciclo de vida y archivo físico (RFGAC-006 / 010) ----------

    @Transactional
    public DocumentoDto cambiarEstado(Long id, Documento.Estado estado, String motivo) {
        Documento d = buscar(id);
        Documento.Estado anterior = d.getEstado();
        d.cambiarEstado(estado, motivo.trim(), LocalDate.now(calendario.getZona()));
        if (estado == Documento.Estado.ELIMINADO) {
            List<Long> ids = versiones.findByDocumentoIdOrderByVersionDesc(id).stream().map(v -> v.getArchivo().getId()).toList();
            almacen.purgar(ids);
            evento(d, DocumentoEvento.Accion.ELIMINADO, "Contenido eliminado (se conservan metadatos y huellas). " + motivo.trim());
        } else {
            evento(d, DocumentoEvento.Accion.CAMBIO_ESTADO, anterior.getDescripcion() + " → " + estado.getDescripcion() + ": " + motivo.trim());
        }
        return armar(d);
    }

    @Transactional
    public DocumentoDto ubicar(Long id, UbicacionRequest r) {
        Documento d = buscar(id);
        d.ubicar(r.sala().trim(), r.estanteria().trim(), limpio(r.seccion()), limpio(r.caja()));
        evento(d, DocumentoEvento.Accion.UBICACION, DocumentoResumenDto.de(d, null).ubicacion());
        return armar(d);
    }

    // ---------- Solicitudes al archivo (RFGAC-008) ----------

    @Transactional
    public SolicitudDto solicitar(SolicitudRequest r) {
        Paciente p = pacienteService.buscarEntidad(r.pacienteId());
        String titulo = null;
        if (r.documentoId() != null) {
            Documento d = buscar(r.documentoId());
            if (!d.getPacienteId().equals(p.getId())) {
                throw new DatosInvalidosException(Map.of("documentoId", "El documento no es de este paciente"));
            }
            titulo = d.getTitulo();
            evento(d, DocumentoEvento.Accion.SOLICITADO, r.motivo().trim());
        }
        String usuario = usuario();
        String nombre = usuarios.findByUsernameIgnoreCase(usuario).map(Usuario::getNombreCompleto).orElse(usuario);
        SolicitudArchivo s = solicitudes.save(new SolicitudArchivo(p.getId(), r.documentoId(), usuario, nombre,
                r.motivo().trim(), r.esUrgente()));
        notificaciones.paraRol(Rol.ARCHIVO, (r.esUrgente() ? "⚠ Solicitud urgente de archivo" : "Solicitud de archivo") + " · "
                + p.getNombreCompleto(), (titulo == null ? "Expediente completo" : titulo) + " — " + nombre, "/documentos/solicitudes");
        return SolicitudDto.de(s, PacienteResumenDto.de(p), titulo);
    }

    @Transactional(readOnly = true)
    public List<SolicitudDto> solicitudesAbiertas() {
        return armarSolicitudes(solicitudes.findByEstadoInOrderByUrgenteDescSolicitadaEnAsc(
                EnumSet.of(SolicitudArchivo.Estado.PENDIENTE, SolicitudArchivo.Estado.ENTREGADA)));
    }

    @Transactional(readOnly = true)
    public List<SolicitudDto> misSolicitudes() {
        return armarSolicitudes(solicitudes.findTop50BySolicitanteIgnoreCaseOrderBySolicitadaEnDesc(usuario()));
    }

    public enum Atencion { ENTREGAR, RECHAZAR, DEVOLVER }

    @Transactional
    public SolicitudDto atender(Long id, Atencion accion, String respuesta) {
        SolicitudArchivo s = solicitudes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Solicitud", id));
        String r = limpio(respuesta);
        switch (accion) {
            case ENTREGAR -> s.entregar(usuario(), r);
            case RECHAZAR -> {
                if (r == null) throw new DatosInvalidosException(Map.of("respuesta", "Explique por qué se rechaza"));
                s.rechazar(usuario(), r);
            }
            case DEVOLVER -> s.registrarDevolucion();
        }
        if (accion != Atencion.DEVOLVER) {
            notificaciones.paraUsuario(s.getSolicitante(), accion == Atencion.ENTREGAR ? "Documento disponible" : "Solicitud rechazada",
                    pacienteService.buscarEntidad(s.getPacienteId()).getNombreCompleto() + (r == null ? "" : ": " + r),
                    "/documentos/solicitudes");
        }
        return armarSolicitudes(List.of(s)).getFirst();
    }

    // ---------- Reportes (RFGAC-011) ----------

    @Transactional(readOnly = true)
    public ReporteDto reporte(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new DatosInvalidosException(Map.of("hasta", "Debe ser posterior a la fecha inicial"));
        }
        Instant ini = desde.atStartOfDay(calendario.getZona()).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(calendario.getZona()).toInstant();
        List<ConteoDto> porTipo = conteos(documentos.contarPorTipo(), k -> TipoDocumento.valueOf(k).getDescripcion());
        List<ConteoDto> porEstado = conteos(documentos.contarPorEstado(), k -> Documento.Estado.valueOf(k).getDescripcion());
        List<ConteoDto> porOrigen = conteos(documentos.contarPorOrigen(), k -> "CARGADO".equals(k) ? "Cargados" : "Generados por los módulos");
        List<ConteoDto> acciones = conteos(eventos.contarPorAccion(ini, fin), EtiquetaAccion::de);
        List<ConteoDto> accesos = eventos.accesosPorUsuario(List.of("VER", "DESCARGAR"), ini, fin).stream().limit(10)
                .map(f -> new ConteoDto((String) f[0], (String) f[0], ((Number) f[1]).longValue())).toList();
        List<SolicitudArchivo> delPeriodo = solicitudes.findBySolicitadaEnGreaterThanEqualAndSolicitadaEnLessThan(ini, fin);
        Map<SolicitudArchivo.Estado, Long> porEstadoSol = delPeriodo.stream()
                .collect(Collectors.groupingBy(SolicitudArchivo::getEstado, Collectors.counting()));
        Double promedio = delPeriodo.stream().filter(s -> s.getEntregadaEn() != null && s.getEstado() != SolicitudArchivo.Estado.RECHAZADA)
                .mapToLong(s -> Duration.between(s.getSolicitadaEn(), s.getEntregadaEn()).toMinutes())
                .average().stream().boxed().findFirst().orElse(null);
        long pendientes = solicitudes.findByEstadoInOrderByUrgenteDescSolicitadaEnAsc(EnumSet.of(SolicitudArchivo.Estado.PENDIENTE)).size();
        return new ReporteDto(desde, hasta, porTipo.stream().mapToLong(ConteoDto::cantidad).sum(), porTipo, porEstado, porOrigen,
                acciones, accesos, delPeriodo.size(),
                porEstadoSol.entrySet().stream().map(e -> new ConteoDto(e.getKey().name(), e.getKey().getDescripcion(), e.getValue())).toList(),
                promedio, pendientes);
    }

    private static List<ConteoDto> conteos(List<Object[]> filas, Function<String, String> etiqueta) {
        return filas.stream().map(f -> {
                    String clave = f[0] instanceof Enum<?> e ? e.name() : String.valueOf(f[0]);
                    return new ConteoDto(clave, etiqueta.apply(clave), ((Number) f[1]).longValue());
                })
                .sorted((a, b) -> Long.compare(b.cantidad(), a.cantidad())).toList();
    }

    private static final class EtiquetaAccion {
        static String de(String accion) {
            return switch (accion) {
                case "REGISTRADO" -> "Registrados";
                case "VER" -> "Consultas";
                case "DESCARGAR" -> "Descargas";
                case "NUEVA_VERSION" -> "Nuevas versiones";
                case "UBICACION" -> "Cambios de ubicación";
                case "CAMBIO_ESTADO" -> "Cambios de estado";
                case "ELIMINADO" -> "Eliminados";
                case "SOLICITADO" -> "Solicitudes";
                default -> accion;
            };
        }
    }

    // ---------- armado y utilidades ----------

    private DocumentoDto armar(Documento d) {
        PacienteResumenDto p = PacienteResumenDto.de(pacienteService.buscarEntidad(d.getPacienteId()));
        return new DocumentoDto(DocumentoResumenDto.de(d, p), p, d.getEpisodioId(), d.getDescripcion(), d.getSala(),
                d.getEstanteria(), d.getSeccion(), d.getCaja(), d.getRetencionHasta(), d.getMotivoEstado(), d.getCreadoPor(),
                d.getCreadoEn(), versiones.findByDocumentoIdOrderByVersionDesc(d.getId()).stream().map(VersionDto::de).toList(),
                eventos.findTop100ByDocumentoIdOrderByFechaDescIdDesc(d.getId()).stream().map(EventoDto::de).toList());
    }

    private List<SolicitudDto> armarSolicitudes(List<SolicitudArchivo> lista) {
        Map<Long, PacienteResumenDto> ps = pacientesDe(lista.stream().map(SolicitudArchivo::getPacienteId).toList());
        Map<Long, String> titulos = documentos.findAllById(lista.stream().map(SolicitudArchivo::getDocumentoId)
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Documento::getId, Documento::getTitulo));
        return lista.stream().map(s -> SolicitudDto.de(s, ps.get(s.getPacienteId()),
                s.getDocumentoId() == null ? null : titulos.get(s.getDocumentoId()))).toList();
    }

    private Map<Long, PacienteResumenDto> pacientesDe(List<Long> ids) {
        return pacientes.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Paciente::getId, PacienteResumenDto::de));
    }

    /** RNFGAC-001: admisiones solo maneja los documentos administrativos (consentimientos, identidad, autorizaciones). */
    private void exigirTipoPermitido(TipoDocumento tipo) {
        if (rol() == Rol.ADMISIONES && !tipo.isAdministrativo()) {
            throw new AccessDeniedException("Admisiones no accede a documentos clínicos");
        }
    }

    private static Rol rol() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return null;
        return a.getAuthorities().stream().map(GrantedAuthority::getAuthority).filter(s -> s.startsWith("ROLE_"))
                .map(s -> s.substring(5)).filter(s -> Arrays.stream(Rol.values()).anyMatch(r -> r.name().equals(s)))
                .map(Rol::valueOf).findFirst().orElse(null);
    }

    private Documento buscar(Long id) {
        return documentos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Documento", id));
    }

    private void evento(Documento d, DocumentoEvento.Accion accion, String detalle) {
        eventos.save(new DocumentoEvento(d.getId(), accion, detalle == null ? null
                : detalle.length() > 500 ? detalle.substring(0, 497) + "…" : detalle, usuario()));
    }

    private String usuario() {
        return auditor.getCurrentAuditor().orElse("sistema");
    }

    private static String contiene(String s) {
        return "%" + s.trim().toLowerCase() + "%";
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
