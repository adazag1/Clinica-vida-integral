package co.edu.ucentral.clinica.auditoriaclinica;

import co.edu.ucentral.clinica.auditoriaclinica.dto.AuditoriaDtos.CodificacionDto;
import co.edu.ucentral.clinica.auditoriaclinica.dto.AuditoriaDtos.DiagnosticoCatalogoDto;
import co.edu.ucentral.clinica.auditoriaclinica.dto.AuditoriaDtos.ExpedienteDto;
import co.edu.ucentral.clinica.auditoriaclinica.dto.AuditoriaDtos.TableroDto;
import co.edu.ucentral.clinica.catalogos.CatalogoService;
import co.edu.ucentral.clinica.facturacion.EstadoCuenta;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.CuentaResumenDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/auditoria-clinica")
@Tag(name = "Auditoría clínica", description = "Revisión de cuentas, codificación, devoluciones, indicadores y catálogos")
public class AuditoriaClinicaController {

    /** RNFAU-003: codificar, devolver y aprobar es exclusivo de auditoría (segregación frente a facturación). */
    static final String AUDITORIA = "hasRole('AUDITORIA')";
    static final String LECTURA = "hasAnyRole('ADMIN','AUDITORIA','FACTURACION','DIRECTIVO')";

    public record CodificacionRequest(
            @NotBlank(message = "Seleccione el diagnóstico principal") String diagnosticoPrincipal,
            @Size(max = 4, message = "Máximo 4 diagnósticos relacionados") List<String> relacionados,
            @Size(max = 300, message = "Máximo 300 caracteres") String motivo) {
    }

    public record DevolucionRequest(
            @NotNull(message = "Seleccione el motivo") Devolucion.Motivo motivo,
            @NotBlank(message = "Explique qué debe corregirse") @Size(max = 1000) String detalle,
            @NotNull(message = "Indique a quién se devuelve") Devolucion.Destino dirigidaA) {
    }

    public record DiagnosticoRequest(
            @NotBlank(message = "Ingrese el código") @Size(max = 10) String codigo,
            @NotBlank(message = "Ingrese el nombre") @Size(max = 250) String nombre) {
    }

    public record CambioDiagnosticoRequest(
            @NotBlank(message = "Ingrese el nombre") @Size(max = 250) String nombre,
            Boolean activo,
            @NotBlank(message = "Registre el motivo del cambio") @Size(max = 300) String motivo) {
    }

    private final AuditoriaClinicaService service;
    private final CatalogoService catalogos;

    public AuditoriaClinicaController(AuditoriaClinicaService service, CatalogoService catalogos) {
        this.service = service;
        this.catalogos = catalogos;
    }

    @GetMapping("/cuentas")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Cuentas en auditoría (o en el estado indicado)")
    public List<CuentaResumenDto> cola(@RequestParam(required = false) EstadoCuenta estado) {
        return service.cola(estado);
    }

    @GetMapping("/cuentas/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITORIA')")
    @Operation(summary = "Expediente completo de la atención con verificación, codificación sugerida y devoluciones (RFAU-002)")
    public ExpedienteDto expediente(@PathVariable Long id) {
        return service.expediente(id);
    }

    @PutMapping("/cuentas/{id}/codificacion")
    @PreAuthorize(AUDITORIA)
    @Operation(summary = "Confirma los códigos CIE-10; los cambios quedan con motivo en el historial (RFAU-003/009)")
    public CodificacionDto codificar(@PathVariable Long id, @Valid @RequestBody CodificacionRequest request) {
        return service.codificar(id, request.diagnosticoPrincipal(), request.relacionados(), request.motivo());
    }

    @PostMapping("/cuentas/{id}/aprobar")
    @PreAuthorize(AUDITORIA)
    @Operation(summary = "Aprueba si cada cargo tiene orden, las notas están firmadas y hay codificación (RFAU-007)")
    public ExpedienteDto aprobar(@PathVariable Long id) {
        return service.aprobar(id);
    }

    @PostMapping("/cuentas/{id}/devolver")
    @PreAuthorize(AUDITORIA)
    @Operation(summary = "Devuelve la cuenta con motivo a quien debe corregirla (RFAU-004)")
    public ExpedienteDto devolver(@PathVariable Long id, @Valid @RequestBody DevolucionRequest request) {
        return service.devolver(id, request.motivo(), request.detalle(), request.dirigidaA());
    }

    @GetMapping("/tablero")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Indicadores: devoluciones, tiempos de corrección, historias incompletas y glosas clínicas (RFAU-006)")
    public TableroDto tablero(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.tablero(desde, hasta);
    }

    @GetMapping("/catalogos/diagnosticos")
    @PreAuthorize(LECTURA)
    public List<DiagnosticoCatalogoDto> diagnosticos(@RequestParam(defaultValue = "") String texto) {
        return service.diagnosticos(texto);
    }

    @PostMapping("/catalogos/diagnosticos")
    @PreAuthorize(AUDITORIA)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agrega un código CIE-10; sube la versión del catálogo (RFAU-005)")
    public DiagnosticoCatalogoDto crearDiagnostico(@Valid @RequestBody DiagnosticoRequest request) {
        return service.crearDiagnostico(request.codigo(), request.nombre());
    }

    @PutMapping("/catalogos/diagnosticos/{id}")
    @PreAuthorize(AUDITORIA)
    public DiagnosticoCatalogoDto actualizarDiagnostico(@PathVariable Long id, @Valid @RequestBody CambioDiagnosticoRequest request) {
        return service.actualizarDiagnostico(id, request.nombre(), request.activo() == null || request.activo(), request.motivo());
    }

    @GetMapping("/catalogos/{catalogo}/version")
    @PreAuthorize(LECTURA)
    public CatalogoService.VersionDto version(@PathVariable String catalogo) {
        return catalogos.version(catalogo.toUpperCase());
    }

    @GetMapping("/catalogos/{catalogo}/historial")
    @PreAuthorize(LECTURA)
    public List<CatalogoService.CambioDto> historial(@PathVariable String catalogo) {
        return catalogos.historial(catalogo.toUpperCase());
    }
}
