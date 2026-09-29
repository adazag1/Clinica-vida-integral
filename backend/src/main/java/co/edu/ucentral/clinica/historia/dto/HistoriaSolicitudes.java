package co.edu.ucentral.clinica.historia.dto;

import co.edu.ucentral.clinica.historia.Antecedente;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Cuerpos de las peticiones del módulo de historia clínica. */
public final class HistoriaSolicitudes {

    private HistoriaSolicitudes() {
    }

    public record AntecedenteRequest(
            @NotNull(message = "Seleccione el tipo") Antecedente.Tipo tipo,
            @NotBlank(message = "Ingrese la descripción") @Size(max = 300, message = "Máximo 300 caracteres") String descripcion,
            @Size(max = 500, message = "Máximo 500 caracteres") String detalle,
            Antecedente.Severidad severidad) {
    }

    public record MotivoRequest(
            @NotBlank(message = "Ingrese el motivo") @Size(max = 300, message = "Máximo 300 caracteres") String motivo) {
    }

    /** Los mismos límites de lo fisiológicamente posible que el triage; todos opcionales. */
    public record SignosRequest(
            Long consultaId,
            @Min(value = 40, message = "Entre 40 y 300 mmHg") @Max(value = 300, message = "Entre 40 y 300 mmHg") Integer presionSistolica,
            @Min(value = 20, message = "Entre 20 y 200 mmHg") @Max(value = 200, message = "Entre 20 y 200 mmHg") Integer presionDiastolica,
            @Min(value = 20, message = "Entre 20 y 250 lpm") @Max(value = 250, message = "Entre 20 y 250 lpm") Integer frecuenciaCardiaca,
            @Min(value = 4, message = "Entre 4 y 70 rpm") @Max(value = 70, message = "Entre 4 y 70 rpm") Integer frecuenciaRespiratoria,
            @DecimalMin(value = "30.0", message = "Entre 30 y 45 °C") @DecimalMax(value = "45.0", message = "Entre 30 y 45 °C") BigDecimal temperatura,
            @Min(value = 50, message = "Entre 50 y 100 %") @Max(value = 100, message = "Entre 50 y 100 %") Integer saturacionOxigeno,
            @DecimalMin(value = "0.5", message = "Entre 0,5 y 400 kg") @DecimalMax(value = "400", message = "Entre 0,5 y 400 kg") BigDecimal peso,
            @Min(value = 20, message = "Entre 20 y 250 cm") @Max(value = 250, message = "Entre 20 y 250 cm") Integer talla) {
    }

    /** Se inicia desde la lista de espera de admisiones (episodio) o desde una cita de la agenda. */
    public record IniciarConsultaRequest(@NotNull(message = "Seleccione el paciente") Long pacienteId, Long episodioId,
                                         Long citaId) {
    }

    public record DiagnosticoRequest(@NotNull Long diagnosticoId, boolean principal, boolean confirmado) {
    }

    public record NotasRequest(
            @Size(max = 1000, message = "Máximo 1000 caracteres") String motivoConsulta,
            @Size(max = 4000, message = "Máximo 4000 caracteres") String enfermedadActual,
            @Size(max = 4000, message = "Máximo 4000 caracteres") String examenFisico,
            @Size(max = 4000, message = "Máximo 4000 caracteres") String plan,
            @NotNull List<@Valid DiagnosticoRequest> diagnosticos) {
    }

    public record PrescripcionRequest(
            @NotNull(message = "Seleccione el medicamento") Long medicamentoId,
            @NotBlank(message = "Ingrese la dosis") @Size(max = 60, message = "Máximo 60 caracteres") String dosis,
            @NotBlank(message = "Ingrese la frecuencia") @Size(max = 60, message = "Máximo 60 caracteres") String frecuencia,
            @NotNull(message = "Ingrese la duración")
            @Min(value = 1, message = "Entre 1 y 365 días") @Max(value = 365, message = "Entre 1 y 365 días") Integer duracionDias,
            @Size(max = 500, message = "Máximo 500 caracteres") String indicaciones) {
    }

    /** RFMT-011: el médico confirma su identidad con la contraseña para firmar. */
    public record FirmaRequest(@NotBlank(message = "Ingrese su contraseña para firmar") String password) {
    }
}
