package co.edu.ucentral.clinica.historia;

import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.AntecedenteDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.ConsultaHistorialDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.DiagnosticoDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.MedicamentoDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.ResumenClinicoDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.SignosDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.AntecedenteRequest;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.MotivoRequest;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.SignosRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/historia")
@Tag(name = "Historia clínica", description = "Resumen clínico, antecedentes, signos vitales e historial")
public class HistoriaController {

    /** RNFMT-003: la historia clínica solo la ven el personal asistencial y auditoría. */
    static final String LECTURA = "hasAnyRole('ADMIN','MEDICO','ENFERMERIA','AUDITORIA')";
    static final String REGISTRO = "hasAnyRole('MEDICO','ENFERMERIA')";

    private final HistoriaService service;

    public HistoriaController(HistoriaService service) {
        this.service = service;
    }

    @GetMapping("/diagnosticos")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Busca en el catálogo CIE-10 por código o nombre")
    public List<DiagnosticoDto> diagnosticos(@RequestParam(defaultValue = "") String texto) {
        return service.buscarDiagnosticos(texto);
    }

    @GetMapping("/medicamentos")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Busca en el catálogo de medicamentos por nombre, principio activo o familia")
    public List<MedicamentoDto> medicamentos(@RequestParam(defaultValue = "") String texto) {
        return service.buscarMedicamentos(texto);
    }

    @GetMapping("/{pacienteId}/resumen")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Vista clínica consolidada: alergias, medicamentos, diagnósticos, signos y triage (RFMT-002)")
    public ResumenClinicoDto resumen(@PathVariable Long pacienteId) {
        return service.resumen(pacienteId);
    }

    @GetMapping("/{pacienteId}/consultas")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Todas las consultas del paciente con diagnósticos y fórmulas (RFMT-009)")
    public List<ConsultaHistorialDto> historial(@PathVariable Long pacienteId) {
        return service.historial(pacienteId);
    }

    @GetMapping("/{pacienteId}/antecedentes")
    @PreAuthorize(LECTURA)
    public List<AntecedenteDto> antecedentes(@PathVariable Long pacienteId) {
        return service.antecedentes(pacienteId);
    }

    @PostMapping("/{pacienteId}/antecedentes")
    @PreAuthorize(REGISTRO)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una alergia, medicamento actual, enfermedad crónica, cirugía u otro antecedente")
    public AntecedenteDto agregarAntecedente(@PathVariable Long pacienteId, @Valid @RequestBody AntecedenteRequest request) {
        return service.agregarAntecedente(pacienteId, request);
    }

    @PostMapping("/antecedentes/{id}/inactivar")
    @PreAuthorize(REGISTRO)
    @Operation(summary = "Inactiva un antecedente con motivo; nunca se borra (RFMT-008)")
    public AntecedenteDto inactivarAntecedente(@PathVariable Long id, @Valid @RequestBody MotivoRequest request) {
        return service.inactivarAntecedente(id, request.motivo());
    }

    @GetMapping("/{pacienteId}/signos")
    @PreAuthorize(LECTURA)
    public List<SignosDto> signos(@PathVariable Long pacienteId) {
        return service.historialSignos(pacienteId);
    }

    @PostMapping("/{pacienteId}/signos")
    @PreAuthorize(REGISTRO)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra signos vitales y devuelve alertas según rangos parametrizados (RFMT-010)")
    public SignosDto registrarSignos(@PathVariable Long pacienteId, @Valid @RequestBody SignosRequest request) {
        return service.registrarSignos(pacienteId, request);
    }
}
