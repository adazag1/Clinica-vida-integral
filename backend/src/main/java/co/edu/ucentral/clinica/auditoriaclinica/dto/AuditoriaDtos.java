package co.edu.ucentral.clinica.auditoriaclinica.dto;

import co.edu.ucentral.clinica.admisiones.dto.ConsentimientoDto;
import co.edu.ucentral.clinica.auditoriaclinica.CodificacionCambio;
import co.edu.ucentral.clinica.auditoriaclinica.Devolucion;
import co.edu.ucentral.clinica.auditoriaclinica.SugeridorDiagnosticos;
import co.edu.ucentral.clinica.documentos.dto.DocumentoDtos.DocumentoResumenDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.CuentaDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.ConsultaDto;
import co.edu.ucentral.clinica.imagenes.dto.ImagenesDtos.EstudioResumenDto;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.OrdenResumenDto;
import co.edu.ucentral.clinica.patologia.dto.PatologiaDtos.CasoResumenDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Respuestas del módulo de auditoría clínica. */
public final class AuditoriaDtos {

    private AuditoriaDtos() {
    }

    /** Punto de la lista de verificación previa a la aprobación (RFAU-007). */
    public record VerificacionDto(String punto, boolean cumple, String detalle) {
    }

    public record CodificacionDto(String diagnosticoPrincipal, String nombrePrincipal, List<String> relacionados,
                                  List<String> nombresRelacionados, String modificadoPor, Instant modificadoEn,
                                  List<CambioCodificacionDto> historial) {
    }

    public record CambioCodificacionDto(String campo, String anterior, String nuevo, String motivo, String usuario, Instant fecha) {
        public static CambioCodificacionDto de(CodificacionCambio c) {
            return new CambioCodificacionDto(c.getCampo(), c.getAnterior(), c.getNuevo(), c.getMotivo(), c.getUsuario(), c.getFecha());
        }
    }

    public record DevolucionDto(Long id, String motivo, String motivoDescripcion, String detalle, String dirigidaA,
                                String devueltaPor, Instant devueltaEn, Instant corregidaEn, String corregidaPor,
                                String respuesta, Long horasCorreccion) {
        public static DevolucionDto de(Devolucion d) {
            return new DevolucionDto(d.getId(), d.getMotivo().name(), d.getMotivo().getDescripcion(), d.getDetalle(),
                    d.getDirigidaA().name(), d.getDevueltaPor(), d.getDevueltaEn(), d.getCorregidaEn(), d.getCorregidaPor(),
                    d.getRespuesta(), d.tiempoCorreccion() == null ? null : d.tiempoCorreccion().toHours());
        }
    }

    /**
     * RFAU-002: todo lo del episodio en una vista para el auditor.
     *
     * @param episodio texto con el número, tipo de atención y cobertura
     */
    public record ExpedienteDto(CuentaDto cuenta, String episodio, List<ConsentimientoDto> consentimientos,
                                List<ConsultaDto> consultas, List<OrdenResumenDto> laboratorio, List<EstudioResumenDto> imagenes,
                                List<CasoResumenDto> patologia, List<DocumentoResumenDto> documentos,
                                List<VerificacionDto> verificacion, CodificacionDto codificacion,
                                List<SugeridorDiagnosticos.Sugerencia> sugerencias, List<DevolucionDto> devoluciones) {
    }

    public record ConteoDto(String clave, String etiqueta, long cantidad, BigDecimal valor, Double promedioHoras) {
    }

    /** RFAU-006: indicadores de auditoría del periodo. */
    public record TableroDto(long cuentasEnAuditoria, long cuentasDevueltas, long auditadasPeriodo, long devolucionesPeriodo,
                             Double tasaDevolucionPct, Double horasPromedioCorreccion, List<ConteoDto> devolucionesPorMotivo,
                             long consultasSinFirmar, long cargosSinSoporte, List<ConteoDto> glosasPorConcepto,
                             long glosasClinicas, BigDecimal valorGlosasClinicas) {
    }

    public record DiagnosticoCatalogoDto(Long id, String codigo, String nombre, boolean activo) {
    }
}
