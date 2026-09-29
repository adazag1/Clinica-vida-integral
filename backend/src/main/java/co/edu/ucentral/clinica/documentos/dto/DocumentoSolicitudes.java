package co.edu.ucentral.clinica.documentos.dto;

import co.edu.ucentral.clinica.documentos.Documento;
import co.edu.ucentral.clinica.documentos.TipoDocumento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Cuerpos de las peticiones de gestión documental. */
public final class DocumentoSolicitudes {

    private DocumentoSolicitudes() {
    }

    /** RFGAC-002 / RFGAC-004: metadatos que acompañan el archivo cargado (multipart). */
    public record CargaRequest(
            @NotNull(message = "Seleccione el paciente") Long pacienteId,
            Long episodioId,
            @NotNull(message = "Seleccione el tipo de documento") TipoDocumento tipo,
            @NotBlank(message = "Escriba un título") @Size(max = 200, message = "Máximo 200 caracteres") String titulo,
            @Size(max = 1000, message = "Máximo 1000 caracteres") String descripcion,
            @NotNull(message = "Indique la fecha del documento") @PastOrPresent(message = "No puede ser una fecha futura") LocalDate fechaDocumento,
            @NotBlank(message = "Indique el servicio") @Size(max = 80, message = "Máximo 80 caracteres") String servicio,
            @Size(max = 150, message = "Máximo 150 caracteres") String profesional,
            @NotNull(message = "Indique si es digital o físico") Documento.Soporte soporte,
            @Size(max = 60) String sala,
            @Size(max = 60) String estanteria,
            @Size(max = 60) String seccion,
            @Size(max = 60) String caja) {
    }

    public record VersionRequest(
            @NotBlank(message = "Explique por qué se reemplaza") @Size(max = 300, message = "Máximo 300 caracteres") String motivo) {
    }

    public record EstadoRequest(
            @NotNull(message = "Seleccione el estado") Documento.Estado estado,
            @NotBlank(message = "Registre el motivo") @Size(max = 300, message = "Máximo 300 caracteres") String motivo) {
    }

    public record UbicacionRequest(
            @NotBlank(message = "Indique la sala") @Size(max = 60) String sala,
            @NotBlank(message = "Indique la estantería") @Size(max = 60) String estanteria,
            @Size(max = 60) String seccion,
            @Size(max = 60) String caja) {
    }

    public record SolicitudRequest(
            @NotNull(message = "Seleccione el paciente") Long pacienteId,
            Long documentoId,
            @NotBlank(message = "Indique para qué lo necesita") @Size(max = 500, message = "Máximo 500 caracteres") String motivo,
            Boolean urgente) {
        public boolean esUrgente() {
            return Boolean.TRUE.equals(urgente);
        }
    }

    public record AtencionRequest(@Size(max = 500, message = "Máximo 500 caracteres") String respuesta) {
    }
}
