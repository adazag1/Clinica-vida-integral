package co.edu.ucentral.clinica.reportes;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraIndicadoresTest {

    @Test
    void resumenConPromedioMedianaYPercentil90() {
        CalculadoraIndicadores.Resumen r = CalculadoraIndicadores.resumir(List.of(10.0, 2.0, 4.0, 8.0, 6.0));
        assertThat(r.casos()).isEqualTo(5);
        assertThat(r.promedio()).isEqualByComparingTo("6.0");
        assertThat(r.mediana()).isEqualByComparingTo("6.0");
        assertThat(r.p90()).isEqualByComparingTo("9.2");
        assertThat(CalculadoraIndicadores.resumir(List.of())).isEqualTo(CalculadoraIndicadores.Resumen.VACIO);
    }

    @Test
    void metaYVariacion() {
        // Meta del CEO: 48 h reducidas un 30 % = 33,6 h
        assertThat(CalculadoraIndicadores.meta(new BigDecimal("48"), new BigDecimal("30"))).isEqualByComparingTo("33.6");
        // Meta de capacidad: aumento del 15 % (porcentaje negativo en la reducción)
        assertThat(CalculadoraIndicadores.meta(new BigDecimal("320"), new BigDecimal("-15"))).isEqualByComparingTo("368");
        assertThat(CalculadoraIndicadores.variacionPct(new BigDecimal("36"), new BigDecimal("48"))).isEqualByComparingTo("-25.0");
        assertThat(CalculadoraIndicadores.variacionPct(null, new BigDecimal("48"))).isNull();
    }

    @Test
    void proyeccionDelRoiConElPromedioDeLosUltimosTresMeses() {
        // Inversión de 1.000; dos meses sin costos registrados y luego 40, 50 y 60 de beneficio
        List<BigDecimal> beneficios = Arrays.asList(null, null, bd(40), bd(50), bd(60));
        CalculadoraIndicadores.Proyeccion p = CalculadoraIndicadores.proyectar(bd(1000), 36, beneficios);

        assertThat(p.acumulado()).isEqualByComparingTo("150");
        assertThat(p.promedioReciente()).isEqualByComparingTo("50");
        // 150 + 31 meses × 50 = 1.700 → ROI 70 %
        assertThat(p.acumuladoAlPlazo()).isEqualByComparingTo("1700");
        assertThat(p.roiAlPlazoPct()).isEqualByComparingTo("70.0");
        assertThat(p.positivoEnPlazo()).isTrue();
        // 150 + 17 × 50 = 1.000 en el mes 22
        assertThat(p.mesRecuperacion()).isEqualTo(22);
        assertThat(p.curva()).hasSize(36);
    }

    @Test
    void sinCostosRegistradosNoHayProyeccion() {
        CalculadoraIndicadores.Proyeccion p = CalculadoraIndicadores.proyectar(bd(1000), 36, Arrays.asList(null, null));
        assertThat(p.roiAlPlazoPct()).isNull();
        assertThat(p.positivoEnPlazo()).isFalse();
        assertThat(p.mesRecuperacion()).isNull();
    }

    @Test
    void roiNegativoSiElBeneficioNoAlcanza() {
        CalculadoraIndicadores.Proyeccion p = CalculadoraIndicadores.proyectar(bd(1000), 36, List.of(bd(10), bd(-5), bd(10)));
        assertThat(p.positivoEnPlazo()).isFalse();
        assertThat(p.mesRecuperacion()).isNull();
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }
}
