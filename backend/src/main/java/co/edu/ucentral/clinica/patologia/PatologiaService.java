package co.edu.ucentral.clinica.patologia;

import co.edu.ucentral.clinica.agenda.CalculadoraDisponibilidad;
import co.edu.ucentral.clinica.comun.excepciones.DatosInvalidosException;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.comun.seguridad.Reautenticador;
import co.edu.ucentral.clinica.documentos.DocumentoGenerado;
import co.edu.ucentral.clinica.documentos.TipoDocumento;
import co.edu.ucentral.clinica.historia.Antecedente;
import co.edu.ucentral.clinica.historia.AntecedenteRepository;
import co.edu.ucentral.clinica.historia.Consulta;
import co.edu.ucentral.clinica.historia.ConsultaRepository;
import co.edu.ucentral.clinica.notificaciones.NotificacionService;
import co.edu.ucentral.clinica.pacientes.Normalizador;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;
import co.edu.ucentral.clinica.patologia.dto.PatologiaDtos.CasoDto;
import co.edu.ucentral.clinica.patologia.dto.PatologiaDtos.CasoResumenDto;
import co.edu.ucentral.clinica.patologia.dto.PatologiaDtos.CustodiaDto;
import co.edu.ucentral.clinica.patologia.dto.PatologiaDtos.EventoDto;
import co.edu.ucentral.clinica.patologia.dto.PatologiaDtos.PlantillaDto;
import co.edu.ucentral.clinica.patologia.dto.PatologiaDtos.VersionDto;
import co.edu.ucentral.clinica.patologia.dto.PatologiaSolicitudes.CampoRequest;
import co.edu.ucentral.clinica.patologia.dto.PatologiaSolicitudes.CustodiaRequest;
import co.edu.ucentral.clinica.patologia.dto.PatologiaSolicitudes.PlantillaRequest;
import co.edu.ucentral.clinica.patologia.dto.PatologiaSolicitudes.RecepcionRequest;
import co.edu.ucentral.clinica.patologia.dto.PatologiaSolicitudes.SolicitudCasoRequest;
import co.edu.ucentral.clinica.seguridad.Rol;
import co.edu.ucentral.clinica.seguridad.Usuario;
import co.edu.ucentral.clinica.seguridad.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Patología: casos, recepción segura, macroscopía y microscopía estructuradas, informes versionados y custodia. */
@Service
public class PatologiaService {

    public enum Vista { PENDIENTES, INFORMADOS }

    private static final Set<EstadoCaso> PENDIENTES = EnumSet.of(EstadoCaso.SOLICITADO, EstadoCaso.RECIBIDO, EstadoCaso.MACROSCOPIA);

    private final CasoPatologiaRepository casos;
    private final InformePatologiaRepository informes;
    private final CustodiaPatologiaRepository custodias;
    private final EventoCasoPatologiaRepository eventos;
    private final PlantillaPatologiaRepository plantillas;
    private final ConsultaRepository consultas;
    private final AntecedenteRepository antecedentes;
    private final UsuarioRepository usuarios;
    private final PacienteService pacienteService;
    private final Reautenticador reautenticador;
    private final NotificacionService notificaciones;
    private final ApplicationEventPublisher publicador;
    private final CalculadoraDisponibilidad calendario;
    private final AuditorAware<String> auditor;
    private final int anosRetencion;

    public PatologiaService(CasoPatologiaRepository casos, InformePatologiaRepository informes,
                            CustodiaPatologiaRepository custodias, EventoCasoPatologiaRepository eventos,
                            PlantillaPatologiaRepository plantillas, ConsultaRepository consultas,
                            AntecedenteRepository antecedentes, UsuarioRepository usuarios, PacienteService pacienteService,
                            Reautenticador reautenticador, NotificacionService notificaciones,
                            ApplicationEventPublisher publicador, CalculadoraDisponibilidad calendario,
                            AuditorAware<String> auditor, @Value("${app.patologia.retencion-anos:15}") int anosRetencion) {
        this.casos = casos;
        this.informes = informes;
        this.custodias = custodias;
        this.eventos = eventos;
        this.plantillas = plantillas;
        this.consultas = consultas;
        this.antecedentes = antecedentes;
        this.usuarios = usuarios;
        this.pacienteService = pacienteService;
        this.reautenticador = reautenticador;
        this.notificaciones = notificaciones;
        this.publicador = publicador;
        this.calendario = calendario;
        this.auditor = auditor;
        this.anosRetencion = anosRetencion;
    }

