package co.edu.ucentral.clinica.reportes.dto;

import co.edu.ucentral.clinica.reportes.CalculadoraIndicadores.Resumen;
import co.edu.ucentral.clinica.reportes.CostoOperativo;
import co.edu.ucentral.clinica.reportes.ParametroIndicador;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ReportesDtos {

    private ReportesDtos() {
    }

    public record ParametroDto(String clave, String valor, String descripcion, String unidad, boolean prueba,
                               String modificadoPor, Instant modificadoEn) {
        public static ParametroDto de(ParametroIndicador p) {
            return new ParametroDto(p.getClave(), p.getValor(), p.getDescripcion(), p.getUnidad(), p.isPrueba(),
                    p.getModificadoPor(), p.getModificadoEn());
        }
    }

    // ---------- US 5.1: tablero clínico (CEO) ----------

    public record MesClinico(String mes, int casos, BigDecimal promedioHoras, BigDecimal medianaHoras) {
    }

    public record ComponenteTiempo(String nombre, String descripcion, int casos, BigDecimal promedioHoras) {
    }

    public record DiagnosticoCritico(String codigo, String nombre, int casos, BigDecimal promedioHoras) {
    }

    public record CasoReciente(Instant diagnosticadoEn, String codigo, String nombre, BigDecimal horas, boolean desdeAdmision) {
    }

    /**
     * @param estado META_ALCANZADA, EN_PROGRESO (mejor que la línea base), SIN_MEJORA o SIN_DATOS
     * @param reduccionPct cuánto se ha reducido el tiempo frente a la línea base (positivo = mejora)
     */
    public record TableroClinicoDto(BigDecimal lineaBaseHoras, BigDecimal metaReduccionPct, BigDecimal metaHoras, int plazoMeses,
                                    String inicio, int mesesTranscurridos, Resumen ultimos30Dias, BigDecimal reduccionPct,
                                    String estado, List<MesClinico> serie, List<ComponenteTiempo> componentes,
                                    List<DiagnosticoCritico> porDiagnostico, List<CasoReciente> recientes,
                                    List<String> prefijosCriticos, boolean parametrosDePrueba, Instant calculadoEn) {
    }

    // ---------- US 5.2: reporte financiero y operativo (CFO) ----------

    /**
     * Un mes del reporte. Los campos de costos son nulos si ese mes no se registraron costos.
     *
     * @param beneficioNeto ahorro administrativo + margen de las atenciones adicionales − costo de la plataforma
     */
    public record MesFinanciero(String mes, boolean costosRegistrados, BigDecimal costoAdministrativo, BigDecimal costoPlataforma,
                                BigDecimal ahorro, BigDecimal variacionCostosPct, long atenciones, long medicos,
                                BigDecimal atencionesPorMedico, BigDecimal variacionCapacidadPct, BigDecimal facturado,
                                BigDecimal recaudado, BigDecimal cargosAutomaticosPct, BigDecimal beneficioCapacidad,
                                BigDecimal beneficioNeto) {
    }

    public record RoiDto(BigDecimal inversion, BigDecimal inversionUsd, BigDecimal acumulado, BigDecimal promedioReciente,
                         int mesesTranscurridos, int plazoMeses, BigDecimal acumuladoAlPlazo, BigDecimal roiAlPlazoPct,
                         Integer mesRecuperacion, boolean positivoEnPlazo, List<BigDecimal> curva) {
    }

    public record TableroFinancieroDto(BigDecimal costosLineaBase, BigDecimal costosMeta, BigDecimal costosMetaReduccionPct,
                                       BigDecimal capacidadLineaBase, BigDecimal capacidadMeta, BigDecimal capacidadMetaAumentoPct,
                                       List<MesFinanciero> meses, RoiDto roi, boolean parametrosDePrueba, Instant calculadoEn) {
    }

    public record CostoDto(String mes, String categoria, String categoriaDescripcion, boolean administrativa, BigDecimal valor,
                           String nota, String registradoPor, Instant registradoEn) {
        public static CostoDto de(CostoOperativo c) {
            return new CostoDto(c.getMes().toString().substring(0, 7), c.getCategoria().name(), c.getCategoria().getDescripcion(),
                    c.getCategoria().isAdministrativa(), c.getValor(), c.getNota(), c.getRegistradoPor(), c.getRegistradoEn());
        }
    }

    public record CategoriaDto(String valor, String descripcion, boolean administrativa) {
    }
}
