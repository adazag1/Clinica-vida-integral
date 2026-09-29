package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.agenda.dto.AgendaDiaDto;
import co.edu.ucentral.clinica.agenda.dto.CatalogosDto;
import co.edu.ucentral.clinica.agenda.dto.IndicadoresDto;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.BloqueoRequest;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.FranjaRequest;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.HorarioRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/agenda")
@Tag(name = "Agenda", description = "Disponibilidad, agenda del día, indicadores, horarios y bloqueos")
public class AgendaController {

    static final String LECTURA = "hasAnyRole('ADMIN','AGENDAMIENTO','ADMISIONES','MEDICO')";
    static final String ESCRITURA = "hasAnyRole('ADMIN','AGENDAMIENTO','ADMISIONES')";
    /** Horarios y bloqueos cambian la oferta de la clínica: solo coordinación de agendamiento. */
    static final String PARAMETRIZACION = "hasAnyRole('ADMIN','AGENDAMIENTO')";

    private final AgendaService service;

    public AgendaController(AgendaService service) {
        this.service = service;
    }

    @GetMapping("/catalogos")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Sedes, especialidades, tipos de cita y profesionales")
    public CatalogosDto catalogos(Authentication auth) {
        return service.catalogos(auth.getName());
    }

    @GetMapping("/dia")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Agenda de un día por profesional: franjas, bloqueos y citas")
    public AgendaDiaDto dia(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                            @RequestParam(required = false) Long sedeId,
                            @RequestParam(required = false) Long especialidadId,
                            @RequestParam(required = false) Long profesionalId) {
        return service.dia(fecha, sedeId, especialidadId, profesionalId);
    }

    @GetMapping("/disponibilidad")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Cupos libres por profesional o especialidad (RFAGE-003)")
    public List<AgendaService.CupoDto> disponibilidad(
            @RequestParam(required = false) Long profesionalId,
            @RequestParam(required = false) Long especialidadId,
            @RequestParam(required = false) Long sedeId,
            @RequestParam Long tipoCitaId,
            @RequestParam(defaultValue = "NORMAL") PrioridadCita prioridad,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(defaultValue = "7") int dias) {
        return service.disponibilidad(profesionalId, especialidadId, sedeId, tipoCitaId, prioridad, desde, dias);
    }

    @GetMapping("/indicadores")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Resumen del día y carga por profesional (RFAGE-011)")
    public IndicadoresDto indicadores(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                      @RequestParam(required = false) Long sedeId) {
        return service.indicadores(fecha, sedeId);
    }

    @GetMapping("/horarios/{profesionalId}")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Horario semanal de atención del profesional (RFAGE-015)")
    public List<FranjaRequest> horario(@PathVariable Long profesionalId) {
        return service.horario(profesionalId);
    }

    @PutMapping("/horarios/{profesionalId}")
    @PreAuthorize(PARAMETRIZACION)
    @Operation(summary = "Reemplaza el horario semanal del profesional")
    public List<FranjaRequest> reemplazarHorario(@PathVariable Long profesionalId,
                                                 @Valid @RequestBody HorarioRequest request) {
        return service.reemplazarHorario(profesionalId, request.franjas());
    }

    @GetMapping("/bloqueos")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Bloqueos de agenda vigentes y futuros")
    public List<AgendaDiaDto.BloqueoDto> bloqueos() {
        return service.bloqueosVigentes();
    }

    @PostMapping("/bloqueos")
    @PreAuthorize(PARAMETRIZACION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Bloquea la agenda por vacaciones, permisos o eventos (RFAGE-014)")
    public AgendaDiaDto.BloqueoDto bloquear(@Valid @RequestBody BloqueoRequest request) {
        return service.bloquear(request);
    }

    @DeleteMapping("/bloqueos/{id}")
    @PreAuthorize(PARAMETRIZACION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desbloquear(@PathVariable Long id) {
        service.desbloquear(id);
    }
}