    // ---------- Solicitud (RFPAT-002 / 003) ----------

    @Transactional
    public CasoDto solicitar(SolicitudCasoRequest r) {
        Paciente p = pacienteService.buscarEntidad(r.pacienteId());
        if (r.consultaId() != null) {
            consultas.findById(r.consultaId()).filter(c -> c.getPacienteId().equals(p.getId()))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Consulta", r.consultaId()));
        }
        // RFPAT-002: la secuencia es global y nunca repite; el prefijo solo ayuda a leer el tipo
        String numero = "%s%02d-%06d".formatted(r.tipo().getPrefijo(), Year.now().getValue() % 100, casos.siguienteNumero());
        String medico = usuario();
        CasoPatologia c = casos.save(new CasoPatologia(numero, p.getId(), r.consultaId(), r.tipo(), r.prioridad(),
                r.sitioAnatomico().trim(), limpio(r.datosClinicos()), r.numeroMuestras(), medico, nombreDe(medico)));
        evento(c, "SOLICITADO", r.tipo().getDescripcion() + " · " + r.sitioAnatomico().trim() + " · " + r.prioridad().getDescripcion());
        if (r.prioridad() != PrioridadCaso.RUTINA) {
            notificaciones.paraRol(Rol.PATOLOGIA, (r.prioridad() == PrioridadCaso.INTRAOPERATORIO ? "⚠ Intraoperatorio " : "Caso urgente ")
                    + numero, p.getNombreCompleto() + ": " + r.sitioAnatomico().trim(), enlace(c));
        }
        return armar(c);
    }

    // ---------- Cola (RFPAT-003) ----------

    @Transactional(readOnly = true)
    public List<CasoResumenDto> cola(Vista vista, TipoCaso tipo, PrioridadCaso prioridad) {
        Instant ahora = Instant.now();
        List<CasoPatologia> lista = vista == Vista.INFORMADOS
                ? casos.findByEstadoAndValidadoEnAfterOrderByValidadoEnDesc(EstadoCaso.INFORMADO, ahora.minus(Duration.ofDays(7)))
                : casos.findByEstadoInOrderBySolicitadoEn(PENDIENTES);
        Comparator<CasoPatologia> orden = vista == Vista.INFORMADOS
                ? Comparator.comparing(CasoPatologia::getValidadoEn).reversed()
                : Comparator.comparing(CasoPatologia::getPrioridad).thenComparing(c -> !c.vencido(ahora))
                .thenComparing(CasoPatologia::getSolicitadoEn);
        return lista.stream().filter(c -> tipo == null || c.getTipo() == tipo)
                .filter(c -> prioridad == null || c.getPrioridad() == prioridad)
                .sorted(orden).map(c -> resumen(c, ahora)).toList();
    }

    @Transactional(readOnly = true)
    public List<CasoResumenDto> dePaciente(Long pacienteId) {
        pacienteService.buscarEntidad(pacienteId);
        Instant ahora = Instant.now();
        return casos.findByPacienteIdOrderBySolicitadoEnDesc(pacienteId).stream().map(c -> resumen(c, ahora)).toList();
    }

    @Transactional(readOnly = true)
    public List<CasoResumenDto> deConsulta(Long consultaId) {
        Instant ahora = Instant.now();
        return casos.findByConsultaIdOrderBySolicitadoEnDesc(consultaId).stream().map(c -> resumen(c, ahora)).toList();
    }

    @Transactional(readOnly = true)
    public CasoDto obtener(Long id) {
        return armar(buscar(id));
    }

    // ---------- Recepción con control de identificación ----------

