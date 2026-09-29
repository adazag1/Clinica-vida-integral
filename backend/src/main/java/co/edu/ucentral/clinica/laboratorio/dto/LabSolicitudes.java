package co.edu.ucentral.clinica.laboratorio.dto;

import co.edu.ucentral.clinica.laboratorio.AreaLab;
import co.edu.ucentral.clinica.laboratorio.MotivoLab;
import co.edu.ucentral.clinica.laboratorio.PrioridadLab;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Cuerpos de las peticiones del laboratorio. */
public final class LabSolicitudes {

    private LabSolicitudes() {
    }

    public record OrdenRequest(
            @NotNull(message = "Seleccione el paciente") Long pacienteId,
            Long consultaId,
            @NotNull(message = "Seleccione la prioridad") PrioridadLab prioridad,
            @NotEmpty(message = "Seleccione al menos un examen") List<@NotNull Long> examenIds,
            @Size(max = 500, message = "Máximo 500 caracteres") String indicaciones) {
    }

    /** @param tomadaEn si no se envía, se toma la hora de recepción */
    public record MuestraRequest(
            Instant tomadaEn,
            @NotBlank(message = "Indique quién tomó la muestra") @Size(max = 60, message = "Máximo 60 caracteres") String tomadaPor) {
    }

    public record RechazoRequest(
            @NotNull(message = "Seleccione el motivo") MotivoLab.Rechazo motivo,
            @Size(max = 300, message = "Máximo 300 caracteres") String detalle) {
    }

    public record TrazabilidadRequest(
            @NotNull Long ordenExamenId,
            @Size(max = 80) String equipo,
            @Size(max = 60) String loteReactivo,
            @Size(max = 80) String calibracion) {
    }

    public record ValorRequest(
            @NotNull Long analitoId,
            @NotNull(message = "Ingrese el valor") @DecimalMin(value = "0", message = "No puede ser negativo") BigDecimal valor) {
    }

    public record ResultadosRequest(
            @NotNull List<@Valid TrazabilidadRequest> trazabilidad,
            @NotNull List<@Valid ValorRequest> valores) {
    }

    public record RepeticionRequest(
            @NotNull(message = "Ingrese el nuevo valor") @DecimalMin(value = "0", message = "No puede ser negativo") BigDecimal valor,
            @NotNull(message = "Seleccione el motivo") MotivoLab.Reproceso motivo,
            @Size(max = 300, message = "Máximo 300 caracteres") String detalle) {
    }

    /**
     * @param contactoCritico a quién se informó el valor crítico (obligatorio si hay críticos)
     * @param medioCritico    llamada, en persona...
     */
    public record ValidacionRequest(
            @Size(max = 150, message = "Máximo 150 caracteres") String contactoCritico,
            @Size(max = 30, message = "Máximo 30 caracteres") String medioCritico) {
    }

    public record ControlCalidadRequest(
            @NotNull(message = "Seleccione el área") AreaLab area,
            @NotBlank(message = "Ingrese el equipo") @Size(max = 80) String equipo,
            @NotBlank(message = "Ingrese los niveles corridos") @Size(max = 60) String niveles,
            @NotBlank(message = "Ingrese el lote del control") @Size(max = 60) String loteControl,
            @NotNull(message = "Indique si el control fue aceptado") Boolean aceptado,
            @Size(max = 300) String observacion) {
    }

    /** RFLC-014: vacío = sin límite por ese lado. */
    public record RangoRequest(BigDecimal refMin, BigDecimal refMax, BigDecimal criticoMin, BigDecimal criticoMax) {
    }
}
