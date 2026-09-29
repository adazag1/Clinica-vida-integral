package co.edu.ucentral.clinica.imagenes.dto;

import co.edu.ucentral.clinica.imagenes.PrioridadEstudio;
import co.edu.ucentral.clinica.imagenes.SegundaOpinion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpos de las peticiones de imágenes diagnósticas. */
public final class ImagenesSolicitudes {

    private ImagenesSolicitudes() {
    }

    public record OrdenEstudioRequest(
            @NotNull(message = "Seleccione el paciente") Long pacienteId,
            Long consultaId,
            @NotNull(message = "Seleccione el estudio") Long tipoEstudioId,
            @NotNull(message = "Seleccione la prioridad") PrioridadEstudio prioridad,
            @NotBlank(message = "Indique el motivo del estudio") @Size(max = 500, message = "Máximo 500 caracteres") String motivo,
            @Size(max = 300, message = "Máximo 300 caracteres") String diagnosticoPresuntivo) {
    }

    public record TipoEstudioRequest(
            @NotNull @Min(value = 5, message = "Mínimo 5 minutos") @Max(value = 43200, message = "Máximo 30 días") Integer tatUrgenteMin,
            @NotNull @Min(value = 5, message = "Mínimo 5 minutos") @Max(value = 43200, message = "Máximo 30 días") Integer tatHospitalizadoMin,
            @NotNull @Min(value = 5, message = "Mínimo 5 minutos") @Max(value = 43200, message = "Máximo 30 días") Integer tatAmbulatorioMin,
            @Size(max = 4000) String plantillaHallazgos,
            @Size(max = 2000) String plantillaImpresion,
            @Size(max = 2000) String plantillaRecomendaciones) {
    }

    /** Datos de la adquisición que acompañan las imágenes (multipart). */
    public record AdquisicionRequest(
            @Size(max = 64, message = "Máximo 64 caracteres")
            @Pattern(regexp = "^$|^[0-9]+(\\.[0-9]+)*$", message = "Un StudyInstanceUID solo tiene números separados por puntos")
            String studyInstanceUid) {
    }

    public record ImagenClaveRequest(Boolean clave, @Size(max = 200, message = "Máximo 200 caracteres") String descripcion) {
        public boolean esClave() {
            return Boolean.TRUE.equals(clave);
        }
    }

    public record InformeRequest(
            @Size(max = 8000, message = "Máximo 8000 caracteres") String hallazgos,
            @Size(max = 4000, message = "Máximo 4000 caracteres") String impresion,
            @Size(max = 4000, message = "Máximo 4000 caracteres") String recomendaciones) {
    }

    /**
     * Firma del informe. Si hay un hallazgo crítico se registra a quién se comunicó (RFRAD-008).
     */
    public record FirmaRequest(
            @NotBlank(message = "Confirme su contraseña") String password,
            Boolean critico,
            @Size(max = 150, message = "Máximo 150 caracteres") String criticoReceptor,
            @Size(max = 40, message = "Máximo 40 caracteres") String criticoMedio) {
        /** Sin marcar = no hay hallazgo crítico. */
        public boolean hayCritico() {
            return Boolean.TRUE.equals(critico);
        }
    }

    public record MotivoRequest(
            @NotBlank(message = "Registre el motivo") @Size(max = 300, message = "Máximo 300 caracteres") String motivo) {
    }

    public record AsignacionRequest(@NotBlank(message = "Seleccione el radiólogo") String usuario) {
    }

    public record SegundaOpinionRequest(
            @NotBlank(message = "Seleccione el radiólogo") String asignadaA,
            @NotBlank(message = "Escriba qué quiere que revise") @Size(max = 500, message = "Máximo 500 caracteres") String pregunta) {
    }

    public record RespuestaOpinionRequest(
            @NotNull(message = "Indique la concordancia") SegundaOpinion.Concordancia concordancia,
            @Size(max = 2000, message = "Máximo 2000 caracteres") String comentario) {
    }
}