    /** El documento del rótulo debe coincidir con el paciente; si no, la muestra no entra y queda registrado. */
    @Transactional(noRollbackFor = ReglaNegocioException.class)
    public CasoDto recibir(Long id, RecepcionRequest r) {
        CasoPatologia c = buscar(id);
        Paciente p = pacienteService.buscarEntidad(c.getPacienteId());
        if (!compacto(r.documentoRotulo()).equals(compacto(p.getNumeroDocumento()))) {
            evento(c, "IDENTIFICACION_RECHAZADA", "Rótulo: " + r.documentoRotulo().trim());
            notificaciones.paraUsuario(c.getMedico(), "Muestra de patología no recibida " + c.getNumero(),
                    "El documento del rótulo no corresponde a " + p.getNombreCompleto() + ". Verifique la muestra.", enlace(c));
            throw new ReglaNegocioException("IDENTIFICACION_NO_COINCIDE",
                    "El documento del rótulo no corresponde al paciente del caso. No reciba la muestra y contacte al servicio que la envió.");
        }
        if (r.muestrasRecibidas() != c.getNumeroMuestras() && limpio(r.observacion()) == null) {
            throw new DatosInvalidosException(Map.of("observacion", "Se esperaban " + c.getNumeroMuestras()
                    + " muestras y llegaron " + r.muestrasRecibidas() + ": registre la observación"));
        }
        PlantillaPatologia plantilla = r.plantillaId() == null
                ? plantillas.findFirstByTipoAndServicioIsNull(c.getTipo())
                .orElseThrow(() -> new ReglaNegocioException("SIN_PLANTILLA", "No hay una plantilla para " + c.getTipo().getDescripcion() + "."))
                : plantillas.findById(r.plantillaId()).filter(x -> x.getTipo() == c.getTipo())
                .orElseThrow(() -> new DatosInvalidosException(Map.of("plantillaId", "La plantilla no corresponde al tipo de estudio")));
        c.recibir(usuario(), plantilla);
        evento(c, "RECIBIDO", r.muestrasRecibidas() + " muestra(s) · identificación verificada"
                + (limpio(r.observacion()) == null ? "" : " · " + r.observacion().trim()));
        return armar(c);
    }

    // ---------- Registro estructurado (RFPAT-004) ----------

    @Transactional
    public CasoDto registrarMacroscopia(Long id, Map<String, String> valores) {
        CasoPatologia c = buscar(id);
        if (c.getPlantilla() == null) {
            throw new ReglaNegocioException("CASO_ESTADO_INVALIDO", "Primero reciba la muestra y verifique la identificación.");
        }
        Map<String, String> limpios = validar(c.getPlantilla().campos(PlantillaPatologia.Seccion.MACRO), valores, true);
        c.registrarMacroscopia(limpios, usuario());
        evento(c, "MACROSCOPIA", null);
        return armar(c);
    }

    @Transactional
    public CasoDto registrarMicroscopia(Long id, Map<String, String> valores, String diagnostico) {
        CasoPatologia c = buscar(id);
        if (c.getPlantilla() == null) {
            throw new ReglaNegocioException("CASO_ESTADO_INVALIDO", "Registre primero la macroscopía.");
        }
        // Borrador: se valida el formato; lo obligatorio se exige al validar
        Map<String, String> limpios = validar(c.getPlantilla().campos(PlantillaPatologia.Seccion.MICRO), valores, false);
        c.registrarMicroscopia(limpios, limpio(diagnostico));
        return armar(c);
    }

