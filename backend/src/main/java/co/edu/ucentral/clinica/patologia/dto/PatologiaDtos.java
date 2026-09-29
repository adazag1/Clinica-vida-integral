package co.edu.ucentral.clinica.patologia.dto;

import co.edu.ucentral.clinica.patologia.CustodiaPatologia;
import co.edu.ucentral.clinica.patologia.EventoCasoPatologia;
import co.edu.ucentral.clinica.patologia.InformePatologia;
import co.edu.ucentral.clinica.patologia.PlantillaPatologia;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Respuestas del módulo de patología. */
public final class PatologiaDtos {

    private PatologiaDtos() {
    }

    public record CasoResumenDto(Long id, String numero, PacienteResumenDto paciente, String tipo, String tipoDescripcion,
                                 String prioridad, String prioridadDescripcion, String estado, String estadoDescripcion,
                                 String sitioAnatomico, String medicoNombre, Instant solicitadoEn, Instant recibidoEn,
                                 Instant limiteRespuesta, boolean vencido, Instant validadoEn, String diagnostico) {
    }

    public record PlantillaDto(Long id, String tipo, String tipoDescripcion, String servicio, String nombre,
                               List<PlantillaPatologia.Campo> campos, String modificadoPor, Instant modificadoEn) {
        public static PlantillaDto de(PlantillaPatologia p) {
            return new PlantillaDto(p.getId(), p.getTipo().name(), p.getTipo().getDescripcion(), p.getServicio(),
                    p.getNombre(), p.getCampos(), p.getModificadoPor(), p.getModificadoEn());
        }
    }

    /** @param integro la huella recalculada coincide con la guardada al validar */
    public record VersionDto(int version, Map<String, String> macroscopia, Map<String, String> microscopia,
                             String diagnostico, String motivoCambio, String autor, String autorNombre,
                             Instant validadoEn, String firmaHash, boolean integro) {
        public static VersionDto de(InformePatologia i, String numeroCaso) {
            return new VersionDto(i.getVersion(), i.getMacroscopia(), i.getMicroscopia(), i.getDiagnostico(),
                    i.getMotivoCambio(), i.getAutor(), i.getAutorNombre(), i.getValidadoEn(), i.getFirmaHash(),
                    i.getFirmaHash().equals(i.huella(numeroCaso)));
        }
    }

    public record CustodiaDto(String material, String materialDescripcion, String accion, String accionDescripcion,
                              int cantidad, String ubicacion, String destinatario, String motivo, String usuario,
                              Instant fecha) {
        public static CustodiaDto de(CustodiaPatologia c) {
            return new CustodiaDto(c.getMaterial().name(), c.getMaterial().getDescripcion(), c.getAccion().name(),
                    c.getAccion().getDescripcion(), c.getCantidad(), c.getUbicacion(), c.getDestinatario(), c.getMotivo(),
                    c.getUsuario(), c.getFecha());
        }
    }

    public record EventoDto(String accion, String detalle, String usuario, Instant fecha) {
        public static EventoDto de(EventoCasoPatologia e) {
            return new EventoDto(e.getAccion(), e.getDetalle(), e.getUsuario(), e.getFecha());
        }
    }

    /**
     * @param cambiosSinValidar el caso informado tiene una corrección en borrador que aún no es una versión
     * @param ubicacionMaterial dónde está hoy cada material, según el último movimiento de custodia
     */
    public record CasoDto(CasoResumenDto resumen, Long consultaId, String datosClinicos, int numeroMuestras,
                          String medico, String recibidoPor, PlantillaDto plantilla, Map<String, String> macroscopia,
                          Map<String, String> microscopia, String diagnostico, String macroscopiaPor,
                          Instant macroscopiaEn, String validadoPor, LocalDate retencionHasta,
                          boolean cambiosSinValidar, List<VersionDto> versiones, List<CustodiaDto> custodia,
                          List<String> ubicacionMaterial, List<EventoDto> eventos, List<String> alergias,
                          List<String> diagnosticosConsulta, List<CasoResumenDto> previos) {
    }
}
