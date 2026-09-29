package co.edu.ucentral.clinica.documentos.dto;

import co.edu.ucentral.clinica.documentos.Documento;
import co.edu.ucentral.clinica.documentos.DocumentoEvento;
import co.edu.ucentral.clinica.documentos.DocumentoVersion;
import co.edu.ucentral.clinica.documentos.SolicitudArchivo;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/** Respuestas de gestión documental. */
public final class DocumentoDtos {

    private DocumentoDtos() {
    }

    /** Fila de búsqueda o de la historia del paciente. */
    public record DocumentoResumenDto(Long id, Long pacienteId, String pacienteNombre, String pacienteDocumento,
                                      String tipo, String tipoDescripcion, String titulo, LocalDate fechaDocumento,
                                      String servicio, String profesional, String soporte, String estado,
                                      String estadoDescripcion, String origen, String enlace, int versionActual,
                                      boolean tieneArchivo, String tipoMime, String ubicacion) {
        public static DocumentoResumenDto de(Documento d, PacienteResumenDto p) {
            return new DocumentoResumenDto(d.getId(), d.getPacienteId(), p == null ? null : p.nombreCompleto(),
                    p == null ? null : p.tipoDocumento() + " " + p.numeroDocumento(), d.getTipo().name(),
                    d.getTipo().getDescripcion(), d.getTitulo(), d.getFechaDocumento(), d.getServicio(), d.getProfesional(),
                    d.getSoporte().name(), d.getEstado().name(), d.getEstado().getDescripcion(), d.getOrigen().name(),
                    d.getEnlace(), d.getVersionActual(), d.getArchivo() != null,
                    d.getArchivo() == null ? null : d.getArchivo().getTipoMime(), textoUbicacion(d));
        }
    }

    public record VersionDto(int version, String nombre, String tipoMime, long tamano, String sha256, String motivo,
                             String usuario, Instant fecha) {
        public static VersionDto de(DocumentoVersion v) {
            return new VersionDto(v.getVersion(), v.getArchivo().getNombre(), v.getArchivo().getTipoMime(),
                    v.getArchivo().getTamano(), v.getArchivo().getSha256(), v.getMotivo(), v.getUsuario(), v.getFecha());
        }
    }

    public record EventoDto(String accion, String detalle, String usuario, Instant fecha) {
        public static EventoDto de(DocumentoEvento e) {
            return new EventoDto(e.getAccion(), e.getDetalle(), e.getUsuario(), e.getFecha());
        }
    }

    public record DocumentoDto(DocumentoResumenDto resumen, PacienteResumenDto paciente, Long episodioId,
                               String descripcion, String sala, String estanteria, String seccion, String caja,
                               LocalDate retencionHasta, String motivoEstado, String registradoPor,
                               Instant registradoEn, List<VersionDto> versiones, List<EventoDto> eventos) {
    }

    public record SolicitudDto(Long id, PacienteResumenDto paciente, Long documentoId, String documentoTitulo,
                               String solicitante, String solicitanteNombre, String motivo, boolean urgente,
                               String estado, String estadoDescripcion, Instant solicitadaEn, String atendidaPor,
                               Instant entregadaEn, Instant devueltaEn, String respuesta) {
        public static SolicitudDto de(SolicitudArchivo s, PacienteResumenDto p, String titulo) {
            return new SolicitudDto(s.getId(), p, s.getDocumentoId(), titulo, s.getSolicitante(), s.getSolicitanteNombre(),
                    s.getMotivo(), s.isUrgente(), s.getEstado().name(), s.getEstado().getDescripcion(), s.getSolicitadaEn(),
                    s.getAtendidaPor(), s.getEntregadaEn(), s.getDevueltaEn(), s.getRespuesta());
        }
    }

    public record ConteoDto(String clave, String etiqueta, long cantidad) {
    }

    /** RFGAC-011: volumen, accesos y solicitudes en un periodo. */
    public record ReporteDto(LocalDate desde, LocalDate hasta, long totalDocumentos, List<ConteoDto> porTipo,
                             List<ConteoDto> porEstado, List<ConteoDto> porOrigen, List<ConteoDto> accionesPeriodo,
                             List<ConteoDto> accesosPorUsuario, long solicitudesPeriodo,
                             List<ConteoDto> solicitudesPorEstado, Double minutosPromedioEntrega,
                             long solicitudesPendientes) {
    }

    static String textoUbicacion(Documento d) {
        List<String> partes = Stream.of(
                        d.getSala() == null ? null : "Sala " + d.getSala(),
                        d.getEstanteria() == null ? null : "estantería " + d.getEstanteria(),
                        d.getSeccion() == null ? null : "sección " + d.getSeccion(),
                        d.getCaja() == null ? null : "caja " + d.getCaja())
                .filter(s -> s != null).toList();
        return partes.isEmpty() ? null : String.join(", ", partes);
    }
}
