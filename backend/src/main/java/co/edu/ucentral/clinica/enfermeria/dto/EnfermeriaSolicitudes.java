package co.edu.ucentral.clinica.enfermeria.dto;

import co.edu.ucentral.clinica.enfermeria.EjecucionOrden;
import co.edu.ucentral.clinica.enfermeria.EventoAdverso;
import co.edu.ucentral.clinica.enfermeria.OrdenClinica;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Cuerpos de las peticiones de enfermería. */
public final class EnfermeriaSolicitudes {

    private EnfermeriaSolicitudes() {
    }

    /**
     * Orden del médico. Un medicamento exige medicamento, dosis y vía; una dieta o cuidado, la descripción.
     *
     * @param frecuenciaHoras cada cuántas horas; vacío = una sola vez (o continua para dietas)
     * @param inicio          vacío = ahora
     */
    public record OrdenClinicaRequest(
            @NotNull(message = "Seleccione el paciente") Long pacienteId,
            Long consultaId,
            @NotNull(message = "Seleccione el tipo de orden") OrdenClinica.Tipo tipo,
            @Size(max = 300, message = "Máximo 300 caracteres") String descripcion,
            Long medicamentoId,
            @Size(max = 60, message = "Máximo 60 caracteres") String dosis,
            @Size(max = 40, message = "Máximo 40 caracteres") String via,
            @Min(value = 1, message = "Entre 1 y 72 horas") @Max(value = 72, message = "Entre 1 y 72 horas") Integer frecuenciaHoras,
            Instant inicio,
            @NotNull(message = "Indique la duración")
            @Min(value = 1, message = "Entre 1 y 720 horas") @Max(value = 720, message = "Entre 1 y 720 horas") Integer duracionHoras,
            @Size(max = 500, message = "Máximo 500 caracteres") String indicaciones) {
    }

    public record MotivoRequest(
            @NotBlank(message = "Ingrese el motivo") @Size(max = 300, message = "Máximo 300 caracteres") String motivo) {
    }

    /** RFENF-003: lo que la enfermera confirma frente al paciente (idealmente leyendo la manilla). */
    public record VerificacionRequest(
            @NotBlank(message = "Confirme el documento del paciente") String documentoPaciente,
            @NotNull(message = "Confirme el medicamento") Long medicamentoId,
            @NotBlank(message = "Confirme la dosis") String dosis,
            @NotBlank(message = "Confirme la vía") String via) {
    }

    /** Segundo profesional que verifica un medicamento de alto riesgo. */
    public record DobleValidacionRequest(
            @NotBlank(message = "Ingrese el usuario de quien verifica") String usuario,
            @NotBlank(message = "Ingrese la contraseña de quien verifica") String password) {
    }

    public record EjecucionRequest(
            @NotNull(message = "Indique la hora programada") Instant programadaPara,
            @NotNull(message = "Indique el resultado") EjecucionOrden.Resultado resultado,
            @Valid VerificacionRequest verificacion,
            @Size(max = 300, message = "Máximo 300 caracteres") String motivo,
            @Size(max = 300, message = "Máximo 300 caracteres") String justificacionRetraso,
            @Valid DobleValidacionRequest dobleValidacion) {
    }

    public record EventoAdversoRequest(
            @NotNull(message = "Seleccione el tipo de evento") EventoAdverso.Tipo tipo,
            @NotNull(message = "Seleccione la severidad") EventoAdverso.Severidad severidad,
            @NotNull(message = "Indique cuándo ocurrió") @PastOrPresent(message = "No puede ser una fecha futura") Instant ocurridoEn,
            @Size(max = 100, message = "Máximo 100 caracteres") String lugar,
            @NotBlank(message = "Describa lo ocurrido") @Size(max = 2000, message = "Máximo 2000 caracteres") String descripcion,
            @NotBlank(message = "Registre las acciones inmediatas") @Size(max = 1000, message = "Máximo 1000 caracteres") String accionesInmediatas,
            @Size(max = 150, message = "Máximo 150 caracteres") String medicoNotificado,
            Long ordenId) {
    }

    public record NotaRequest(
            @NotBlank(message = "Escriba la nota") @Size(max = 2000, message = "Máximo 2000 caracteres") String texto) {
    }

    public record EntregaRequest(@Size(max = 2000, message = "Máximo 2000 caracteres") String nota) {
    }
}
