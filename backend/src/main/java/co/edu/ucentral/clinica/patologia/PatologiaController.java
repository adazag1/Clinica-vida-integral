package co.edu.ucentral.clinica.patologia;

import co.edu.ucentral.clinica.patologia.dto.PatologiaDtos.CasoDto;
import co.edu.ucentral.clinica.patologia.dto.PatologiaDtos.CasoResumenDto;
import co.edu.ucentral.clinica.patologia.dto.PatologiaDtos.PlantillaDto;
import co.edu.ucentral.clinica.patologia.dto.PatologiaSolicitudes.CustodiaRequest;
import co.edu.ucentral.clinica.patologia.dto.PatologiaSolicitudes.MicroscopiaRequest;
import co.edu.ucentral.clinica.patologia.dto.PatologiaSolicitudes.PlantillaRequest;
import co.edu.ucentral.clinica.patologia.dto.PatologiaSolicitudes.RecepcionRequest;
import co.edu.ucentral.clinica.patologia.dto.PatologiaSolicitudes.SolicitudCasoRequest;
import co.edu.ucentral.clinica.patologia.dto.PatologiaSolicitudes.ValidacionRequest;
import co.edu.ucentral.clinica.patologia.dto.PatologiaSolicitudes.ValoresRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/api/patologia")
@Tag(name = "Patología", description = "Casos, macroscopía, microscopía, informes versionados y custodia")
public class PatologiaController {

    static final String LECTURA = "hasAnyRole('ADMIN','PATOLOGIA','MEDICO','ENFERMERIA','AUDITORIA')";
    static final String PATOLOGIA = "hasAnyRole('ADMIN','PATOLOGIA')";
    /** Validar el informe es acto médico del patólogo. */
    static final String PATOLOGO = "hasRole('PATOLOGIA')";

    private final PatologiaService service;

    public PatologiaController(PatologiaService service) {
        this.service = service;
    }

    @PostMapping("/casos")
    @PreAuthorize("hasRole('MEDICO')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Solicita un estudio de patología; asigna el número único del caso (RFPAT-002)")
    public CasoDto solicitar(@Valid @RequestBody SolicitudCasoRequest request) {
        return service.solicitar(request);
    }

    @GetMapping("/casos")
    @PreAuthorize(PATOLOGIA)
    @Operation(summary = "Cola de casos: intraoperatorios y urgentes primero (RFPAT-003)")
    public List<CasoResumenDto> cola(@RequestParam(defaultValue = "PENDIENTES") PatologiaService.Vista vista,
                                     @RequestParam(required = false) TipoCaso tipo,
                                     @RequestParam(required = false) PrioridadCaso prioridad) {
        return service.cola(vista, tipo, prioridad);
    }

    @GetMapping(value = "/casos", params = "consultaId")
    @PreAuthorize(LECTURA)
    public List<CasoResumenDto> deConsulta(@RequestParam Long consultaId) {
        return service.deConsulta(consultaId);
    }

    @GetMapping("/casos/pacientes/{pacienteId}")
    @PreAuthorize(LECTURA)
    public List<CasoResumenDto> dePaciente(@PathVariable Long pacienteId) {
        return service.dePaciente(pacienteId);
    }

    @GetMapping("/casos/{id}")
    @PreAuthorize(LECTURA)
    public CasoDto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping("/casos/{id}/recibir")
    @PreAuthorize(PATOLOGIA)
    @Operation(summary = "Recibe la muestra verificando que el rótulo corresponda al paciente")
    public CasoDto recibir(@PathVariable Long id, @Valid @RequestBody RecepcionRequest request) {
        return service.recibir(id, request);
    }

    @PutMapping("/casos/{id}/macroscopia")
    @PreAuthorize(PATOLOGIA)
    @Operation(summary = "Macroscopía en campos estructurados según la plantilla (RFPAT-004)")
    public CasoDto macroscopia(@PathVariable Long id, @Valid @RequestBody ValoresRequest request) {
        return service.registrarMacroscopia(id, request.valores());
    }

    @PutMapping("/casos/{id}/microscopia")
    @PreAuthorize(PATOLOGO)
    @Operation(summary = "Microscopía y diagnóstico (borrador hasta validar)")
    public CasoDto microscopia(@PathVariable Long id, @Valid @RequestBody MicroscopiaRequest request) {
        return service.registrarMicroscopia(id, request.valores(), request.diagnostico());
    }

    @PostMapping("/casos/{id}/validar")
    @PreAuthorize(PATOLOGO)
    @Operation(summary = "Valida con contraseña: crea una versión inmutable con autor, fecha y motivo (RFPAT-006/007)")
    public CasoDto validar(@PathVariable Long id, @Valid @RequestBody ValidacionRequest request) {
        return service.validarInforme(id, request.password(), request.motivoCambio());
    }

    @PostMapping("/casos/{id}/custodia")
    @PreAuthorize(PATOLOGIA)
    @Operation(summary = "Registra archivo, préstamo, devolución o descarte del material (RFPAT-008)")
    public CasoDto custodia(@PathVariable Long id, @Valid @RequestBody CustodiaRequest request) {
        return service.registrarCustodia(id, request);
    }

    @GetMapping("/plantillas")
    @PreAuthorize(LECTURA)
    public List<PlantillaDto> plantillas() {
        return service.plantillas();
    }

    @PostMapping("/plantillas")
    @PreAuthorize(PATOLOGIA)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea la plantilla personalizada de un servicio (RFPAT-005)")
    public PlantillaDto crearPlantilla(@Valid @RequestBody PlantillaRequest request) {
        return service.crearPlantilla(request);
    }

    @PutMapping("/plantillas/{id}")
    @PreAuthorize(PATOLOGIA)
    public PlantillaDto actualizarPlantilla(@PathVariable Long id, @Valid @RequestBody PlantillaRequest request) {
        return service.actualizarPlantilla(id, request);
    }
}
