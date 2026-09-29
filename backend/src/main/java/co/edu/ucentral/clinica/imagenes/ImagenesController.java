package co.edu.ucentral.clinica.imagenes;

import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.EstudioDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.EstudioResumenDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.MetricaDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.RadiologoDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.TipoEstudioDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.AdquisicionRequest;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.AsignacionRequest;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.FirmaRequest;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.ImagenClaveRequest;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.InformeRequest;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.MotivoRequest;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.OrdenEstudioRequest;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.RespuestaOpinionRequest;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.SegundaOpinionRequest;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesSolicitudes.TipoEstudioRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/imagenes")
@Tag(name = "Imágenes diagnósticas", description = "Lista de trabajo, visor, informes, segunda lectura y métricas")
public class ImagenesController {

    static final String LECTURA = "hasAnyRole('ADMIN','RADIOLOGIA','MEDICO','ENFERMERIA','AUDITORIA')";
    static final String RADIOLOGIA = "hasAnyRole('ADMIN','RADIOLOGIA')";
    /** Firmar, enmendar y dar segunda opinión es acto médico del radiólogo: el administrador no lo hace. */
    static final String RADIOLOGO = "hasRole('RADIOLOGIA')";

    private final ImagenesService service;

    public ImagenesController(ImagenesService service) {
        this.service = service;
    }

    @GetMapping("/tipos")
    @PreAuthorize(LECTURA)
    public List<TipoEstudioDto> catalogo() {
        return service.catalogo();
    }

    @PutMapping("/tipos/{id}")
    @PreAuthorize(RADIOLOGIA)
    @Operation(summary = "Tiempos objetivo por prioridad y plantilla de informe del tipo de estudio (RFRAD-004/006)")
    public TipoEstudioDto configurarTipo(@PathVariable Long id, @Valid @RequestBody TipoEstudioRequest request) {
        return service.configurarTipo(id, request);
    }

    @GetMapping("/radiologos")
    @PreAuthorize(RADIOLOGIA)
    public List<RadiologoDto> radiologos() {
        return service.radiologos();
    }

