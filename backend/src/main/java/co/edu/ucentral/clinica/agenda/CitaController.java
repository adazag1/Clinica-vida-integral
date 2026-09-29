package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.agenda.dto.CitaDto;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.AsistenciaRequest;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.CancelacionRequest;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.CitaRequest;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.ListaEsperaRequest;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.ReprogramacionRequest;
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
@RequestMapping("/api")
@Tag(name = "Citas", description = "Agendar, confirmar, cancelar, reprogramar y lista de espera")
public class CitaController {

    /** El médico registra la asistencia de sus pacientes. */
    private static final String ASISTENCIA = "hasAnyRole('ADMIN','AGENDAMIENTO','ADMISIONES','MEDICO')";

    private final CitaService citas;
    private final ListaEsperaService listaEspera;
    private final RecordatorioService recordatorios;

    public CitaController(CitaService citas, ListaEsperaService listaEspera, RecordatorioService recordatorios) {
        this.citas = citas;
        this.listaEspera = listaEspera;
        this.recordatorios = recordatorios;
    }

    @PostMapping("/citas")
    @PreAuthorize(AgendaController.ESCRITURA)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agenda una cita; la duración la fija el tipo de cita (RFAGE-004/005)")
    public CitaDto crear(@Valid @RequestBody CitaRequest request) {
        return citas.crear(request);
    }

    @GetMapping("/citas/{id}")
    @PreAuthorize(AgendaController.LECTURA)
    @Operation(summary = "Detalle de la cita con historial y recordatorios")
    public CitaDto obtener(@PathVariable Long id) {
        return citas.obtener(id);
    }

    @GetMapping("/citas")
    @PreAuthorize(AgendaController.LECTURA)
    @Operation(summary = "Citas de un paciente, de la más reciente a la más antigua")
    public List<CitaDto> dePaciente(@RequestParam Long pacienteId) {
        return citas.dePaciente(pacienteId);
    }

    @PostMapping("/citas/{id}/confirmar")
    @PreAuthorize(AgendaController.ESCRITURA)
    public CitaDto confirmar(@PathVariable Long id) {
        return citas.confirmar(id);
    }

    @PostMapping("/citas/{id}/cancelar")
    @PreAuthorize(AgendaController.ESCRITURA)
    @Operation(summary = "Cancela con motivo estructurado y ofrece el cupo a la lista de espera (RFAGE-007/008)")
    public CitaService.ResultadoCupoLiberado cancelar(@PathVariable Long id,
                                                      @Valid @RequestBody CancelacionRequest request) {
        return citas.cancelar(id, request);
    }

    @PostMapping("/citas/{id}/reprogramar")
    @PreAuthorize(AgendaController.ESCRITURA)
    @Operation(summary = "Mueve la cita a otro horario conservando el historial (RFAGE-008)")
    public CitaService.ResultadoCupoLiberado reprogramar(@PathVariable Long id,
                                                         @Valid @RequestBody ReprogramacionRequest request) {
        return citas.reprogramar(id, request);
    }

    @PostMapping("/citas/{id}/asistencia")
    @PreAuthorize(ASISTENCIA)
    public CitaDto asistencia(@PathVariable Long id, @Valid @RequestBody AsistenciaRequest request) {
        return citas.registrarAsistencia(id, request.asistio());
    }

    @PostMapping("/citas/{id}/recordatorio")
    @PreAuthorize(AgendaController.ESCRITURA)
    @Operation(summary = "Envía el recordatorio ahora (RFAGE-009)")
    public CitaDto recordatorio(@PathVariable Long id) {
        return recordatorios.enviarManual(id);
    }

    @GetMapping("/lista-espera")
    @PreAuthorize(AgendaController.LECTURA)
    @Operation(summary = "Pacientes en lista de espera, en el orden en que recibirán cupo (RFAGE-007)")
    public List<ListaEsperaService.EntradaDto> listaEspera() {
        return listaEspera.activas();
    }

    @PostMapping("/lista-espera")
    @PreAuthorize(AgendaController.ESCRITURA)
    @ResponseStatus(HttpStatus.CREATED)
    public ListaEsperaService.EntradaDto agregarListaEspera(@Valid @RequestBody ListaEsperaRequest request) {
        return listaEspera.agregar(request);
    }

    @PostMapping("/lista-espera/{id}/retirar")
    @PreAuthorize(AgendaController.ESCRITURA)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirarListaEspera(@PathVariable Long id) {
        listaEspera.retirar(id);
    }
}
