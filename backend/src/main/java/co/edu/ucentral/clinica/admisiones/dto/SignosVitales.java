package co.edu.ucentral.clinica.admisiones.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * RFADM-005: signos vitales estructurados. Los rangos rechazan valores fisiológicamente
 * imposibles (errores de digitación como FC 800 o temperatura 3,7); los valores anormales
 * pero posibles sí se aceptan y son los que suben el nivel de triage.
 */
public record SignosVitales(
        @NotNull(message = "Ingrese la frecuencia cardiaca")
        @Min(value = 20, message = "Entre 20 y 250 lpm") @Max(value = 250, message = "Entre 20 y 250 lpm")
        Integer frecuenciaCardiaca,

        @NotNull(message = "Ingrese la frecuencia respiratoria")
        @Min(value = 4, message = "Entre 4 y 70 rpm") @Max(value = 70, message = "Entre 4 y 70 rpm")
        Integer frecuenciaRespiratoria,

        @NotNull(message = "Ingrese la presión sistólica")
        @Min(value = 40, message = "Entre 40 y 300 mmHg") @Max(value = 300, message = "Entre 40 y 300 mmHg")
        Integer presionSistolica,

        @NotNull(message = "Ingrese la presión diastólica")
        @Min(value = 20, message = "Entre 20 y 200 mmHg") @Max(value = 200, message = "Entre 20 y 200 mmHg")
        Integer presionDiastolica,

        @NotNull(message = "Ingrese la temperatura")
        @DecimalMin(value = "30.0", message = "Entre 30 y 45 °C") @DecimalMax(value = "45.0", message = "Entre 30 y 45 °C")
        BigDecimal temperatura,

        @NotNull(message = "Ingrese la saturación de oxígeno")
        @Min(value = 50, message = "Entre 50 y 100 %") @Max(value = 100, message = "Entre 50 y 100 %")
        Integer saturacionOxigeno,

        @NotNull(message = "Ingrese la escala de Glasgow")
        @Min(value = 3, message = "Entre 3 y 15") @Max(value = 15, message = "Entre 3 y 15")
        Integer glasgow,

        @NotNull(message = "Ingrese la escala de dolor")
        @Min(value = 0, message = "Entre 0 y 10") @Max(value = 10, message = "Entre 0 y 10")
        Integer dolor) {

    @JsonIgnore
    @AssertTrue(message = "La presión diastólica debe ser menor que la sistólica")
    public boolean isPresionCoherente() {
        return presionSistolica == null || presionDiastolica == null || presionDiastolica < presionSistolica;
    }
}
