package co.edu.ucentral.clinica.reportes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Cálculos de los tableros de directivos, sin base de datos para poder probarlos. */
public final class CalculadoraIndicadores {

    private CalculadoraIndicadores() {
    }

    public record Resumen(int casos, BigDecimal promedio, BigDecimal mediana, BigDecimal p90) {
        public static final Resumen VACIO = new Resumen(0, null, null, null);
    }

    public static Resumen resumir(List<Double> valores) {
        if (valores.isEmpty()) return Resumen.VACIO;
        List<Double> orden = valores.stream().sorted().toList();
        double promedio = orden.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return new Resumen(orden.size(), redondear(promedio), redondear(percentil(orden, 50)), redondear(percentil(orden, 90)));
    }

    /** Percentil con interpolación lineal (el mismo método de Excel PERCENTIL.INC). */
    static double percentil(List<Double> ordenados, double p) {
        if (ordenados.size() == 1) return ordenados.getFirst();
        double pos = p / 100 * (ordenados.size() - 1);
        int i = (int) Math.floor(pos);
        double fraccion = pos - i;
        return i + 1 < ordenados.size() ? ordenados.get(i) + fraccion * (ordenados.get(i + 1) - ordenados.get(i)) : ordenados.get(i);
    }

    /** Variación porcentual de {@code actual} frente a {@code base} (negativa = reducción). */
    public static BigDecimal variacionPct(BigDecimal actual, BigDecimal base) {
        if (actual == null || base == null || base.signum() == 0) return null;
        return actual.subtract(base).multiply(BigDecimal.valueOf(100)).divide(base, 1, RoundingMode.HALF_UP);
    }

    /** Valor meta: línea base reducida (o aumentada, con porcentaje negativo) en el porcentaje dado. */
    public static BigDecimal meta(BigDecimal base, BigDecimal reduccionPct) {
        if (base == null) return null;
        return base.multiply(BigDecimal.ONE.subtract(reduccionPct.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Proyección del ROI. Se acumulan los beneficios netos de los meses con costos registrados; los
     * meses que faltan hasta el plazo se proyectan con el promedio de los últimos tres meses con datos.
     *
     * @param beneficios beneficio neto de cada mes desde la puesta en marcha (null = mes sin costos registrados)
     */
    public record Proyeccion(BigDecimal inversion, BigDecimal acumulado, BigDecimal promedioReciente, int mesesTranscurridos,
                             int plazoMeses, BigDecimal acumuladoAlPlazo, BigDecimal roiAlPlazoPct, Integer mesRecuperacion,
                             List<BigDecimal> curva) {
        public boolean positivoEnPlazo() {
            return roiAlPlazoPct != null && roiAlPlazoPct.signum() > 0;
        }
    }

    public static Proyeccion proyectar(BigDecimal inversion, int plazoMeses, List<BigDecimal> beneficios) {
        BigDecimal acumulado = BigDecimal.ZERO;
        List<BigDecimal> conDatos = new ArrayList<>();
        List<BigDecimal> curva = new ArrayList<>();
        Integer recuperacion = null;
        for (int m = 0; m < beneficios.size(); m++) {
            BigDecimal b = beneficios.get(m);
            if (b != null) {
                acumulado = acumulado.add(b);
                conDatos.add(b);
            }
            curva.add(acumulado.subtract(inversion));
            if (recuperacion == null && acumulado.compareTo(inversion) >= 0) recuperacion = m + 1;
        }
        if (conDatos.isEmpty()) {
            return new Proyeccion(inversion, acumulado, null, beneficios.size(), plazoMeses, null, null, null, curva);
        }
        BigDecimal promedio = conDatos.subList(Math.max(0, conDatos.size() - 3), conDatos.size()).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(Math.min(3, conDatos.size())), 2, RoundingMode.HALF_UP);
        BigDecimal proyectado = acumulado;
        for (int m = beneficios.size(); m < plazoMeses; m++) {
            proyectado = proyectado.add(promedio);
            curva.add(proyectado.subtract(inversion));
            if (recuperacion == null && proyectado.compareTo(inversion) >= 0) recuperacion = m + 1;
        }
        BigDecimal roi = inversion.signum() == 0 ? null
                : proyectado.subtract(inversion).multiply(BigDecimal.valueOf(100)).divide(inversion, 1, RoundingMode.HALF_UP);
        return new Proyeccion(inversion, acumulado, promedio, beneficios.size(), plazoMeses, proyectado, roi, recuperacion, curva);
    }

    static BigDecimal redondear(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }
}
