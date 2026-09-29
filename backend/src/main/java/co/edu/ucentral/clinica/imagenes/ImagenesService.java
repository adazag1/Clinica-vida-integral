package co.edu.ucentral.clinica.imagenes;

import co.edu.ucentral.clinica.agenda.CalculadoraDisponibilidad;
import co.edu.ucentral.clinica.archivos.AlmacenArchivos;
import co.edu.ucentral.clinica.archivos.Archivo;
import co.edu.ucentral.clinica.comun.excepciones.DatosInvalidosException;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.comun.seguridad.Reautenticador;
import co.edu.ucentral.clinica.documentos.DocumentoGenerado;
import co.edu.ucentral.clinica.documentos.TipoDocumento;
import co.edu.ucentral.clinica.historia.Antecedente;
import co.edu.ucentral.clinica.historia.AntecedenteRepository;
import co.edu.ucentral.clinica.historia.Consulta;
import co.edu.ucentral.clinica.historia.ConsultaDiagnostico;
import co.edu.ucentral.clinica.historia.ConsultaRepository;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.ContextoClinicoDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.EstudioDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.EstudioResumenDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.EventoDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.ImagenDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.InformeDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.MetricaDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.PlantillaDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.RadiologoDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.SegundaOpinionDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.TipoEstudioDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.FirmaRequest;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.InformeRequest;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.OrdenEstudioRequest;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.TipoEstudioRequest;
import co.edu.ucentral.clinica.notificaciones.NotificacionService;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;
import co.edu.ucentral.clinica.seguridad.Rol;
import co.edu.ucentral.clinica.seguridad.Usuario;
import co.edu.ucentral.clinica.seguridad.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AuditorAware;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Imágenes diagnósticas: orden → adquisición → lectura e informe → médico (RFRAD-002 a 012). */
@Service
public class ImagenesService {

    public enum Vista { PENDIENTES, MIOS, INFORMADOS }

    private static final Set<EstadoEstudio> PENDIENTES =
            EnumSet.of(EstadoEstudio.ORDENADO, EstadoEstudio.ADQUIRIDO, EstadoEstudio.EN_LECTURA, EstadoEstudio.BLOQUEADO);
    private static final int MAX_IMAGENES = 40;

    private final TipoEstudioRepository tipos;
    private final EstudioImagenRepository estudios;
    private final ImagenEstudioRepository imagenes;
    private final SegundaOpinionRepository opiniones;
    private final EventoEstudioRepository eventos;
    private final ConsultaRepository consultas;
    private final AntecedenteRepository antecedentes;
    private final UsuarioRepository usuarios;
    private final PacienteService pacienteService;
    private final AlmacenArchivos almacen;
    private final Reautenticador reautenticador;
    private final NotificacionService notificaciones;
    private final ApplicationEventPublisher publicador;
    private final CalculadoraDisponibilidad calendario;
    private final AuditorAware<String> auditor;
    private final String visorPacs;

    public ImagenesService(TipoEstudioRepository tipos, EstudioImagenRepository estudios, ImagenEstudioRepository imagenes,
                           SegundaOpinionRepository opiniones, EventoEstudioRepository eventos,
                           ConsultaRepository consultas, AntecedenteRepository antecedentes, UsuarioRepository usuarios,
                           PacienteService pacienteService, AlmacenArchivos almacen, Reautenticador reautenticador,
                           NotificacionService notificaciones, ApplicationEventPublisher publicador,
                           CalculadoraDisponibilidad calendario, AuditorAware<String> auditor,
                           @Value("${app.pacs.visor-url:}") String visorPacs) {
        this.tipos = tipos;
        this.estudios = estudios;
        this.imagenes = imagenes;
        this.opiniones = opiniones;
        this.eventos = eventos;
        this.consultas = consultas;
        this.antecedentes = antecedentes;
        this.usuarios = usuarios;
        this.pacienteService = pacienteService;
        this.almacen = almacen;
        this.reautenticador = reautenticador;
        this.notificaciones = notificaciones;
        this.publicador = publicador;
        this.calendario = calendario;
        this.auditor = auditor;
        this.visorPacs = visorPacs == null ? "" : visorPacs.trim();
    }

    // ---------- Catálogo y plantillas (RFRAD-006) ----------

