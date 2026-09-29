package co.edu.ucentral.clinica.imagenes.dto;

import co.edu.ucentral.clinica.archivos.AlmacenArchivos;
import co.edu.ucentral.clinica.imagenes.EventoEstudio;
import co.edu.ucentral.clinica.imagenes.ImagenEstudio;
import co.edu.ucentral.clinica.imagenes.InformeImagen;
import co.edu.ucentral.clinica.imagenes.SegundaOpinion;
import co.edu.ucentral.clinica.imagenes.TipoEstudio;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;

import java.time.Instant;
import java.util.List;

/** Respuestas del módulo de imágenes diagnósticas. */
public final class ImagenesDtos {

    private ImagenesDtos() {
    }

    public record TipoEstudioDto(Long id, String codigo, String nombre, String modalidad, String modalidadDescripcion,
                                 String region, int tatUrgenteMin, int tatHospitalizadoMin, int tatAmbulatorioMin,
                                 String plantillaHallazgos, String plantillaImpresion, String plantillaRecomendaciones,
                                 String modificadoPor, Instant modificadoEn) {
        public static TipoEstudioDto de(TipoEstudio t) {
            return new TipoEstudioDto(t.getId(), t.getCodigo(), t.getNombre(), t.getModalidad().name(),
                    t.getModalidad().getDescripcion(), t.getRegion(), t.getTatUrgenteMin(), t.getTatHospitalizadoMin(),
                    t.getTatAmbulatorioMin(), t.getPlantillaHallazgos(), t.getPlantillaImpresion(),
                    t.getPlantillaRecomendaciones(), t.getModificadoPor(), t.getModificadoEn());
        }
    }

    /** Fila de la lista de trabajo (RFRAD-002 / 004) o de estudios previos (RFRAD-005). */
    public record EstudioResumenDto(Long id, String numero, PacienteResumenDto paciente, String estudio, String modalidad,
                                    String modalidadDescripcion, String prioridad, String prioridadDescripcion,
                                    String estado, String estadoDescripcion, Instant ordenadoEn, Instant limiteTat,
                                    boolean tatVencido, String asignadoA, String medicoNombre, int imagenes,
                                    boolean critico, boolean segundaOpinionPendiente, Instant informadoEn,
                                    String impresion) {
    }

    /** @param visible true si el navegador puede mostrarla (JPG/PNG); un DICOM se abre en el visor del PACS */
    public record ImagenDto(Long id, int orden, String nombre, String tipoMime, long tamano, boolean visible,
                            boolean clave, String descripcion, String sha256) {
        public static ImagenDto de(ImagenEstudio i) {
            return new ImagenDto(i.getId(), i.getOrden(), i.getArchivo().getNombre(), i.getArchivo().getTipoMime(),
                    i.getArchivo().getTamano(), AlmacenArchivos.esImagenVisible(i.getArchivo().getTipoMime()), i.isClave(),
                    i.getDescripcion(), i.getArchivo().getSha256());
        }
    }

    /** @param integro la huella recalculada coincide con la guardada al firmar */
    public record InformeDto(int version, String hallazgos, String impresion, String recomendaciones, String estado,
                             String motivoCambio, String autor, String autorNombre, Instant creadoEn,
                             Instant modificadoEn, Instant firmadoEn, String firmaHash, Boolean integro) {
        public static InformeDto de(InformeImagen i) {
            return new InformeDto(i.getVersion(), i.getHallazgos(), i.getImpresion(), i.getRecomendaciones(),
                    i.getEstado().name(), i.getMotivoCambio(), i.getAutor(), i.getAutorNombre(), i.getCreadoEn(),
                    i.getModificadoEn(), i.getFirmadoEn(), i.getFirmaHash(),
                    i.getFirmaHash() == null ? null : i.getFirmaHash().equals(i.huella()));
        }
    }

    public record SegundaOpinionDto(Long id, String solicitadaPor, String asignadaA, String asignadaANombre,
                                    String pregunta, Instant solicitadaEn, String concordancia,
                                    String concordanciaDescripcion, String comentario, Instant respondidaEn) {
        public static SegundaOpinionDto de(SegundaOpinion s, String nombre) {
            return new SegundaOpinionDto(s.getId(), s.getSolicitadaPor(), s.getAsignadaA(), nombre, s.getPregunta(),
                    s.getSolicitadaEn(), s.getConcordancia() == null ? null : s.getConcordancia().name(),
                    s.getConcordancia() == null ? null : s.getConcordancia().getDescripcion(), s.getComentario(),
                    s.getRespondidaEn());
        }
    }

    public record EventoDto(String accion, String detalle, String usuario, Instant fecha) {
        public static EventoDto de(EventoEstudio e) {
            return new EventoDto(e.getAccion(), e.getDetalle(), e.getUsuario(), e.getFecha());
        }
    }

    /** RFRAD-003: lo que el radiólogo necesita saber del paciente sin salir del visor. */
    public record ContextoClinicoDto(String motivoConsulta, List<String> diagnosticos, List<String> alergias,
                                     List<String> antecedentes) {
    }

    public record PlantillaDto(String hallazgos, String impresion, String recomendaciones) {
    }

    public record EstudioDto(EstudioResumenDto resumen, Long consultaId, String motivo, String diagnosticoPresuntivo,
                             String medico, Instant adquiridoEn, String adquiridoPor, String studyInstanceUid,
                             String urlPacs, String bloqueoMotivo, String bloqueadoPor, Instant bloqueadoEn,
                             String criticoReceptor, String criticoMedio, Instant criticoNotificadoEn,
                             List<ImagenDto> imagenes, InformeDto borrador, InformeDto vigente, List<InformeDto> versiones,
                             PlantillaDto plantilla, List<SegundaOpinionDto> segundasOpiniones, List<EventoDto> eventos,
                             List<EstudioResumenDto> previos, ContextoClinicoDto contexto) {
    }

    /** RFRAD-012: tiempos de respuesta por tipo de estudio. */
    public record MetricaDto(String estudio, String modalidad, long informados, Double promedioMin, Double medianaMin,
                             Double promedioLecturaMin, Double dentroDeTatPct, long pendientes, long pendientesVencidos) {
    }

    public record RadiologoDto(String usuario, String nombre) {
    }
}