    @PostMapping("/estudios")
    @PreAuthorize("hasRole('MEDICO')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "El médico ordena un estudio desde la consulta")
    public EstudioDto ordenar(@Valid @RequestBody OrdenEstudioRequest request) {
        return service.ordenar(request);
    }

    @GetMapping("/estudios")
    @PreAuthorize(RADIOLOGIA)
    @Operation(summary = "Lista de trabajo priorizada por urgencia y tiempo (RFRAD-002/004)")
    public List<EstudioResumenDto> listaTrabajo(@RequestParam(defaultValue = "PENDIENTES") ImagenesService.Vista vista,
                                                @RequestParam(required = false) Modalidad modalidad,
                                                @RequestParam(required = false) PrioridadEstudio prioridad) {
        return service.listaTrabajo(vista, modalidad, prioridad);
    }

    @GetMapping("/estudios/pacientes/{pacienteId}")
    @PreAuthorize(LECTURA)
    public List<EstudioResumenDto> dePaciente(@PathVariable Long pacienteId) {
        return service.dePaciente(pacienteId);
    }

    @GetMapping(value = "/estudios", params = "consultaId")
    @PreAuthorize(LECTURA)
    public List<EstudioResumenDto> deConsulta(@RequestParam Long consultaId) {
        return service.deConsulta(consultaId);
    }

    @GetMapping("/estudios/{id}")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Estudio con imágenes, informe, contexto clínico y estudios previos (RFRAD-003/005)")
    public EstudioDto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping(value = "/estudios/{id}/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(RADIOLOGIA)
    @Operation(summary = "Carga imágenes (JPG, PNG o DICOM) y/o el StudyInstanceUID del PACS (RFRAD-009)")
    public EstudioDto cargarImagenes(@PathVariable Long id, @Valid @ModelAttribute AdquisicionRequest datos,
                                     @RequestPart(name = "archivos", required = false) List<MultipartFile> archivos) {
        return service.cargarImagenes(id, archivos, datos.studyInstanceUid());
    }

    @GetMapping("/estudios/{id}/imagenes/{imagenId}")
    @PreAuthorize(LECTURA)
    public ResponseEntity<byte[]> imagen(@PathVariable Long id, @PathVariable Long imagenId) {
        ImagenesService.ContenidoImagen c = service.imagen(id, imagenId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(c.tipoMime()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(c.bytes());
    }

    @PutMapping("/estudios/{id}/imagenes/{imagenId}")
    @PreAuthorize(RADIOLOGO)
    @Operation(summary = "Marca o desmarca una imagen clave para el informe (RFRAD-010)")
    public EstudioDto marcarClave(@PathVariable Long id, @PathVariable Long imagenId, @Valid @RequestBody ImagenClaveRequest request) {
        return service.marcarClave(id, imagenId, request.esClave(), request.descripcion());
    }

    @PutMapping("/estudios/{id}/informe")
    @PreAuthorize(RADIOLOGO)
    @Operation(summary = "Guarda el borrador del informe")
    public EstudioDto guardarInforme(@PathVariable Long id, @Valid @RequestBody InformeRequest request) {
        return service.guardarInforme(id, request);
    }

    @PostMapping("/estudios/{id}/firmar")
    @PreAuthorize(RADIOLOGO)
    @Operation(summary = "Firma con contraseña; registra la notificación de un hallazgo crítico (RFRAD-008)")
    public EstudioDto firmar(@PathVariable Long id, @Valid @RequestBody FirmaRequest request) {
        return service.firmar(id, request);
    }

    @PostMapping("/estudios/{id}/enmendar")
    @PreAuthorize(RADIOLOGO)
    @Operation(summary = "Abre una nueva versión del informe conservando la firmada (RNFRAD-002)")
    public EstudioDto enmendar(@PathVariable Long id, @Valid @RequestBody MotivoRequest request) {
        return service.enmendar(id, request.motivo());
    }

    @PostMapping("/estudios/{id}/asignar")
    @PreAuthorize(RADIOLOGIA)
    public EstudioDto asignar(@PathVariable Long id, @Valid @RequestBody AsignacionRequest request) {
        return service.asignar(id, request.usuario());
    }

    @PostMapping("/estudios/{id}/segunda-opinion")
    @PreAuthorize(RADIOLOGO)
    @Operation(summary = "Pide doble lectura a otro radiólogo (RFRAD-007)")
    public EstudioDto pedirSegundaOpinion(@PathVariable Long id, @Valid @RequestBody SegundaOpinionRequest request) {
        return service.pedirSegundaOpinion(id, request.asignadaA(), request.pregunta());
    }

    @PostMapping("/estudios/{id}/segunda-opinion/{opinionId}")
    @PreAuthorize(RADIOLOGO)
    public EstudioDto responderSegundaOpinion(@PathVariable Long id, @PathVariable Long opinionId,
                                              @Valid @RequestBody RespuestaOpinionRequest request) {
        return service.responderSegundaOpinion(id, opinionId, request.concordancia(), request.comentario());
    }

    @PostMapping("/estudios/{id}/bloquear")
    @PreAuthorize(RADIOLOGIA)
    @Operation(summary = "Bloquea el estudio por inconsistencia de identificación (RFRAD-011)")
    public EstudioDto bloquear(@PathVariable Long id, @Valid @RequestBody MotivoRequest request) {
        return service.bloquear(id, request.motivo());
    }

    @PostMapping("/estudios/{id}/desbloquear")
    @PreAuthorize(RADIOLOGIA)
    public EstudioDto desbloquear(@PathVariable Long id, @Valid @RequestBody MotivoRequest request) {
        return service.desbloquear(id, request.motivo());
    }

    @GetMapping("/metricas")
    @PreAuthorize("hasAnyRole('ADMIN','RADIOLOGIA','DIRECTIVO','AUDITORIA')")
    @Operation(summary = "Tiempos de respuesta por tipo de estudio (RFRAD-012)")
    public List<MetricaDto> metricas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.metricas(desde, hasta);
    }
}
