package co.edu.ucentral.clinica.ia;

import co.edu.ucentral.clinica.ia.asistente.AsistenteService;
import co.edu.ucentral.clinica.ia.dto.IaDtos.AlertaDto;
import co.edu.ucentral.clinica.ia.dto.IaDtos.EstadoIaDto;
import co.edu.ucentral.clinica.ia.dto.IaDtos.ReglaDto;
import co.edu.ucentral.clinica.ia.dto.IaDtos.RespuestaAsistenteDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ia")
@Tag(name = "Motor de IA", description = "Alertas clínicas por reglas, retroalimentación, asistente de lenguaje y dataset anonimizado")
public class IaController {

    /** Quienes atienden al paciente ven y califican las alertas. */
    static final String CLINICO = "hasAnyRole('ADMIN','MEDICO','ENFERMERIA')";
    static final String GESTION = "hasAnyRole('ADMIN','DIRECTIVO')";

    public record RetroalimentacionRequest(
            @NotNull(message = "Indique si la alerta fue útil") AlertaIa.Retroalimentacion valor,
            @Size(max = 500, message = "Máximo 500 caracteres") String comentario) {
    }

    public record ReglaRequest(Boolean activa) {
        boolean esActiva() {
            return activa == null || activa;
        }
    }

    public record AsistenteRequest(@NotNull(message = "Indique qué necesita del asistente") AsistenteService.Tipo tipo) {
    }

    private final AlertasIaService alertas;
    private final AsistenteService asistente;
    private final DatasetService dataset;

    public IaController(AlertasIaService alertas, AsistenteService asistente, DatasetService dataset) {
        this.alertas = alertas;
        this.asistente = asistente;
        this.dataset = dataset;
    }

    @GetMapping("/pacientes/{pacienteId}/alertas")
    @PreAuthorize(CLINICO)
    @Operation(summary = "Reevalúa las reglas y devuelve las alertas del paciente, vigentes primero (US 4.2)")
    public List<AlertaDto> alertas(@PathVariable Long pacienteId, @RequestParam(defaultValue = "false") boolean historial) {
        return alertas.alertas(pacienteId, historial);
    }

    @PostMapping("/alertas/{id}/retroalimentacion")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
    @Operation(summary = "El médico califica la alerta como útil o no útil; base de la precisión por regla")
    public AlertaDto retroalimentar(@PathVariable Long id, @Valid @RequestBody RetroalimentacionRequest request) {
        return alertas.retroalimentar(id, request.valor(), request.comentario());
    }

    @GetMapping("/reglas")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTIVO','MEDICO')")
    @Operation(summary = "Reglas con su referencia clínica, estado y precisión según la retroalimentación")
    public List<ReglaDto> reglas() {
        return alertas.reglas();
    }

    @PutMapping("/reglas/{codigo}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activa o desactiva una regla")
    public ReglaDto cambiarRegla(@PathVariable String codigo, @RequestBody ReglaRequest request) {
        return alertas.cambiarRegla(codigo, request.esActiva());
    }

    @GetMapping("/estado")
    @Operation(summary = "Estado del motor y del asistente de lenguaje")
    public EstadoIaDto estado() {
        return asistente.estado();
    }

    @GetMapping("/pacientes/{pacienteId}/asistente/vista-previa")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
    @Operation(summary = "Texto anonimizado que se enviaría al modelo de lenguaje")
    public Map<String, String> vistaPrevia(@PathVariable Long pacienteId) {
        return Map.of("texto", asistente.vistaPrevia(pacienteId));
    }

    @PostMapping("/pacientes/{pacienteId}/asistente")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
    @Operation(summary = "Resumen o diagnóstico diferencial con Azure OpenAI; 422 IA_DESHABILITADA si está apagado")
    public RespuestaAsistenteDto consultarAsistente(@PathVariable Long pacienteId, @Valid @RequestBody AsistenteRequest request,
                                                    HttpServletRequest http) {
        return asistente.consultar(pacienteId, request.tipo(), http.getRemoteAddr());
    }

    @GetMapping("/dataset")
    @PreAuthorize(GESTION)
    @Operation(summary = "Dataset anonimizado en CSV (US 4.1); la descarga queda auditada")
    public ResponseEntity<byte[]> dataset(HttpServletRequest http) {
        DatasetService.Exportacion e = dataset.exportar(http.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dataset-clinico-anonimizado-" + LocalDate.now() + ".csv\"")
                .header("X-Filas", String.valueOf(e.filas()))
                .header("X-Pacientes", String.valueOf(e.pacientes()))
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(e.csv().getBytes(StandardCharsets.UTF_8));
    }
}
