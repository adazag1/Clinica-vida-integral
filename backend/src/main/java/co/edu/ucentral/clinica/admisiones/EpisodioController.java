package co.edu.ucentral.clinica.admisiones;

import co.edu.ucentral.clinica.admisiones.dto.CoberturaRequest;
import co.edu.ucentral.clinica.admisiones.dto.ConsentimientoRequest;
import co.edu.ucentral.clinica.admisiones.dto.EpisodioDto;
import co.edu.ucentral.clinica.admisiones.dto.EpisodioRequest;
import co.edu.ucentral.clinica.admisiones.dto.JustificacionRequest;
import co.edu.ucentral.clinica.admisiones.dto.ListaEsperaDto;
import co.edu.ucentral.clinica.admisiones.dto.SugerenciaRequest;
import co.edu.ucentral.clinica.admisiones.dto.SugerenciaTriage;
import co.edu.ucentral.clinica.admisiones.dto.TriageRequest;
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
@RequestMapping("/api/episodios")
@Tag(name = "Admisiones y triage", description = "Episodios de atención, cobertura, consentimientos, triage y lista de espera")
public class EpisodioController {

    private static final String LECTURA = "hasAnyRole('ADMIN','ADMISIONES','MEDICO','ENFERMERIA')";
    private static final String ADMISION = "hasAnyRole('ADMIN','ADMISIONES')";
    /** El triage lo hace personal asistencial o de admisiones entrenado. */
    private static final String CLINICO = "hasAnyRole('ADMIN','ADMISIONES','ENFERMERIA','MEDICO')";

    private final EpisodioService service;

    public EpisodioController(EpisodioService service) {
        this.service = service;
    }

    @GetMapping("/lista-espera")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Pacientes pendientes de triage, en admisión y en espera de consulta (RFADM-013)")
    public ListaEsperaDto listaEspera() {
        return service.listaEspera();
    }

    @GetMapping
    @PreAuthorize(LECTURA)
    @Operation(summary = "Atenciones de un paciente, de la más reciente a la más antigua")
    public List<EpisodioDto> dePaciente(@RequestParam Long pacienteId) {
        return service.dePaciente(pacienteId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Detalle de la atención con triage, consentimientos y requisitos pendientes")
    public EpisodioDto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @PreAuthorize(ADMISION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Admite al paciente y genera el número de atención (RFADM-004)")
    public EpisodioDto crear(@Valid @RequestBody EpisodioRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}/cobertura")
    @PreAuthorize(ADMISION)
    @Operation(summary = "Registra la verificación de cobertura con la aseguradora (RFADM-008)")
    public EpisodioDto cobertura(@PathVariable Long id, @Valid @RequestBody CoberturaRequest request) {
        return service.registrarCobertura(id, request);
    }

    @PutMapping("/{id}/excepcion-urgencia")
    @PreAuthorize(CLINICO)
    @Operation(summary = "Autoriza atender la urgencia sin esperar la validación administrativa (RFADM-009)")
    public EpisodioDto excepcionUrgencia(@PathVariable Long id, @Valid @RequestBody JustificacionRequest request) {
        return service.registrarExcepcionUrgencia(id, request.justificacion());
    }

    @PostMapping("/{id}/consentimientos")
    @PreAuthorize(CLINICO)
    @Operation(summary = "Registra un consentimiento con fecha, hora y responsable (RFADM-007)")
    public EpisodioDto consentimiento(@PathVariable Long id, @Valid @RequestBody ConsentimientoRequest request) {
        return service.registrarConsentimiento(id, request);
    }

    @PostMapping("/triage/sugerencia")
    @PreAuthorize(CLINICO)
    @Operation(summary = "Nivel de triage sugerido para unos signos vitales, sin guardar (RFADM-006)")
    public SugerenciaTriage sugerencia(@Valid @RequestBody SugerenciaRequest request) {
        return service.sugerirTriage(request.signos(), request.edad());
    }

    @PutMapping("/{id}/triage")
    @PreAuthorize(CLINICO)
    @Operation(summary = "Clasifica o reclasifica el triage con signos vitales estructurados (RFADM-005/006)")
    public EpisodioDto triage(@PathVariable Long id, @Valid @RequestBody TriageRequest request) {
        return service.clasificarTriage(id, request);
    }

    @PostMapping("/{id}/enviar-a-consulta")
    @PreAuthorize(CLINICO)
    @Operation(summary = "Pasa el paciente a la lista del médico si no le falta ningún requisito (RFADM-012)")
    public EpisodioDto enviarAConsulta(@PathVariable Long id) {
        return service.enviarAConsulta(id);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize(ADMISION)
    @Operation(summary = "Anula una atención registrada por error, con justificación")
    public EpisodioDto anular(@PathVariable Long id, @Valid @RequestBody JustificacionRequest request) {
        return service.anular(id, request.justificacion());
    }
}