    @Transactional(readOnly = true)
    public List<TipoEstudioDto> catalogo() {
        return tipos.findAllByOrderByModalidadAscNombreAsc().stream().map(TipoEstudioDto::de).toList();
    }

    @Transactional
    public TipoEstudioDto configurarTipo(Long id, TipoEstudioRequest r) {
        TipoEstudio t = tipos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Tipo de estudio", id));
        if (r.tatUrgenteMin() > r.tatHospitalizadoMin() || r.tatHospitalizadoMin() > r.tatAmbulatorioMin()) {
            throw new DatosInvalidosException(Map.of("tatUrgenteMin", "El tiempo de un urgente debe ser el menor y el de un ambulatorio el mayor"));
        }
        t.configurar(r.tatUrgenteMin(), r.tatHospitalizadoMin(), r.tatAmbulatorioMin(), limpio(r.plantillaHallazgos()),
                limpio(r.plantillaImpresion()), limpio(r.plantillaRecomendaciones()), usuario());
        return TipoEstudioDto.de(t);
    }

    @Transactional(readOnly = true)
    public List<RadiologoDto> radiologos() {
        return usuarios.findByRolAndActivoTrueOrderByNombreCompleto(Rol.RADIOLOGIA).stream()
                .map(u -> new RadiologoDto(u.getUsername(), u.getNombreCompleto())).toList();
    }

    // ---------- Orden del médico ----------

