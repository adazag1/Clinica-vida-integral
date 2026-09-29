package co.edu.ucentral.clinica.patologia.dto;

import co.edu.ucentral.clinica.patologia.CustodiaPatologia;
import co.edu.ucentral.clinica.patologia.PlantillaPatologia;
import co.edu.ucentral.clinica.patologia.PrioridadCaso;
import co.edu.ucentral.clinica.patologia.TipoCaso;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** Cuerpos de las peticiones de patología. */
public final class PatologiaSolicitudes {

    private PatologiaSolicitudes() {
    }

    public record SolicitudCasoRequest(
            @NotNull(message = "Seleccione el paciente") Long pacienteId,
            Long consultaId,
            @NotNull(message = "Seleccione el tipo de estudio") TipoCaso tipo,
            @NotNull(message = "Seleccione la prioridad") PrioridadCaso prioridad,
            @NotBlank(message = "Indique el sitio anatómico") @Size(max = 150, message = "Máximo 150 caracteres") String sitioAnatomico,
            @Size(max = 1000, message = "Máximo 1000 caracteres") String datosClinicos,
            @NotNull(message = "Indique cuántas muestras se envían")
            @Min(value = 1, message = "Al menos una muestra") @Max(value = 20, message = "Máximo 20 muestras") Integer numeroMuestras) {
    }

    /** Control de identificación al recibir: lo que dice el rótulo del frasco se compara con el paciente. */
    public record RecepcionRequest(
            @NotBlank(message = "Digite el documento que aparece en el rótulo") String documentoRotulo,
            @NotNull(message = "Indique cuántas muestras llegaron") @Min(value = 1, message = "Al menos una muestra") Integer muestrasRecibidas,
            Long plantillaId,
            @Size(max = 300, message = "Máximo 300 caracteres") String observacion) {
    }

    public record ValoresRequest(@NotNull(message = "Registre los campos") Map<String, @Size(max = 2000) String> valores) {
    }

    public record MicroscopiaRequest(
            @NotNull(message = "Registre los campos") Map<String, @Size(max = 2000) String> valores,
            @Size(max = 2000, message = "Máximo 2000 caracteres") String diagnostico) {
    }

    public record ValidacionRequest(
            @NotBlank(message = "Confirme su contraseña") String password,
            @Size(max = 300, message = "Máximo 300 caracteres") String motivoCambio) {
    }

    public record CustodiaRequest(
            @NotNull(message = "Seleccione el material") CustodiaPatologia.Material material,
            @NotNull(message = "Seleccione el movimiento") CustodiaPatologia.Accion accion,
            @NotNull @Min(value = 1, message = "Al menos 1") @Max(value = 500, message = "Máximo 500") Integer cantidad,
            @Size(max = 150, message = "Máximo 150 caracteres") String ubicacion,
            @Size(max = 150, message = "Máximo 150 caracteres") String destinatario,
            @Size(max = 300, message = "Máximo 300 caracteres") String motivo) {
    }

    public record CampoRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{0,39}$", message = "Solo minúsculas, números y guion bajo") String clave,
            @NotBlank(message = "Escriba la etiqueta") @Size(max = 80) String etiqueta,
            @NotNull PlantillaPatologia.Seccion seccion,
            @NotNull PlantillaPatologia.TipoCampo tipo,
            List<@NotBlank @Size(max = 80) String> opciones,
            Boolean requerido,
            @Size(max = 20) String unidad) {
        public boolean esRequerido() {
            return Boolean.TRUE.equals(requerido);
        }
    }

    public record PlantillaRequest(
            @NotNull(message = "Seleccione el tipo de estudio") TipoCaso tipo,
            @Size(max = 80, message = "Máximo 80 caracteres") String servicio,
            @NotBlank(message = "Escriba el nombre") @Size(max = 120) String nombre,
            @NotEmpty(message = "Agregue al menos un campo") @Size(max = 40) List<@Valid CampoRequest> campos) {
    }
}