    /** Solo los campos de la plantilla; números y opciones con formato válido; los obligatorios, si se exige. */
    static Map<String, String> validar(List<PlantillaPatologia.Campo> campos, Map<String, String> valores, boolean exigirRequeridos) {
        Map<String, String> limpios = new LinkedHashMap<>();
        Map<String, String> errores = new LinkedHashMap<>();
        for (PlantillaPatologia.Campo campo : campos) {
            String v = limpio(valores.get(campo.clave()));
            if (v == null) {
                if (exigirRequeridos && campo.requerido()) errores.put(campo.clave(), campo.etiqueta() + ": obligatorio");
                continue;
            }
            switch (campo.tipo()) {
                case numero -> {
                    try {
                        if (Double.parseDouble(v.replace(',', '.')) < 0) errores.put(campo.clave(), campo.etiqueta() + ": no puede ser negativo");
                    } catch (NumberFormatException e) {
                        errores.put(campo.clave(), campo.etiqueta() + ": debe ser un número");
                    }
                }
                case opcion -> {
                    if (campo.opciones() == null || !campo.opciones().contains(v)) {
                        errores.put(campo.clave(), campo.etiqueta() + ": seleccione una opción de la lista");
                    }
                }
                case texto -> {
                }
            }
            limpios.put(campo.clave(), v);
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        return limpios;
    }

    // ---------- Validación, auditoría y versiones (RFPAT-006 / 007) ----------

    @Transactional(noRollbackFor = ReglaNegocioException.class) // conserva el conteo de contraseñas erradas
    public CasoDto validarInforme(Long id, String password, String motivoCambio) {
        CasoPatologia c = buscar(id);
        if (c.getEstado() != EstadoCaso.MACROSCOPIA && c.getEstado() != EstadoCaso.INFORMADO) {
            throw new ReglaNegocioException("CASO_ESTADO_INVALIDO", "Registre la macroscopía y la microscopía antes de validar.");
        }
        PlantillaPatologia pl = c.getPlantilla();
        validar(pl.campos(PlantillaPatologia.Seccion.MACRO), c.getMacroscopia(), true);
        validar(pl.campos(PlantillaPatologia.Seccion.MICRO), c.getMicroscopia(), true);
        if (c.getDiagnostico() == null) {
            throw new DatosInvalidosException(Map.of("diagnostico", "Escriba el diagnóstico"));
        }
        List<InformePatologia> previas = informes.findByCasoIdOrderByVersionDesc(c.getId());
        String motivo = limpio(motivoCambio);
        if (!previas.isEmpty()) {
            InformePatologia ultima = previas.getFirst();
            if (ultima.getMacroscopia().equals(c.getMacroscopia()) && ultima.getMicroscopia().equals(c.getMicroscopia())
                    && ultima.getDiagnostico().equals(c.getDiagnostico())) {
                throw new ReglaNegocioException("SIN_CAMBIOS", "El informe no tiene cambios frente a la versión " + ultima.getVersion() + ".");
            }
            if (motivo == null) {
                throw new DatosInvalidosException(Map.of("motivoCambio", "Explique por qué se corrige el informe validado"));
            }
        }
        String yo = usuario();
        reautenticador.confirmar(yo, password);
        int version = previas.isEmpty() ? 1 : previas.getFirst().getVersion() + 1;
        informes.save(new InformePatologia(c, version, previas.isEmpty() ? null : motivo, yo, nombreDe(yo)));
        LocalDate hoy = LocalDate.now(calendario.getZona());
        c.validar(yo, c.getRetencionHasta() != null ? c.getRetencionHasta() : hoy.plusYears(anosRetencion));
        // RFPAT-006: quién, cuándo y en qué estado quedó
        evento(c, "VALIDADO", "Versión " + version + " · " + c.getEstado().getDescripcion() + (version > 1 ? " · " + motivo : ""));
        notificaciones.paraUsuario(c.getMedico(), (version == 1 ? "Informe de patología " : "Informe de patología corregido ")
                + c.getNumero(), c.getDiagnostico(), enlace(c));
        publicador.publishEvent(new DocumentoGenerado(c.getPacienteId(), null, TipoDocumento.INFORME_PATOLOGIA,
                c.getTipo().getDescripcion() + " · " + c.getSitioAnatomico() + " · " + c.getNumero(), "Patología", nombreDe(yo),
                "PATOLOGIA", String.valueOf(c.getId()), enlace(c), hoy, version > 1 ? motivo : null));
        return armar(c);
    }

    // ---------- Custodia y retención (RFPAT-008) ----------

    @Transactional
    public CasoDto registrarCustodia(Long id, CustodiaRequest r) {
        CasoPatologia c = buscar(id);
        if (c.getEstado() == EstadoCaso.SOLICITADO) {
            throw new ReglaNegocioException("CASO_ESTADO_INVALIDO", "El material aún no ha llegado a patología.");
        }
        Optional<CustodiaPatologia> ultimo = custodias.findByCasoIdOrderByFechaAscIdAsc(id).stream()
                .filter(x -> x.getMaterial() == r.material()).reduce((a, b) -> b);
        if (ultimo.filter(u -> u.getAccion() == CustodiaPatologia.Accion.DESCARTADO).isPresent()) {
            throw new ReglaNegocioException("MATERIAL_DESCARTADO", "Ese material ya fue descartado.");
        }
        switch (r.accion()) {
            case ARCHIVADO -> {
                if (limpio(r.ubicacion()) == null) throw new DatosInvalidosException(Map.of("ubicacion", "Indique dónde se archiva"));
            }
            case PRESTADO -> {
                if (limpio(r.destinatario()) == null) throw new DatosInvalidosException(Map.of("destinatario", "Indique a quién se presta"));
            }
            case DEVUELTO -> {
                if (ultimo.filter(u -> u.getAccion() == CustodiaPatologia.Accion.PRESTADO).isEmpty()) {
                    throw new ReglaNegocioException("SIN_PRESTAMO", "No hay un préstamo pendiente de ese material.");
                }
            }
            case DESCARTADO -> {
                LocalDate hoy = LocalDate.now(calendario.getZona());
                if (c.getRetencionHasta() == null || !hoy.isAfter(c.getRetencionHasta())) {
                    throw new ReglaNegocioException("RETENCION_VIGENTE", c.getRetencionHasta() == null
                            ? "El caso aún no tiene informe: el material se conserva."
                            : "El material debe conservarse hasta el " + c.getRetencionHasta() + ".");
                }
            }
        }
        custodias.save(new CustodiaPatologia(c.getId(), r.material(), r.accion(), r.cantidad(), limpio(r.ubicacion()),
                limpio(r.destinatario()), limpio(r.motivo()), usuario()));
        evento(c, "CUSTODIA", r.material().getDescripcion() + ": " + r.accion().getDescripcion().toLowerCase());
        return armar(c);
    }

    // ---------- Plantillas (RFPAT-005) ----------

    @Transactional(readOnly = true)
    public List<PlantillaDto> plantillas() {
        return plantillas.findAllByOrderByTipoAscServicioAscNombreAsc().stream().map(PlantillaDto::de).toList();
    }

    @Transactional
    public PlantillaDto crearPlantilla(PlantillaRequest r) {
        if (limpio(r.servicio()) == null) {
            throw new DatosInvalidosException(Map.of("servicio", "Una plantilla nueva es la personalización de un servicio: indique cuál"));
        }
        return PlantillaDto.de(plantillas.save(new PlantillaPatologia(r.tipo(), r.servicio().trim(), r.nombre().trim(),
                campos(r.campos()), usuario())));
    }

    @Transactional
    public PlantillaDto actualizarPlantilla(Long id, PlantillaRequest r) {
        PlantillaPatologia p = plantillas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Plantilla", id));
        if (p.getTipo() != r.tipo()) {
            throw new DatosInvalidosException(Map.of("tipo", "No se cambia el tipo de estudio de una plantilla"));
        }
        p.actualizar(r.nombre().trim(), campos(r.campos()), usuario());
        return PlantillaDto.de(p);
    }

    private static List<PlantillaPatologia.Campo> campos(List<CampoRequest> campos) {
        Set<String> claves = new HashSet<>();
        Map<String, String> errores = new LinkedHashMap<>();
        List<PlantillaPatologia.Campo> lista = new ArrayList<>();
        for (CampoRequest c : campos) {
            if (!claves.add(c.clave())) errores.put(c.clave(), "Clave repetida: " + c.clave());
            List<String> opciones = c.opciones() == null ? null : c.opciones().stream().map(String::trim).distinct().toList();
            if (c.tipo() == PlantillaPatologia.TipoCampo.opcion && (opciones == null || opciones.size() < 2)) {
                errores.put(c.clave(), c.etiqueta() + ": una lista necesita al menos dos opciones");
            }
            lista.add(new PlantillaPatologia.Campo(c.clave(), c.etiqueta().trim(), c.seccion(), c.tipo(),
                    c.tipo() == PlantillaPatologia.TipoCampo.opcion ? opciones : null, c.esRequerido(), limpio(c.unidad())));
        }
        if (lista.stream().noneMatch(c -> c.seccion() == PlantillaPatologia.Seccion.MICRO)) {
            errores.put("campos", "La plantilla necesita al menos un campo de microscopía");
        }
        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);
        return lista;
    }

