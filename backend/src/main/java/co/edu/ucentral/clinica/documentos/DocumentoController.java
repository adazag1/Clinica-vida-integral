package co.edu.ucentral.clinica.documentos;

import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.documentos.dto.DocumentoDtos.DocumentoDto;
import co.edu.ucentral.clinica.documentos.dto.DocumentoDtos.DocumentoResumenDto;
import co.edu.ucentral.clinica.documentos.dto.DocumentoDtos.ReporteDto;
import co.edu.ucentral.clinica.documentos.dto.DocumentoDtos.SolicitudDto;
import co.edu.ucentral.clinica.documentos.dto.DocumentoSolicitudes.AtencionRequest;
import co.edu.ucentral.clinica.documentos.dto.DocumentoSolicitudes.CargaRequest;
import co.edu.ucentral.clinica.documentos.dto.DocumentoSolicitudes.EstadoRequest;
import co.edu.ucentral.clinica.documentos.dto.DocumentoSolicitudes.SolicitudRequest;
import co.edu.ucentral.clinica.documentos.dto.DocumentoSolicitudes.UbicacionRequest;
import co.edu.ucentral.clinica.documentos.dto.DocumentoSolicitudes.VersionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/documentos")
@Tag(name = "Gestión documental", description = "Documentos clínicos, versiones, archivo físico y solicitudes")
public class DocumentoController {

    /** RNFGAC-001: documentos clínicos solo para el personal asistencial, archivo y auditoría (admisiones: solo administrativos). */
    static final String LECTURA = "hasAnyRole('ADMIN','ARCHIVO','MEDICO','ENFERMERIA','AUDITORIA','ADMISIONES')";
    static final String CARGA = "hasAnyRole('ADMIN','ARCHIVO','MEDICO','ENFERMERIA','ADMISIONES')";
    static final String ARCHIVO = "hasAnyRole('ADMIN','ARCHIVO')";
    static final String SOLICITUD = "hasAnyRole('ADMIN','ARCHIVO','MEDICO','ENFERMERIA','AUDITORIA')";

    private final DocumentoService service;

    public DocumentoController(DocumentoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(LECTURA)
    @Operation(summary = "Búsqueda avanzada por paciente, documento, tipo, fechas, profesional, servicio o palabras clave (RFGAC-003)")
    public Page<DocumentoResumenDto> buscar(@RequestParam(required = false) Long pacienteId,
                                            @RequestParam(required = false) String documentoPaciente,
                                            @RequestParam(required = false) TipoDocumento tipo,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                            @RequestParam(required = false) String profesional,
                                            @RequestParam(required = false) String servicio,
                                            @RequestParam(required = false) String texto,
                                            @RequestParam(required = false) Documento.Estado estado,
                                            @RequestParam(defaultValue = "0") int pagina,
                                            @RequestParam(defaultValue = "20") int tamano) {
        return service.buscar(new DocumentoService.Busqueda(pacienteId, documentoPaciente, tipo, desde, hasta, profesional,
                servicio, texto, estado), pagina, tamano);
    }

    @GetMapping("/pacientes/{pacienteId}")
    @PreAuthorize(LECTURA)
    public List<DocumentoResumenDto> dePaciente(@PathVariable Long pacienteId) {
        return service.dePaciente(pacienteId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CARGA)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un documento digital (PDF, JPG, PNG) o físico con sus metadatos (RFGAC-002/004)")
    public DocumentoDto cargar(@Valid @ModelAttribute CargaRequest datos,
                               @RequestPart(name = "archivo", required = false) MultipartFile archivo) {
        return service.cargar(datos, archivo);
    }

    @GetMapping("/{id}")
    @PreAuthorize(LECTURA)
    public DocumentoDto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @GetMapping("/{id}/contenido")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Descarga la versión vigente o una anterior; la descarga queda registrada")
    public ResponseEntity<byte[]> contenido(@PathVariable Long id, @RequestParam(required = false) Integer version) {
        DocumentoService.Contenido c = service.descargar(id, version);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.tipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(c.nombre(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(c.bytes());
    }

    @PostMapping(value = "/{id}/versiones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CARGA)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reemplaza el archivo conservando la versión anterior (RFGAC-005)")
    public DocumentoDto nuevaVersion(@PathVariable Long id, @Valid @ModelAttribute VersionRequest datos,
                                     @RequestPart("archivo") MultipartFile archivo) {
        return service.nuevaVersion(id, archivo, datos.motivo());
    }

    @PostMapping("/{id}/estado")
    @PreAuthorize(ARCHIVO)
    @Operation(summary = "Activo, archivado, en custodia o eliminado según la retención (RFGAC-006)")
    public DocumentoDto cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest request) {
        return service.cambiarEstado(id, request.estado(), request.motivo());
    }

    @PutMapping("/{id}/ubicacion")
    @PreAuthorize(ARCHIVO)
    @Operation(summary = "Ubicación física: sala, estantería, sección y caja (RFGAC-010)")
    public DocumentoDto ubicar(@PathVariable Long id, @Valid @RequestBody UbicacionRequest request) {
        return service.ubicar(id, request);
    }

    // ---------- Solicitudes ----------

    @PostMapping("/solicitudes")
    @PreAuthorize(SOLICITUD)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pide un documento o el expediente al archivo clínico (RFGAC-008)")
    public SolicitudDto solicitar(@Valid @RequestBody SolicitudRequest request) {
        return service.solicitar(request);
    }

    @GetMapping("/solicitudes")
    @PreAuthorize(ARCHIVO)
    public List<SolicitudDto> solicitudesAbiertas() {
        return service.solicitudesAbiertas();
    }

    @GetMapping("/solicitudes/mias")
    @PreAuthorize(SOLICITUD)
    public List<SolicitudDto> misSolicitudes() {
        return service.misSolicitudes();
    }

    @PostMapping("/solicitudes/{id}/{accion}")
    @PreAuthorize(ARCHIVO)
    public SolicitudDto atender(@PathVariable Long id, @PathVariable String accion,
                                @Valid @RequestBody(required = false) AtencionRequest request) {
        DocumentoService.Atencion a = switch (accion) {
            case "entregar" -> DocumentoService.Atencion.ENTREGAR;
            case "rechazar" -> DocumentoService.Atencion.RECHAZAR;
            case "devolver" -> DocumentoService.Atencion.DEVOLVER;
            default -> throw new RecursoNoEncontradoException("Acción", accion);
        };
        return service.atender(id, a, request == null ? null : request.respuesta());
    }

    @GetMapping("/reportes")
    @PreAuthorize("hasAnyRole('ADMIN','ARCHIVO','AUDITORIA','DIRECTIVO')")
    @Operation(summary = "Volumen documental, accesos y tiempos de las solicitudes (RFGAC-011)")
    public ReporteDto reporte(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.reporte(desde, hasta);
    }
}
