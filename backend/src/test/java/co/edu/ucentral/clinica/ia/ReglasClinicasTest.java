package co.edu.ucentral.clinica.ia;

import co.edu.ucentral.clinica.ia.reglas.ReglaAnemia;
import co.edu.ucentral.clinica.ia.reglas.ReglaCrisisHipertensiva;
import co.edu.ucentral.clinica.ia.reglas.ReglaCriticoSinNota;
import co.edu.ucentral.clinica.ia.reglas.ReglaGlucosa;
import co.edu.ucentral.clinica.ia.reglas.ReglaLesionRenal;
import co.edu.ucentral.clinica.ia.reglas.ReglaMetforminaRenal;
import co.edu.ucentral.clinica.ia.reglas.ReglaPotasio;
import co.edu.ucentral.clinica.ia.reglas.ReglaSepsis;
import co.edu.ucentral.clinica.ia.reglas.ReglaSindromeCoronario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReglasClinicasTest {

    private static final Instant AHORA = Instant.parse("2026-09-29T15:00:00Z");

    /** Arma el contexto de un paciente de prueba paso a paso. */
    private static final class Caso {
        int edad = 60;
        String sexo = "MASCULINO";
        final List<ContextoClinico.Signo> signos = new ArrayList<>();
        final Map<String, List<ContextoClinico.Resultado>> resultados = new HashMap<>();
        final List<ContextoClinico.Medicamento> medicamentos = new ArrayList<>();
        final List<String> diagnosticos = new ArrayList<>();
        String texto = "";
        final List<ContextoClinico.Critico> criticos = new ArrayList<>();
        Instant ultimaNota;

        Caso signo(long horasAtras, Integer pas, Integer pad, Integer fc, Integer fr, String temp) {
            signos.add(new ContextoClinico.Signo(AHORA.minus(Duration.ofHours(horasAtras)), pas, pad, fc, fr,
                    temp == null ? null : new BigDecimal(temp), 95));
            signos.sort((a, b) -> b.fecha().compareTo(a.fecha()));
            return this;
        }

        Caso lab(String analito, long horasAtras, String valor, String refMax) {
            resultados.computeIfAbsent(analito, k -> new ArrayList<>()).add(new ContextoClinico.Resultado(
                    AHORA.minus(Duration.ofHours(horasAtras)), new BigDecimal(valor), null,
                    refMax == null ? null : new BigDecimal(refMax), "ALTO", null));
            resultados.get(analito).sort((a, b) -> b.fecha().compareTo(a.fecha()));
            return this;
        }

        Caso med(String nombre, String principio, String familia) {
            medicamentos.add(new ContextoClinico.Medicamento(nombre, principio, familia));
            return this;
        }

        ContextoClinico contexto() {
            return new ContextoClinico(1L, edad, sexo, AHORA, signos, resultados, medicamentos, diagnosticos, texto, criticos, ultimaNota);
        }
    }

    @Test
    void sepsisCriticaConQsofaYAlertaConSirsMasFoco() {
        ReglaSepsis regla = new ReglaSepsis();
        Optional<Hallazgo> qsofa = regla.evaluar(new Caso().signo(1, 95, 60, 118, 26, "38.9").contexto());
        assertThat(qsofa).hasValueSatisfying(h -> assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.CRITICA));

        Caso sirs = new Caso().signo(1, 120, 80, 104, 18, "38.6");
        sirs.texto = "tos productiva y fiebre de 3 dias";
        assertThat(regla.evaluar(sirs.contexto())).hasValueSatisfying(h -> assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.ALERTA));

        // Taquicardia y fiebre sin foco infeccioso ni qSOFA: no alerta
        assertThat(regla.evaluar(new Caso().signo(1, 120, 80, 104, 18, "38.6").contexto())).isEmpty();
    }

    @Test
    void lesionRenalPorSubidaDeCreatininaEn48Horas() {
        ReglaLesionRenal regla = new ReglaLesionRenal();
        Caso c = new Caso().lab("CREA", 40, "1.0", null).lab("CREA", 2, "1.4", null);
        assertThat(regla.evaluar(c.contexto())).hasValueSatisfying(h -> {
            assertThat(h.escala()).isEqualTo("KDIGO estadio 1");
            assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.ALERTA);
        });
        Caso grave = new Caso().lab("CREA", 120, "1.0", null).lab("CREA", 2, "2.3", null);
        assertThat(regla.evaluar(grave.contexto())).hasValueSatisfying(h -> assertThat(h.escala()).isEqualTo("KDIGO estadio 2"));
        assertThat(regla.evaluar(new Caso().lab("CREA", 40, "1.0", null).lab("CREA", 2, "1.1", null).contexto())).isEmpty();
    }

    @Test
    void metforminaConTfgBajaSegunCkdEpi2021() {
        ReglaMetforminaRenal regla = new ReglaMetforminaRenal();
        // Mujer de 70 años con creatinina 1,8: TFG ≈ 29 → contraindicada
        Caso c = new Caso().lab("CREA", 5, "1.8", null).med("Metformina 850 mg", "metformina", "biguanidas");
        c.edad = 70;
        c.sexo = "FEMENINO";
        assertThat(regla.evaluar(c.contexto())).hasValueSatisfying(h -> {
            assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.CRITICA);
            assertThat(h.puntaje().intValue()).isBetween(27, 31);
        });
        // Misma creatinina sin metformina: no hay alerta
        Caso sin = new Caso().lab("CREA", 5, "1.8", null);
        assertThat(regla.evaluar(sin.contexto())).isEmpty();
    }

    @Test
    void potasioAltoConLosartanYBajoConTiazida() {
        ReglaPotasio regla = new ReglaPotasio();
        Caso alto = new Caso().lab("K", 3, "6.1", "5.1").med("Losartán 50 mg", "losartan", "antagonistas del receptor de angiotensina");
        assertThat(regla.evaluar(alto.contexto())).hasValueSatisfying(h -> assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.CRITICA));
        Caso bajo = new Caso().lab("K", 3, "3.2", "5.1").med("Hidroclorotiazida 25 mg", "hidroclorotiazida", "diureticos tiazidicos");
        assertThat(regla.evaluar(bajo.contexto())).hasValueSatisfying(h -> assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.ALERTA));
        assertThat(regla.evaluar(new Caso().lab("K", 3, "6.1", "5.1").contexto())).isEmpty();
    }

    @Test
    void troponinaElevadaEsCriticaConDolorToracico() {
        ReglaSindromeCoronario regla = new ReglaSindromeCoronario();
        Caso conDolor = new Caso().lab("TNI", 1, "0.35", "0.04");
        conDolor.texto = "dolor toracico opresivo irradiado a brazo izquierdo";
        assertThat(regla.evaluar(conDolor.contexto())).hasValueSatisfying(h -> assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.CRITICA));
        assertThat(regla.evaluar(new Caso().lab("TNI", 1, "0.35", "0.04").contexto()))
                .hasValueSatisfying(h -> assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.ALERTA));
        assertThat(regla.evaluar(new Caso().lab("TNI", 1, "0.01", "0.04").contexto())).isEmpty();
    }

    @Test
    void crisisHipertensivaYHipertensionPersistente() {
        ReglaCrisisHipertensiva regla = new ReglaCrisisHipertensiva();
        assertThat(regla.evaluar(new Caso().signo(2, 192, 118, 80, 16, null).contexto()))
                .hasValueSatisfying(h -> assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.CRITICA));
        assertThat(regla.evaluar(new Caso().signo(30, 166, 98, 80, 16, null).signo(3, 162, 102, 80, 16, null).contexto()))
                .hasValueSatisfying(h -> assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.ALERTA));
        // La crisis de hace 10 horas ya no cuenta como crisis actual
        assertThat(regla.evaluar(new Caso().signo(10, 192, 118, 80, 16, null).contexto())).isEmpty();
    }

    @Test
    void hipoglucemiaConInsulinaEsCritica() {
        ReglaGlucosa regla = new ReglaGlucosa();
        Caso c = new Caso().lab("GLU", 1, "62", "99").med("Insulina glargina 100 UI/mL", "insulina glargina", "insulinas");
        assertThat(regla.evaluar(c.contexto())).hasValueSatisfying(h -> assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.CRITICA));
        assertThat(regla.evaluar(new Caso().lab("GLU", 1, "62", "99").contexto()))
                .hasValueSatisfying(h -> assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.ALERTA));
        assertThat(regla.evaluar(new Caso().lab("GLU", 1, "320", "99").contexto())).isEmpty();
    }

    @Test
    void caidaDeHemoglobina() {
        ReglaAnemia regla = new ReglaAnemia();
        assertThat(regla.evaluar(new Caso().lab("HGB", 72, "12.5", null).lab("HGB", 2, "10.1", null).contexto()))
                .hasValueSatisfying(h -> {
                    assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.ALERTA);
                    assertThat(h.puntaje()).isEqualByComparingTo("2.4");
                });
        assertThat(regla.evaluar(new Caso().lab("HGB", 2, "6.4", null).contexto()))
                .hasValueSatisfying(h -> assertThat(h.severidad()).isEqualTo(Hallazgo.Severidad.CRITICA));
    }

    @Test
    void criticoSinNotaSeCierraCuandoElMedicoFirma() {
        ReglaCriticoSinNota regla = new ReglaCriticoSinNota();
        Caso c = new Caso();
        c.criticos.add(new ContextoClinico.Critico("Laboratorio LAB-1", "Potasio 6,8 mmol/L", AHORA.minus(Duration.ofHours(5))));
        c.ultimaNota = AHORA.minus(Duration.ofHours(8));
        assertThat(regla.evaluar(c.contexto())).isPresent();
        c.ultimaNota = AHORA.minus(Duration.ofHours(1));
        assertThat(regla.evaluar(c.contexto())).isEmpty();
    }
}