    // ---------- armado ----------

    private CasoResumenDto resumen(CasoPatologia c, Instant ahora) {
        return new CasoResumenDto(c.getId(), c.getNumero(), PacienteResumenDto.de(pacienteService.buscarEntidad(c.getPacienteId())),
                c.getTipo().name(), c.getTipo().getDescripcion(), c.getPrioridad().name(), c.getPrioridad().getDescripcion(),
                c.getEstado().name(), c.getEstado().getDescripcion(), c.getSitioAnatomico(), c.getMedicoNombre(),
                c.getSolicitadoEn(), c.getRecibidoEn(), c.limiteRespuesta(), c.vencido(ahora), c.getValidadoEn(),
                c.getEstado() == EstadoCaso.INFORMADO ? c.getDiagnostico() : null);
    }

    private CasoDto armar(CasoPatologia c) {
        Instant ahora = Instant.now();
        List<InformePatologia> versiones = informes.findByCasoIdOrderByVersionDesc(c.getId());
        boolean cambios = !versiones.isEmpty() && (!versiones.getFirst().getMicroscopia().equals(c.getMicroscopia())
                || !Objects.equals(versiones.getFirst().getDiagnostico(), c.getDiagnostico()));
        List<CustodiaPatologia> movimientos = custodias.findByCasoIdOrderByFechaAscIdAsc(c.getId());
        Map<CustodiaPatologia.Material, CustodiaPatologia> ultimoPor = new LinkedHashMap<>();
        movimientos.forEach(m -> ultimoPor.put(m.getMaterial(), m));
        List<String> ubicacion = ultimoPor.values().stream().map(m -> m.getMaterial().getDescripcion() + ": " + switch (m.getAccion()) {
            case ARCHIVADO, DEVUELTO -> m.getUbicacion() != null ? "archivado en " + m.getUbicacion() : "devuelto al archivo";
            case PRESTADO -> "prestado a " + m.getDestinatario();
            case DESCARTADO -> "descartado";
        }).toList();
        Optional<Consulta> consulta = c.getConsultaId() == null ? Optional.empty() : consultas.findById(c.getConsultaId());
        List<String> dx = consulta.map(x -> x.getDiagnosticos().stream()
                .map(d -> d.getDiagnostico().getCodigo() + " " + d.getDiagnostico().getNombre()).toList()).orElse(List.of());
        List<String> alergias = antecedentes.findByPacienteIdAndTipoAndActivoTrue(c.getPacienteId(), Antecedente.Tipo.ALERGIA)
                .stream().map(Antecedente::getDescripcion).toList();
        List<CasoResumenDto> previos = casos.findByPacienteIdOrderBySolicitadoEnDesc(c.getPacienteId()).stream()
                .filter(x -> !x.getId().equals(c.getId())).map(x -> resumen(x, ahora)).toList();
        return new CasoDto(resumen(c, ahora), c.getConsultaId(), c.getDatosClinicos(), c.getNumeroMuestras(), c.getMedico(),
                c.getRecibidoPor(), c.getPlantilla() == null ? null : PlantillaDto.de(c.getPlantilla()), c.getMacroscopia(),
                c.getMicroscopia(), c.getDiagnostico(), c.getMacroscopiaPor(), c.getMacroscopiaEn(), c.getValidadoPor(),
                c.getRetencionHasta(), cambios, versiones.stream().map(v -> VersionDto.de(v, c.getNumero())).toList(),
                movimientos.stream().map(CustodiaDto::de).toList(), ubicacion,
                eventos.findByCasoIdOrderByFechaAscIdAsc(c.getId()).stream().map(EventoDto::de).toList(), alergias, dx, previos);
    }

    private CasoPatologia buscar(Long id) {
        return casos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Caso de patología", id));
    }

    private void evento(CasoPatologia c, String accion, String detalle) {
        eventos.save(new EventoCasoPatologia(c.getId(), accion, detalle, usuario()));
    }

    private String nombreDe(String username) {
        return usuarios.findByUsernameIgnoreCase(username).map(Usuario::getNombreCompleto).orElse(username);
    }

    private static String enlace(CasoPatologia c) {
        return "/patologia/casos/" + c.getId();
    }

    private static String compacto(String s) {
        return s == null ? "" : Normalizador.texto(s).replace(" ", "");
    }

    private String usuario() {
        return auditor.getCurrentAuditor().orElse("sistema");
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
