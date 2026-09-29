package co.edu.ucentral.clinica.admisiones;

import co.edu.ucentral.clinica.admisiones.dto.SignosVitales;
import co.edu.ucentral.clinica.admisiones.dto.SugerenciaTriage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraTriageTest {

    private final CalculadoraTriage calculadora = new CalculadoraTriage();

    /** Adulto sano: FC 75, FR 16, PA 120/80, 36,8 °C, SpO2 98 %, Glasgow 15, sin dolor. */
    private static SignosVitales normales() {
        return new SignosVitales(75, 16, 120, 80, new BigDecimal("36.8"), 98, 15, 0);
    }

    private static SignosVitales con(Integer fc, Integer fr, Integer pas, Integer pad, String temp, Integer spo2,
                                     Integer glasgow, Integer dolor) {
        SignosVitales n = normales();
        return new SignosVitales(fc != null ? fc : n.frecuenciaCardiaca(), fr != null ? fr : n.frecuenciaRespiratoria(),
                pas != null ? pas : n.presionSistolica(), pad != null ? pad : n.presionDiastolica(),
                temp != null ? new BigDecimal(temp) : n.temperatura(), spo2 != null ? spo2 : n.saturacionOxigeno(),
                glasgow != null ? glasgow : n.glasgow(), dolor != null ? dolor : n.dolor());
    }

    @Test
    void signosNormalesSonNivelCinco() {
        SugerenciaTriage s = calculadora.sugerir(normales(), 35);
        assertThat(s.nivel()).isEqualTo(5);
        assertThat(s.advertencias()).isEmpty();
    }

    @Test
    void glasgowBajoEsNivelUno() {
        SugerenciaTriage s = calculadora.sugerir(con(null, null, null, null, null, null, 7, null), 40);
        assertThat(s.nivel()).isEqualTo(1);
        assertThat(s.criterios()).anyMatch(c -> c.startsWith("Glasgow 7"));
    }

    @Test
    void hipoxemiaSeveraEsNivelUno() {
        assertThat(calculadora.sugerir(con(null, null, null, null, null, 82, null, null), 60).nivel()).isEqualTo(1);
    }

    @Test
    void hipotensionEsNivelUno() {
        assertThat(calculadora.sugerir(con(null, null, 65, 40, null, null, null, null), 60).nivel()).isEqualTo(1);
    }

    @Test
    void dolorSeveroEsNivelDos() {
        assertThat(calculadora.sugerir(con(null, null, null, null, null, null, null, 9), 30).nivel()).isEqualTo(2);
    }

    @Test
    void crisisHipertensivaEsNivelDos() {
        assertThat(calculadora.sugerir(con(null, null, 230, 125, null, null, null, null), 70).nivel()).isEqualTo(2);
    }

    @Test
    void fiebreAltaEsNivelTres() {
        assertThat(calculadora.sugerir(con(null, null, null, null, "39.4", null, null, null), 30).nivel()).isEqualTo(3);
    }

    @Test
    void fiebreModeradaEsNivelCuatro() {
        SugerenciaTriage s = calculadora.sugerir(con(null, null, null, null, "38.2", null, null, null), 30);
        assertThat(s.nivel()).isEqualTo(4);
        assertThat(s.criterios()).containsExactly("Temperatura 38,2 °C (fiebre)");
    }

    @Test
    void ganaElCriterioMasGrave() {
        // Fiebre (IV) + dolor moderado (III) + saturación 88 % (II) → II
        SugerenciaTriage s = calculadora.sugerir(con(null, null, null, null, "38.5", 88, null, 6), 50);
        assertThat(s.nivel()).isEqualTo(2);
        assertThat(s.criterios()).containsExactly("Saturación 88 % (85 a 89 %)");
    }

    @Test
    void enNinosAdvierteQueLosUmbralesSonDeAdultos() {
        SugerenciaTriage s = calculadora.sugerir(normales(), 5);
        assertThat(s.advertencias()).singleElement().asString().contains("pediátrico");
    }
}
