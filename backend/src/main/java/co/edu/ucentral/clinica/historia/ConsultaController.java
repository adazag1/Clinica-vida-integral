package co.edu.ucentral.clinica.historia;

import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.ConsultaDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.PacientePorAtenderDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.VerificacionFirmaDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.FirmaRequest;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.IniciarConsultaRequest;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.NotasRequest;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.PrescripcionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/consultas")
@Tag(name = "Consulta médica", description = "Atención del médico: notas, diagnósticos, fórmula y firma")
public class ConsultaController {

    /** Solo el médico escribe y firma la consulta. */
    private static final String MEDICO = "hasRole('MEDICO')";

    private final ConsultaService service;

    public ConsultaController(ConsultaService service) {
        this.service = service;
    }

    @GetMapping("/por-atender")
    @PreAuthorize(MEDICO)
    @Operation(summary = "Lista de trabajo del médico, críticos primero (RFMT-012)")
    public List<PacientePorAtenderDto> porAtender() {
        return service.porAtender();
    }

    @PostMapping
    @PreAuthorize(MEDICO)
    @Operation(summary = "Abre (o retoma) la consulta de un paciente en espera o con cita")
    public ConsultaDto iniciar(@Valid @RequestBody IniciarConsultaRequest request) {
        return service.iniciar(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize(HistoriaController.LECTURA)
    public ConsultaDto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MEDICO)
    @Operation(summary = "Guarda notas y diagnósticos (se puede guardar incompleta)")
    public ConsultaDto guardar(@PathVariable Long id, @Valid @RequestBody NotasRequest request) {
        return service.guardar(id, request);
    }

    @PostMapping("/{id}/prescripciones")
    @PreAuthorize(MEDICO)
    @Operation(summary = "Agrega un medicamento a la fórmula; se bloquea si el paciente es alérgico (RFMT-003)")
    public ConsultaDto agregarPrescripcion(@PathVariable Long id, @Valid @RequestBody PrescripcionRequest request) {
        return service.agregarPrescripcion(id, request);
    }

    @DeleteMapping("/{id}/prescripciones/{prescripcionId}")
    @PreAuthorize(MEDICO)
    public ConsultaDto quitarPrescripcion(@PathVariable Long id, @PathVariable Long prescripcionId) {
        return service.quitarPrescripcion(id, prescripcionId);
    }

    @PostMapping("/{id}/finalizar")
    @PreAuthorize(MEDICO)
    @Operation(summary = "Firma y cierra la consulta si están los campos obligatorios (RFMT-007 / RFMT-011)")
    public ConsultaDto finalizar(@PathVariable Long id, @Valid @RequestBody FirmaRequest request) {
        return service.finalizar(id, request.password());
    }

    @GetMapping("/{id}/verificar-firma")
    @PreAuthorize(HistoriaController.LECTURA)
    @Operation(summary = "Comprueba que la consulta firmada no haya sido alterada")
    public VerificacionFirmaDto verificarFirma(@PathVariable Long id) {
        return service.verificarFirma(id);
    }
}
