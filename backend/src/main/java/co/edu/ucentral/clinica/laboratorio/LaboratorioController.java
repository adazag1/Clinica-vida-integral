package co.edu.ucentral.clinica.laboratorio;

import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.ControlCalidadDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.ExamenDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.OrdenDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.OrdenResumenDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.RangoDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.ResultadoRecienteDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.ControlCalidadRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.MuestraRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.OrdenRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.RangoRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.RechazoRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.RepeticionRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.ResultadosRequest;
import co.edu.ucentral.clinica.laboratorio.dto.LabSolicitudes.ValidacionRequest;
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
@RequestMapping("/api/laboratorio")
@Tag(name = "Laboratorio clínico", description = "Órdenes, muestras, resultados, control de calidad y validación")
public class LaboratorioController {

    /** RNFLC-004: validar y configurar reglas es exclusivo del laboratorio. */
    private static final String LAB = "hasAnyRole('ADMIN','LABORATORIO')";
    /** Enfermería o el área de toma de muestras entrega la muestra. */
    private static final String MUESTRA = "hasAnyRole('ADMIN','LABORATORIO','ENFERMERIA')";
    private static final String LECTURA = "hasAnyRole('ADMIN','LABORATORIO','MEDICO','ENFERMERIA','AUDITORIA')";

    private final LaboratorioService service;

    public LaboratorioController(LaboratorioService service) {
        this.service = service;
    }

    @GetMapping("/examenes")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Catálogo de exámenes con sus analitos y rangos")
    public List<ExamenDto> catalogo() {
        return service.catalogo();
    }

    @PutMapping("/rangos/{id}")
    @PreAuthorize(LAB)
    @Operation(summary = "Ajusta un rango de referencia o crítico sin desarrollo (RFLC-014)")
    public RangoDto actualizarRango(@PathVariable Long id, @RequestBody RangoRequest request) {
        return service.actualizarRango(id, request);
    }

    @PostMapping("/ordenes")
    @PreAuthorize("hasRole('MEDICO')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "El médico ordena exámenes desde la consulta (RFMT-006 / RFLC-015)")
    public OrdenDto crear(@Valid @RequestBody OrdenRequest request) {
        return service.crearOrden(request);
    }

    @GetMapping("/ordenes")
    @PreAuthorize(LAB)
    @Operation(summary = "Cola del laboratorio por estado y prioridad, urgentes primero (RFLC-002 / RFLC-005)")
    public List<OrdenResumenDto> cola(@RequestParam(required = false) EstadoOrden estado,
                                      @RequestParam(required = false) PrioridadLab prioridad) {
        return service.cola(estado, prioridad);
    }

    @GetMapping("/pacientes/{pacienteId}/ordenes")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Órdenes del paciente y su estado (RFMT-006)")
    public List<OrdenResumenDto> dePaciente(@PathVariable Long pacienteId) {
        return service.dePaciente(pacienteId);
    }

    @GetMapping("/pacientes/{pacienteId}/resultados-recientes")
    @PreAuthorize(LECTURA)
    public List<ResultadoRecienteDto> recientes(@PathVariable Long pacienteId) {
        return service.resultadosRecientes(pacienteId);
    }

    @GetMapping("/ordenes/{id}")
    @PreAuthorize(LECTURA)
    public OrdenDto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping("/ordenes/{id}/muestra")
    @PreAuthorize(MUESTRA)
    @Operation(summary = "Registra la muestra recibida e identificada")
    public OrdenDto recibirMuestra(@PathVariable Long id, @Valid @RequestBody MuestraRequest request) {
        return service.recibirMuestra(id, request);
    }

    @PostMapping("/ordenes/{id}/rechazar")
    @PreAuthorize(LAB)
    @Operation(summary = "Rechaza la muestra con motivo estructurado (RFLC-011)")
    public OrdenDto rechazar(@PathVariable Long id, @Valid @RequestBody RechazoRequest request) {
        return service.rechazarMuestra(id, request);
    }

    @PostMapping("/ordenes/{id}/nueva-muestra")
    @PreAuthorize(MUESTRA)
    public OrdenDto nuevaMuestra(@PathVariable Long id) {
        return service.solicitarNuevaMuestra(id);
    }

    @PutMapping("/ordenes/{id}/resultados")
    @PreAuthorize(LAB)
    @Operation(summary = "Registra trazabilidad y resultados; interpreta por rango y hace el delta check")
    public OrdenDto resultados(@PathVariable Long id, @Valid @RequestBody ResultadosRequest request) {
        return service.registrarResultados(id, request);
    }

    @PostMapping("/ordenes/{id}/resultados/{analitoId}/repetir")
    @PreAuthorize(LAB)
    @Operation(summary = "Repite una prueba con motivo estructurado (RFLC-009)")
    public OrdenDto repetir(@PathVariable Long id, @PathVariable Long analitoId,
                            @Valid @RequestBody RepeticionRequest request) {
        return service.repetir(id, analitoId, request);
    }

    @PostMapping("/ordenes/{id}/validar")
    @PreAuthorize(LAB)
    @Operation(summary = "Libera los resultados si se cumplen las reglas; avisa al médico (RFMT-004)")
    public OrdenDto validar(@PathVariable Long id, @Valid @RequestBody ValidacionRequest request) {
        return service.validar(id, request);
    }

    @GetMapping("/control-calidad")
    @PreAuthorize(LAB)
    public List<ControlCalidadDto> controles() {
        return service.controlesDeHoy();
    }

    @PostMapping("/control-calidad")
    @PreAuthorize(LAB)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra el control de calidad del día (RFLC-007)")
    public ControlCalidadDto registrarControl(@Valid @RequestBody ControlCalidadRequest request) {
        return service.registrarControl(request);
    }
}
