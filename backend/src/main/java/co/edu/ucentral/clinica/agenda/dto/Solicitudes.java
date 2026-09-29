package co.edu.ucentral.clinica.agenda.dto;

import co.edu.ucentral.clinica.agenda.BloqueoAgenda;
import co.edu.ucentral.clinica.agenda.HorarioProfesional;
import co.edu.ucentral.clinica.agenda.MotivoCancelacion;
import co.edu.ucentral.clinica.agenda.PrioridadCita;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** Cuerpos de las peticiones del módulo de agenda. */
public final class Solicitudes {

    private Solicitudes() {
    }

    /** El fin no se envía: lo calcula el tipo de cita (RFAGE-004). */
    public record CitaRequest(
            @NotNull(message = "Seleccione el paciente") Long pacienteId,
            @NotNull(message = "Seleccione el profesional") Long profesionalId,
            @NotNull(message = "Seleccione el tipo de cita") Long tipoCitaId,
            @NotNull(message = "Seleccione el horario") Instant inicio,
            @NotNull(message = "Seleccione la prioridad") PrioridadCita prioridad,
            @Size(max = 500, message = "Máximo 500 caracteres") String motivo) {
    }

    public record CancelacionRequest(
            @NotNull(message = "Seleccione el motivo") MotivoCancelacion motivo,
            @Size(max = 500, message = "Máximo 500 caracteres") String detalle) {
    }

    /** @param profesionalId otro profesional; si es null se mantiene el mismo */
    public record ReprogramacionRequest(
            @NotNull(message = "Seleccione el nuevo horario") Instant inicio,
            Long profesionalId,
            @Size(max = 500, message = "Máximo 500 caracteres") String detalle) {
    }

    public record AsistenciaRequest(@NotNull(message = "Indique si asistió") Boolean asistio) {
    }

    public record BloqueoRequest(
            @NotNull(message = "Seleccione el profesional") Long profesionalId,
            @NotNull(message = "Ingrese el inicio") Instant inicio,
            @NotNull(message = "Ingrese el fin") Instant fin,
            @NotNull(message = "Seleccione el motivo") BloqueoAgenda.Motivo motivo,
            @Size(max = 300, message = "Máximo 300 caracteres") String descripcion) {
    }

    public record FranjaRequest(
            @NotNull @Min(value = 1, message = "Día entre 1 y 7") @Max(value = 7, message = "Día entre 1 y 7") Integer dia,
            @NotNull(message = "Ingrese la hora de inicio") LocalTime horaInicio,
            @NotNull(message = "Ingrese la hora de fin") LocalTime horaFin,
            @NotNull HorarioProfesional.Tipo tipo) {
    }

    public record HorarioRequest(@NotNull List<@Valid FranjaRequest> franjas) {
    }

    public record ListaEsperaRequest(
            @NotNull(message = "Seleccione el paciente") Long pacienteId,
            @NotNull(message = "Seleccione la especialidad") Long especialidadId,
            Long profesionalId,
            @NotNull(message = "Seleccione el tipo de cita") Long tipoCitaId,
            @NotNull(message = "Seleccione la prioridad") PrioridadCita prioridad,
            @Size(max = 500, message = "Máximo 500 caracteres") String motivo) {
    }
}
