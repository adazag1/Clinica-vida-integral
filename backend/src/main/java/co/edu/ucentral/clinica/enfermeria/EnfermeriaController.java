package co.edu.ucentral.clinica.enfermeria;

import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.EntregaDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.EventoAdversoDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.NotaDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.OrdenClinicaDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.OrdenesPacienteDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.PacienteCensoDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaDtos.ResumenTurnoDto;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaSolicitudes.EjecucionRequest;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaSolicitudes.EntregaRequest;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaSolicitudes.EventoAdversoRequest;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaSolicitudes.MotivoRequest;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaSolicitudes.NotaRequest;
import co.edu.ucentral.clinica.enfermeria.dto.EnfermeriaSolicitudes.OrdenClinicaRequest;
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
@RequestMapping("/api/enfermeria")
@Tag(name = "Enfermería", description = "Órdenes, administración de medicamentos, eventos adversos y entrega de turno")
public class EnfermeriaController {

    static final String LECTURA = "hasAnyRole('ADMIN','MEDICO','ENFERMERIA','AUDITORIA')";
    static final String ENFERMERIA = "hasAnyRole('ADMIN','ENFERMERIA')";
    static final String MEDICO = "hasRole('MEDICO')";

    private final EnfermeriaService service;

    public EnfermeriaController(EnfermeriaService service) {
        this.service = service;
    }

    @GetMapping("/censo")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERIA')")
    @Operation(summary = "Pacientes a cargo con dosis atrasadas, próximas y alertas, lo urgente primero")
    public List<PacienteCensoDto> censo() {
        return service.censo();
    }

    @GetMapping("/pacientes/{pacienteId}/ordenes")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Medicamentos, dietas, cuidados y exámenes del paciente con su horario (RFENF-002)")
    public OrdenesPacienteDto ordenesPaciente(@PathVariable Long pacienteId) {
        return service.ordenesPaciente(pacienteId);
    }

    @GetMapping("/ordenes")
    @PreAuthorize(LECTURA)
    public List<OrdenClinicaDto> deConsulta(@RequestParam Long consultaId) {
        return service.deConsulta(consultaId);
    }

    @PostMapping("/ordenes")
    @PreAuthorize(MEDICO)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "El médico ordena un medicamento con horario, una dieta o un cuidado (RFENF-005)")
    public OrdenClinicaDto crearOrden(@Valid @RequestBody OrdenClinicaRequest request) {
        return service.crearOrden(request);
    }

    @PostMapping("/ordenes/{id}/suspender")
    @PreAuthorize(MEDICO)
    @Operation(summary = "Suspende una orden; enfermería ya no puede ejecutarla (RFENF-009)")
    public OrdenClinicaDto suspender(@PathVariable Long id, @Valid @RequestBody MotivoRequest request) {
        return service.suspender(id, request.motivo());
    }

    @PostMapping("/ordenes/{id}/ejecuciones")
    @PreAuthorize(ENFERMERIA)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una dosis o cuidado: cinco correctos y doble verificación en alto riesgo (RFENF-003/004)")
    public OrdenClinicaDto ejecutar(@PathVariable Long id, @Valid @RequestBody EjecucionRequest request) {
        return service.ejecutar(id, request);
    }

    @GetMapping("/pacientes/{pacienteId}/eventos")
    @PreAuthorize(LECTURA)
    public List<EventoAdversoDto> eventos(@PathVariable Long pacienteId) {
        return service.eventosPaciente(pacienteId);
    }

    @PostMapping("/pacientes/{pacienteId}/eventos")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERIA')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reporta un evento adverso o incidente (RFENF-007)")
    public EventoAdversoDto reportarEvento(@PathVariable Long pacienteId, @Valid @RequestBody EventoAdversoRequest request) {
        return service.reportarEvento(pacienteId, request);
    }

    @GetMapping("/eventos")
    @PreAuthorize("hasAnyRole('ADMIN','ENFERMERIA','AUDITORIA')")
    @Operation(summary = "Eventos adversos de los últimos días, para seguridad del paciente")
    public List<EventoAdversoDto> eventosRecientes(@RequestParam(defaultValue = "30") int dias) {
        return service.eventosRecientes(Math.clamp(dias, 1, 365));
    }

    @GetMapping("/pacientes/{pacienteId}/notas")
    @PreAuthorize(LECTURA)
    public List<NotaDto> notas(@PathVariable Long pacienteId) {
        return service.notas(pacienteId);
    }

    @PostMapping("/pacientes/{pacienteId}/notas")
    @PreAuthorize(ENFERMERIA)
    @ResponseStatus(HttpStatus.CREATED)
    public NotaDto agregarNota(@PathVariable Long pacienteId, @Valid @RequestBody NotaRequest request) {
        return service.agregarNota(pacienteId, request.texto());
    }

    @GetMapping("/pacientes/{pacienteId}/resumen-turno")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Resumen automático del paciente en el turno actual (RFENF-008)")
    public ResumenTurnoDto resumenTurno(@PathVariable Long pacienteId) {
        return service.resumenTurno(pacienteId);
    }

    @GetMapping("/pacientes/{pacienteId}/entregas")
    @PreAuthorize(LECTURA)
    public List<EntregaDto> entregas(@PathVariable Long pacienteId) {
        return service.entregas(pacienteId);
    }

    @PostMapping("/pacientes/{pacienteId}/entregas")
    @PreAuthorize(ENFERMERIA)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Entrega el turno guardando el resumen tal como está (RFENF-008)")
    public EntregaDto entregar(@PathVariable Long pacienteId, @Valid @RequestBody EntregaRequest request) {
        return service.entregarTurno(pacienteId, request.nota());
    }
}