    @Transactional
    public EstudioDto ordenar(OrdenEstudioRequest r) {
        Paciente p = pacienteService.buscarEntidad(r.pacienteId());
        String dx = limpio(r.diagnosticoPresuntivo());
        if (r.consultaId() != null) {
            Consulta c = consultas.findById(r.consultaId()).filter(x -> x.getPacienteId().equals(p.getId()))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Consulta", r.consultaId()));
            if (dx == null) {
                // RFRAD-003: si el médico no lo escribe, se toma el diagnóstico principal de la consulta
                dx = c.getDiagnosticos().stream().filter(ConsultaDiagnostico::isPrincipal).findFirst()
                        .map(d -> d.getDiagnostico().getCodigo() + " " + d.getDiagnostico().getNombre()).orElse(null);
            }
        }
        TipoEstudio tipo = tipos.findById(r.tipoEstudioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de estudio", r.tipoEstudioId()));
        String medico = usuario();
        String nombre = nombreDe(medico);
        String numero = "IMG-%d-%06d".formatted(Year.now().getValue(), estudios.siguienteNumero());
        EstudioImagen e = estudios.save(new EstudioImagen(numero, p.getId(), r.consultaId(), tipo, r.prioridad(),
                r.motivo().trim(), dx, medico, nombre));
        evento(e, "ORDENADO", tipo.getNombre() + " · " + r.prioridad().getDescripcion());
        if (r.prioridad() == PrioridadEstudio.URGENTE) {
            notificaciones.paraRol(Rol.RADIOLOGIA, "Estudio urgente " + numero, p.getNombreCompleto() + ": " + tipo.getNombre(),
                    enlace(e));
        }
        return armar(e);
    }

    // ---------- Lista de trabajo (RFRAD-002 / 004) ----------

    @Transactional(readOnly = true)
    public List<EstudioResumenDto> listaTrabajo(Vista vista, Modalidad modalidad, PrioridadEstudio prioridad) {
        Instant ahora = Instant.now();
        String yo = usuario();
        List<EstudioImagen> lista = vista == Vista.INFORMADOS
                ? estudios.findByEstadoAndInformadoEnAfterOrderByInformadoEnDesc(EstadoEstudio.INFORMADO, ahora.minus(Duration.ofDays(3)))
                : estudios.findByEstadoInOrderByOrdenadoEn(PENDIENTES);
        Set<Long> conOpinionMia = opiniones.findByAsignadaAIgnoreCaseAndRespondidaEnIsNull(yo).stream()
                .map(SegundaOpinion::getEstudioId).collect(Collectors.toSet());
        if (vista == Vista.MIOS) {
            // Mis lecturas en curso, los pendientes sin asignar y las segundas lecturas que me pidieron
            List<EstudioImagen> mias = new ArrayList<>(lista.stream()
                    .filter(e -> yo.equalsIgnoreCase(e.getAsignadoA()) || (e.getAsignadoA() == null && e.getEstado() == EstadoEstudio.ADQUIRIDO))
                    .toList());
            conOpinionMia.stream().filter(id -> mias.stream().noneMatch(e -> e.getId().equals(id)))
                    .forEach(id -> estudios.findById(id).ifPresent(mias::add));
            lista = mias;
        }
        Comparator<EstudioImagen> orden = vista == Vista.INFORMADOS
                ? Comparator.comparing(EstudioImagen::getInformadoEn).reversed()
                : Comparator.comparing((EstudioImagen e) -> e.getEstado() == EstadoEstudio.BLOQUEADO)
                .thenComparing(EstudioImagen::getPrioridad)
                .thenComparing(e -> !e.tatVencido(ahora))
                .thenComparing(EstudioImagen::getOrdenadoEn);
        return lista.stream()
                .filter(e -> modalidad == null || e.getTipoEstudio().getModalidad() == modalidad)
                .filter(e -> prioridad == null || e.getPrioridad() == prioridad)
                .sorted(orden)
                .map(e -> resumen(e, ahora, conOpinionMia.contains(e.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EstudioResumenDto> dePaciente(Long pacienteId) {
        pacienteService.buscarEntidad(pacienteId);
        Instant ahora = Instant.now();
        return estudios.findByPacienteIdOrderByOrdenadoEnDesc(pacienteId).stream().map(e -> resumen(e, ahora, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<EstudioResumenDto> deConsulta(Long consultaId) {
        Instant ahora = Instant.now();
        return estudios.findByConsultaIdOrderByOrdenadoEnDesc(consultaId).stream().map(e -> resumen(e, ahora, false)).toList();
    }

    @Transactional(readOnly = true)
    public EstudioDto obtener(Long id) {
        return armar(buscar(id));
    }

    // ---------- Adquisición e imágenes (RFRAD-009 / 010) ----------

    @Transactional
    public EstudioDto cargarImagenes(Long id, List<MultipartFile> archivos, String uid) {
        EstudioImagen e = buscar(id);
        if ((archivos == null || archivos.isEmpty()) && limpio(uid) == null) {
            throw new DatosInvalidosException(Map.of("archivo", "Adjunte al menos una imagen o el identificador del estudio en el PACS"));
        }
        e.adquirir(usuario(), limpio(uid));
        int orden = e.getImagenes().stream().mapToInt(ImagenEstudio::getOrden).max().orElse(0);
        List<MultipartFile> lista = archivos == null ? List.of() : archivos;
        if (e.getImagenes().size() + lista.size() > MAX_IMAGENES) {
            throw new DatosInvalidosException(Map.of("archivo", "Máximo " + MAX_IMAGENES + " imágenes por estudio; las demás se consultan en el PACS"));
        }
        for (MultipartFile f : lista) {
            Archivo a = almacen.guardar(f, AlmacenArchivos.IMAGENES, usuario());
            e.getImagenes().add(new ImagenEstudio(e, a, ++orden));
        }
        evento(e, "IMAGENES", lista.size() + " imagen(es)" + (limpio(uid) == null ? "" : " · UID " + uid.trim()));
        estudios.flush();
        return armar(e);
    }

    @Transactional
    public EstudioDto marcarClave(Long id, Long imagenId, boolean clave, String descripcion) {
        EstudioImagen e = buscar(id);
        e.exigirNoBloqueado();
        ImagenEstudio img = e.getImagenes().stream().filter(i -> i.getId().equals(imagenId)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Imagen", imagenId));
        img.marcar(clave, limpio(descripcion));
        return armar(e);
    }

    public record ContenidoImagen(String nombre, String tipoMime, byte[] bytes) {
    }

    @Transactional(readOnly = true)
    public ContenidoImagen imagen(Long id, Long imagenId) {
        ImagenEstudio img = imagenes.findById(imagenId).filter(i -> i.getEstudio().getId().equals(id))
                .orElseThrow(() -> new RecursoNoEncontradoException("Imagen", imagenId));
        return new ContenidoImagen(img.getArchivo().getNombre(), img.getArchivo().getTipoMime(),
                almacen.contenido(img.getArchivo().getId()));
    }

    // ---------- Informe (RFRAD-006 / 008 / RNFRAD-002) ----------

    /** Guarda el borrador. El primero en escribir toma el estudio; nadie más edita su borrador. */
    @Transactional
    public EstudioDto guardarInforme(Long id, InformeRequest r) {
        EstudioImagen e = buscar(id);
        String yo = usuario();
        Optional<InformeImagen> borrador = e.borrador();
        if (borrador.isPresent()) {
            if (!borrador.get().getAutor().equalsIgnoreCase(yo)) {
                throw new ReglaNegocioException("INFORME_AJENO", "El borrador lo está escribiendo " + borrador.get().getAutorNombre() + ".");
            }
            e.exigirNoBloqueado();
            borrador.get().editar(limpio(r.hallazgos()), limpio(r.impresion()), limpio(r.recomendaciones()));
        } else {
            if (e.getEstado() == EstadoEstudio.INFORMADO) {
                throw new ReglaNegocioException("ESTUDIO_INFORMADO", "El estudio ya está informado; para corregirlo use «Enmendar».");
            }
            e.iniciarLectura(yo);
            e.getInformes().add(new InformeImagen(e, e.siguienteVersion(), limpio(r.hallazgos()), limpio(r.impresion()),
                    limpio(r.recomendaciones()), null, yo, nombreDe(yo)));
            evento(e, "LECTURA", "Inicia la lectura");
        }
        estudios.flush();
        return armar(e);
    }

    /** RNFRAD-002: una corrección es una versión nueva con motivo; la firmada se conserva. */
    @Transactional
    public EstudioDto enmendar(Long id, String motivo) {
        EstudioImagen e = buscar(id);
        if (e.getEstado() != EstadoEstudio.INFORMADO) {
            throw new ReglaNegocioException("ESTUDIO_NO_INFORMADO", "Solo se enmiendan informes ya firmados.");
        }
        InformeImagen firmado = e.vigente().orElseThrow();
        String yo = usuario();
        e.iniciarLectura(yo);
        e.getInformes().add(new InformeImagen(e, e.siguienteVersion(), firmado.getHallazgos(), firmado.getImpresion(),
                firmado.getRecomendaciones(), motivo.trim(), yo, nombreDe(yo)));
        evento(e, "ENMIENDA", motivo.trim());
        estudios.flush();
        return armar(e);
    }

    @Transactional(noRollbackFor = ReglaNegocioException.class) // conserva el conteo de contraseñas erradas
    public EstudioDto firmar(Long id, FirmaRequest r) {
        EstudioImagen e = buscar(id);
        e.exigirNoBloqueado();
        String yo = usuario();
        InformeImagen inf = e.borrador()
                .orElseThrow(() -> new ReglaNegocioException("SIN_BORRADOR", "No hay un informe en borrador para firmar."));
        if (!inf.getAutor().equalsIgnoreCase(yo)) {
            throw new ReglaNegocioException("INFORME_AJENO", "Solo " + inf.getAutorNombre() + " puede firmar este informe.");
        }
        List<String> faltan = new ArrayList<>();
        if (inf.getHallazgos() == null) faltan.add("los hallazgos");
        if (inf.getImpresion() == null) faltan.add("la impresión diagnóstica");
        if (e.getImagenes().isEmpty() && e.getStudyInstanceUid() == null) faltan.add("las imágenes del estudio");
        if (r.hayCritico() && limpio(r.criticoReceptor()) == null) faltan.add("a quién se comunicó el hallazgo crítico");
        if (!faltan.isEmpty()) {
            throw new ReglaNegocioException("INFORME_INCOMPLETO", "No se puede firmar: faltan " + String.join(", ", faltan) + ".");
        }
        reautenticador.confirmar(yo, r.password());
        inf.firmar();
        boolean primeraVez = e.getInformadoEn() == null;
        e.informar(yo);
        if (r.hayCritico()) {
            e.registrarCritico(r.criticoReceptor().trim(), Optional.ofNullable(limpio(r.criticoMedio())).orElse("Llamada"));
        }
        evento(e, "FIRMADO", "Versión " + inf.getVersion() + (r.hayCritico() ? " · hallazgo crítico informado a " + r.criticoReceptor().trim() : ""));

        Paciente p = pacienteService.buscarEntidad(e.getPacienteId());
        String titulo = r.hayCritico() ? "⚠ Hallazgo crítico · " + p.getNombreCompleto()
                : (primeraVez ? "Informe de imágenes disponible " : "Informe de imágenes corregido ") + e.getNumero();
        notificaciones.paraUsuario(e.getMedico(), titulo, e.getTipoEstudio().getNombre() + ": " + inf.getImpresion(), enlace(e));
        publicador.publishEvent(new DocumentoGenerado(e.getPacienteId(), null, TipoDocumento.INFORME_RADIOLOGIA,
                e.getTipoEstudio().getNombre() + " · " + e.getNumero(), "Imágenes diagnósticas", inf.getAutorNombre(),
                "IMAGENES", String.valueOf(e.getId()), enlace(e), LocalDate.now(calendario.getZona()), inf.getMotivoCambio()));
        return armar(e);
    }

    // ---------- Asignación y segunda opinión (RFRAD-002 / 007) ----------

    @Transactional
    public EstudioDto asignar(Long id, String radiologo) {
        EstudioImagen e = buscar(id);
        Usuario u = radiologo(radiologo);
        e.asignar(u.getUsername());
        evento(e, "ASIGNADO", u.getNombreCompleto());
        if (!u.getUsername().equalsIgnoreCase(usuario())) {
            notificaciones.paraUsuario(u.getUsername(), "Estudio asignado " + e.getNumero(), e.getTipoEstudio().getNombre(), enlace(e));
        }
        return armar(e);
    }

    @Transactional
    public EstudioDto pedirSegundaOpinion(Long id, String asignadaA, String pregunta) {
        EstudioImagen e = buscar(id);
        e.exigirNoBloqueado();
        Usuario u = radiologo(asignadaA);
        if (u.getUsername().equalsIgnoreCase(usuario())) {
            throw new ReglaNegocioException("OPINION_PROPIA", "La segunda lectura la debe hacer otro radiólogo.");
        }
        opiniones.save(new SegundaOpinion(e.getId(), usuario(), u.getUsername(), pregunta.trim()));
        evento(e, "SEGUNDA_OPINION", "Solicitada a " + u.getNombreCompleto());
        notificaciones.paraUsuario(u.getUsername(), "Segunda lectura solicitada " + e.getNumero(), pregunta.trim(), enlace(e));
        return armar(e);
    }

    @Transactional
    public EstudioDto responderSegundaOpinion(Long id, Long opinionId, SegundaOpinion.Concordancia concordancia, String comentario) {
        EstudioImagen e = buscar(id);
        SegundaOpinion s = opiniones.findById(opinionId).filter(o -> o.getEstudioId().equals(id))
                .orElseThrow(() -> new RecursoNoEncontradoException("Segunda lectura", opinionId));
        if (concordancia != SegundaOpinion.Concordancia.CONCORDANTE && limpio(comentario) == null) {
            throw new DatosInvalidosException(Map.of("comentario", "Explique la discrepancia"));
        }
        s.responder(usuario(), concordancia, limpio(comentario));
        evento(e, "SEGUNDA_OPINION", concordancia.getDescripcion());
        notificaciones.paraUsuario(s.getSolicitadaPor(), (concordancia == SegundaOpinion.Concordancia.DISCREPANCIA_MAYOR ? "⚠ " : "")
                + "Segunda lectura " + e.getNumero(), concordancia.getDescripcion() + (limpio(comentario) == null ? "" : ": " + comentario.trim()), enlace(e));
        return armar(e);
    }

    // ---------- Bloqueo por identificación (RFRAD-011) ----------

    @Transactional
    public EstudioDto bloquear(Long id, String motivo) {
        EstudioImagen e = buscar(id);
        e.bloquear(motivo.trim(), usuario());
        evento(e, "BLOQUEADO", motivo.trim());
        String detalle = pacienteService.buscarEntidad(e.getPacienteId()).getNombreCompleto() + " · " + e.getNumero() + ": " + motivo.trim();
        notificaciones.paraRol(Rol.ADMISIONES, "Estudio bloqueado por identificación", detalle, enlace(e));
        notificaciones.paraUsuario(e.getMedico(), "Estudio bloqueado por identificación", detalle, enlace(e));
        return armar(e);
    }

    @Transactional
    public EstudioDto desbloquear(Long id, String resolucion) {
        EstudioImagen e = buscar(id);
        e.desbloquear();
        evento(e, "DESBLOQUEADO", resolucion.trim());
        return armar(e);
    }

    // ---------- Métricas (RFRAD-012) y alertas de tiempo ----------

    @Transactional(readOnly = true)
    public List<MetricaDto> metricas(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new DatosInvalidosException(Map.of("hasta", "Debe ser posterior a la fecha inicial"));
        }
        Instant ini = desde.atStartOfDay(calendario.getZona()).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(calendario.getZona()).toInstant();
        Instant ahora = Instant.now();
        Map<TipoEstudio, List<EstudioImagen>> informados = estudios.findByInformadoEnGreaterThanEqualAndInformadoEnLessThan(ini, fin)
                .stream().collect(Collectors.groupingBy(EstudioImagen::getTipoEstudio, LinkedHashMap::new, Collectors.toList()));
        Map<TipoEstudio, List<EstudioImagen>> pendientes = estudios.findByEstadoInOrderByOrdenadoEn(PENDIENTES).stream()
                .collect(Collectors.groupingBy(EstudioImagen::getTipoEstudio));
        List<MetricaDto> filas = new ArrayList<>();
        for (TipoEstudio t : tipos.findAllByOrderByModalidadAscNombreAsc()) {
            List<EstudioImagen> hechos = informados.getOrDefault(t, List.of());
            List<EstudioImagen> abiertos = pendientes.getOrDefault(t, List.of());
            if (hechos.isEmpty() && abiertos.isEmpty()) continue;
            List<Long> minutos = hechos.stream().map(e -> Duration.between(e.getOrdenadoEn(), e.getInformadoEn()).toMinutes()).sorted().toList();
            Double lectura = hechos.stream().filter(e -> e.getAdquiridoEn() != null)
                    .mapToLong(e -> Duration.between(e.getAdquiridoEn(), e.getInformadoEn()).toMinutes()).average().stream().boxed()
                    .findFirst().orElse(null);
            long enTat = hechos.stream().filter(e -> !e.getInformadoEn().isAfter(e.limiteTat())).count();
            filas.add(new MetricaDto(t.getNombre(), t.getModalidad().getDescripcion(), hechos.size(),
                    minutos.isEmpty() ? null : minutos.stream().mapToLong(Long::longValue).average().orElse(0),
                    mediana(minutos), lectura, hechos.isEmpty() ? null : Math.round(1000.0 * enTat / hechos.size()) / 10.0,
                    abiertos.size(), abiertos.stream().filter(e -> e.tatVencido(ahora)).count()));
        }
        return filas;
    }

    private static Double mediana(List<Long> ordenados) {
        if (ordenados.isEmpty()) return null;
        int n = ordenados.size();
        return n % 2 == 1 ? ordenados.get(n / 2).doubleValue() : (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2.0;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT3M")
    @Transactional
    public void alertarTatVencidos() {
        Instant ahora = Instant.now();
        estudios.findByEstadoInOrderByOrdenadoEn(EnumSet.of(EstadoEstudio.ORDENADO, EstadoEstudio.ADQUIRIDO, EstadoEstudio.EN_LECTURA))
                .stream().filter(e -> !e.isTatAlertado() && e.tatVencido(ahora))
                .forEach(e -> {
                    e.marcarTatAlertado();
                    notificaciones.paraRol(Rol.RADIOLOGIA, "Tiempo de respuesta excedido " + e.getNumero(),
                            e.getTipoEstudio().getNombre() + " · " + e.getPrioridad().getDescripcion() + " · "
                                    + e.getEstado().getDescripcion(), enlace(e));
                });
    }

    // ---------- armado ----------

    private EstudioResumenDto resumen(EstudioImagen e, Instant ahora, boolean opinionPendiente) {
        return new EstudioResumenDto(e.getId(), e.getNumero(), PacienteResumenDto.de(pacienteService.buscarEntidad(e.getPacienteId())),
                e.getTipoEstudio().getNombre(), e.getTipoEstudio().getModalidad().name(),
                e.getTipoEstudio().getModalidad().getDescripcion(), e.getPrioridad().name(), e.getPrioridad().getDescripcion(),
                e.getEstado().name(), e.getEstado().getDescripcion(), e.getOrdenadoEn(), e.limiteTat(), e.tatVencido(ahora),
                e.getAsignadoA(), e.getMedicoNombre(), e.getImagenes().size(), e.isCritico(), opinionPendiente,
                e.getInformadoEn(), e.vigente().map(InformeImagen::getImpresion).orElse(null));
    }

    private EstudioDto armar(EstudioImagen e) {
        Instant ahora = Instant.now();
        List<SegundaOpinion> ops = opiniones.findByEstudioIdOrderBySolicitadaEn(e.getId());
        String yo = usuario();
        boolean opinionMia = ops.stream().anyMatch(o -> o.isPendiente() && o.getAsignadaA().equalsIgnoreCase(yo));
        TipoEstudio t = e.getTipoEstudio();
        String urlPacs = visorPacs.isEmpty() || e.getStudyInstanceUid() == null ? null : visorPacs.replace("{uid}", e.getStudyInstanceUid());
        List<EstudioResumenDto> previos = estudios.findByPacienteIdOrderByOrdenadoEnDesc(e.getPacienteId()).stream()
                .filter(x -> !x.getId().equals(e.getId())).map(x -> resumen(x, ahora, false)).toList();
        return new EstudioDto(resumen(e, ahora, opinionMia), e.getConsultaId(), e.getMotivo(), e.getDiagnosticoPresuntivo(),
                e.getMedico(), e.getAdquiridoEn(), e.getAdquiridoPor(), e.getStudyInstanceUid(), urlPacs, e.getBloqueoMotivo(),
                e.getBloqueadoPor(), e.getBloqueadoEn(), e.getCriticoReceptor(), e.getCriticoMedio(), e.getCriticoNotificadoEn(),
                e.getImagenes().stream().map(ImagenDto::de).toList(),
                e.borrador().map(InformeDto::de).orElse(null), e.vigente().map(InformeDto::de).orElse(null),
                e.getInformes().stream().filter(i -> i.getEstado() == InformeImagen.Estado.FIRMADO)
                        .sorted(Comparator.comparing(InformeImagen::getVersion).reversed()).map(InformeDto::de).toList(),
                new PlantillaDto(t.getPlantillaHallazgos(), t.getPlantillaImpresion(), t.getPlantillaRecomendaciones()),
                ops.stream().map(o -> SegundaOpinionDto.de(o, nombreDe(o.getAsignadaA()))).toList(),
                eventos.findByEstudioIdOrderByFechaAscIdAsc(e.getId()).stream().map(EventoDto::de).toList(),
                previos, contexto(e));
    }

    /** RFRAD-003: motivo, diagnósticos, alergias y antecedentes relevantes, sin salir del visor. */
    private ContextoClinicoDto contexto(EstudioImagen e) {
        Optional<Consulta> c = e.getConsultaId() == null ? Optional.empty() : consultas.findById(e.getConsultaId());
        List<String> dx = c.map(x -> x.getDiagnosticos().stream()
                .map(d -> d.getDiagnostico().getCodigo() + " " + d.getDiagnostico().getNombre()).toList()).orElse(List.of());
        List<Antecedente> activos = antecedentes.findByPacienteIdAndActivoTrue(e.getPacienteId());
        List<String> alergias = activos.stream().filter(a -> a.getTipo() == Antecedente.Tipo.ALERGIA)
                .map(a -> a.getDescripcion() + (a.getSeveridad() == null ? "" : " (" + a.getSeveridad().name().toLowerCase() + ")")).toList();
        List<String> otros = activos.stream()
                .filter(a -> a.getTipo() == Antecedente.Tipo.PATOLOGICO || a.getTipo() == Antecedente.Tipo.QUIRURGICO)
                .map(a -> a.getTipo().getDescripcion() + ": " + a.getDescripcion()).toList();
        return new ContextoClinicoDto(c.map(Consulta::getMotivoConsulta).orElse(null), dx, alergias, otros);
    }

    private Usuario radiologo(String username) {
        return usuarios.findByUsernameIgnoreCase(username.trim()).filter(u -> u.getRol() == Rol.RADIOLOGIA && u.isActivo())
                .orElseThrow(() -> new DatosInvalidosException(Map.of("usuario", "Seleccione un radiólogo activo")));
    }

    private String nombreDe(String username) {
        return usuarios.findByUsernameIgnoreCase(username).map(Usuario::getNombreCompleto).orElse(username);
    }

    private EstudioImagen buscar(Long id) {
        return estudios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Estudio", id));
    }

    private void evento(EstudioImagen e, String accion, String detalle) {
        eventos.save(new EventoEstudio(e.getId(), accion, detalle, usuario()));
    }

    private static String enlace(EstudioImagen e) {
        return "/imagenes/estudios/" + e.getId();
    }

    private String usuario() {
        return auditor.getCurrentAuditor().orElse("sistema");
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
