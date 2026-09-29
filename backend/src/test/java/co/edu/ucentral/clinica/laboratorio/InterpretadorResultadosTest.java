package co.edu.ucentral.clinica.laboratorio;

import co.edu.ucentral.clinica.pacientes.Sexo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterpretadorResultadosTest {

    private final InterpretadorResultados interpretador = new InterpretadorResultados();

    private static RangoReferencia rango(Sexo sexo, int edadMin, int edadMax, String refMin, String refMax,
                                         String critMin, String critMax) {
        RangoReferencia r = BeanUtils.instantiateClass(RangoReferencia.class);
        ReflectionTestUtils.setField(r, "sexo", sexo);
        ReflectionTestUtils.setField(r, "edadMin", edadMin);
        ReflectionTestUtils.setField(r, "edadMax", edadMax);
        r.actualizar(dec(refMin), dec(refMax), dec(critMin), dec(critMax), "test");
        return r;
    }

    private static BigDecimal dec(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    /** Hemoglobina: hombres, mujeres y niños tienen rangos distintos. */
    private final List<RangoReferencia> hemoglobina = List.of(
            rango(Sexo.MASCULINO, 13, 150, "13.5", "17.5", "7", "20"),
            rango(Sexo.FEMENINO, 13, 150, "12.0", "15.5", "7", "20"),
            rango(null, 1, 12, "11.0", "14.5", "7", "20"));

    @Test
    void eligeElRangoSegunSexoYEdad() {
        BigDecimal valor = new BigDecimal("12.5");
        RangoReferencia mujer = interpretador.rango(hemoglobina, Sexo.FEMENINO, 30).orElseThrow();
        RangoReferencia hombre = interpretador.rango(hemoglobina, Sexo.MASCULINO, 30).orElseThrow();
        RangoReferencia nino = interpretador.rango(hemoglobina, Sexo.MASCULINO, 8).orElseThrow();

        assertThat(interpretador.interpretar(valor, mujer)).isEqualTo(Interpretacion.NORMAL);
        assertThat(interpretador.interpretar(valor, hombre)).isEqualTo(Interpretacion.BAJO);
        assertThat(interpretador.interpretar(valor, nino)).isEqualTo(Interpretacion.NORMAL);
    }

    @Test
    void clasificaCriticosAntesQueAnormales() {
        RangoReferencia potasio = rango(null, 0, 150, "3.5", "5.1", "2.8", "6.2");
        assertThat(interpretador.interpretar(new BigDecimal("6.8"), potasio)).isEqualTo(Interpretacion.CRITICO_ALTO);
        assertThat(interpretador.interpretar(new BigDecimal("5.5"), potasio)).isEqualTo(Interpretacion.ALTO);
        assertThat(interpretador.interpretar(new BigDecimal("2.5"), potasio)).isEqualTo(Interpretacion.CRITICO_BAJO);
        assertThat(interpretador.interpretar(new BigDecimal("4.2"), potasio)).isEqualTo(Interpretacion.NORMAL);
    }

    @Test
    void unLimiteVacioEsSinLimite() {
        RangoReferencia colesterol = rango(null, 0, 150, null, "199", null, null);
        assertThat(interpretador.interpretar(new BigDecimal("80"), colesterol)).isEqualTo(Interpretacion.NORMAL);
        assertThat(interpretador.interpretar(new BigDecimal("260"), colesterol)).isEqualTo(Interpretacion.ALTO);
    }

    @Test
    void deltaCheckContraElResultadoPrevio() {
        BigDecimal delta = interpretador.delta(new BigDecimal("4.0"), new BigDecimal("6.9"));
        assertThat(delta).isEqualByComparingTo("-42.0");
        assertThat(interpretador.superaDelta(delta, new BigDecimal("20"))).isTrue();
        assertThat(interpretador.superaDelta(interpretador.delta(new BigDecimal("4.2"), new BigDecimal("4.0")),
                new BigDecimal("20"))).isFalse();
        assertThat(interpretador.delta(new BigDecimal("4.0"), null)).isNull();
    }
}
