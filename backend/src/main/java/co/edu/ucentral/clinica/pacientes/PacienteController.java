package co.edu.ucentral.clinica.pacientes;

import co.edu.ucentral.clinica.pacientes.dto.CambioDemograficoDto;
import co.edu.ucentral.clinica.pacientes.dto.PacienteDto;
import co.edu.ucentral.clinica.pacientes.dto.PacienteRequest;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;
import co.edu.ucentral.clinica.pacientes.dto.PosibleDuplicadoDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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
@RequestMapping("/api/pacientes")
@Tag(name = "Pacientes", description = "Registro, búsqueda, duplicados e historial demográfico")
public class PacienteController {

    /** Quienes atienden o programan al paciente pueden consultarlo; solo admisiones lo registra. */
    static final String LECTURA = "hasAnyRole('ADMIN','ADMISIONES','MEDICO','ENFERMERIA','AGENDAMIENTO')";
    static final String ESCRITURA = "hasAnyRole('ADMIN','ADMISIONES')";

    private final PacienteService service;

    public PacienteController(PacienteService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(LECTURA)
    @Operation(summary = "Busca pacientes por documento, nombre, fecha de nacimiento o teléfono (RFADM-002)")
    public Page<PacienteResumenDto> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String documento,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaNacimiento,
            @RequestParam(required = false) String telefono,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.buscar(texto, documento, nombre, fechaNacimiento, telefono, pagina, tamano);
    }

    @GetMapping("/{id}")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Datos completos de un paciente")
    public PacienteDto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping("/posibles-duplicados")
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Pacientes parecidos a los datos digitados, antes de guardar (RFADM-003)")
    public List<PosibleDuplicadoDto> posiblesDuplicados(@RequestBody PacienteRequest request,
                                                        @RequestParam(required = false) Long excluirId) {
        return service.posiblesDuplicados(request, excluirId);
    }

    @PostMapping
    @PreAuthorize(ESCRITURA)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un paciente; con tipo TEMPORAL genera un identificador provisional (RFADM-010)")
    public PacienteDto crear(@Valid @RequestBody PacienteRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Actualiza los datos con un motivo obligatorio que queda en el historial (RFADM-011)")
    public PacienteDto actualizar(@PathVariable Long id, @Valid @RequestBody PacienteRequest request) {
        return service.actualizar(id, request);
    }

    @GetMapping("/{id}/cambios")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Historial de cambios demográficos del paciente")
    public List<CambioDemograficoDto> cambios(@PathVariable Long id) {
        return service.historialCambios(id);
    }
}
