package co.edu.ucentral.clinica.admisiones;

import co.edu.ucentral.clinica.admisiones.dto.SignosVitales;
import co.edu.ucentral.clinica.admisiones.dto.SugerenciaTriage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * RFADM-006: sugiere el nivel de triage (I a V, Resolución 5596 de 2015) a partir de los signos
 * vitales. Toma el nivel más grave que cumpla algún criterio y devuelve los criterios que lo
 * activaron, para que quien clasifica vea el porqué.
 *
 * <p>Es un apoyo, no una decisión: los umbrales son para adultos y no conocen los síntomas del
 * paciente (dolor torácico, sangrado, etc.). Quien clasifica asigna el nivel final y, si difiere
 * del sugerido, debe justificarlo. Los umbrales deben validarlos el comité médico de la clínica.</p>
 */
@Component
public class CalculadoraTriage {

    private static final int EDAD_ADULTO = 14;

    public SugerenciaTriage sugerir(SignosVitales s, Integer edad) {
        List<String> nivel1 = new ArrayList<>();
        List<String> nivel2 = new ArrayList<>();
        List<String> nivel3 = new ArrayList<>();
        List<String> nivel4 = new ArrayList<>();

        int fc = s.frecuenciaCardiaca();
        int fr = s.frecuenciaRespiratoria();
        int pas = s.presionSistolica();
        int pad = s.presionDiastolica();
        double temp = s.temperatura().doubleValue();
        int spo2 = s.saturacionOxigeno();
        int glasgow = s.glasgow();
        int dolor = s.dolor();

        // Nivel I: riesgo vital inmediato
        if (glasgow <= 8) nivel1.add("Glasgow " + glasgow + " (≤ 8)");
        if (spo2 < 85) nivel1.add("Saturación " + spo2 + " % (< 85 %)");
        if (pas < 70) nivel1.add("Presión sistólica " + pas + " mmHg (< 70)");
        if (fc < 40 || fc > 150) nivel1.add("Frecuencia cardiaca " + fc + " lpm (< 40 o > 150)");
        if (fr < 8 || fr > 35) nivel1.add("Frecuencia respiratoria " + fr + " rpm (< 8 o > 35)");

        // Nivel II: muy urgente
        if (glasgow >= 9 && glasgow <= 13) nivel2.add("Glasgow " + glasgow + " (9 a 13)");
        if (spo2 >= 85 && spo2 <= 89) nivel2.add("Saturación " + spo2 + " % (85 a 89 %)");
        if (pas >= 70 && pas <= 89) nivel2.add("Presión sistólica " + pas + " mmHg (70 a 89)");
        if (pas >= 220 || pad >= 120) nivel2.add("Presión " + pas + "/" + pad + " mmHg (crisis hipertensiva)");
        if ((fc >= 40 && fc <= 49) || (fc >= 131 && fc <= 150)) nivel2.add("Frecuencia cardiaca " + fc + " lpm");
        if (fr >= 30 && fr <= 35) nivel2.add("Frecuencia respiratoria " + fr + " rpm (30 a 35)");
        if (temp >= 40 || temp < 35) nivel2.add("Temperatura " + formato(s.temperatura()) + " °C");
        if (dolor >= 8) nivel2.add("Dolor " + dolor + "/10 (severo)");

        // Nivel III: urgente
        if (spo2 >= 90 && spo2 <= 93) nivel3.add("Saturación " + spo2 + " % (90 a 93 %)");
        if (fc >= 111 && fc <= 130) nivel3.add("Frecuencia cardiaca " + fc + " lpm (111 a 130)");
        if (fr >= 25 && fr <= 29) nivel3.add("Frecuencia respiratoria " + fr + " rpm (25 a 29)");
        if (temp >= 39 && temp < 40) nivel3.add("Temperatura " + formato(s.temperatura()) + " °C (39 a 39,9)");
        if ((pas >= 180 && pas < 220) || (pad >= 110 && pad < 120)) nivel3.add("Presión " + pas + "/" + pad + " mmHg (elevada)");
        if (dolor >= 5 && dolor <= 7) nivel3.add("Dolor " + dolor + "/10 (moderado)");

        // Nivel IV: prioritario sin riesgo
        if (temp >= 38 && temp < 39) nivel4.add("Temperatura " + formato(s.temperatura()) + " °C (fiebre)");
        if (fc >= 101 && fc <= 110) nivel4.add("Frecuencia cardiaca " + fc + " lpm (101 a 110)");
        if (fr >= 21 && fr <= 24) nivel4.add("Frecuencia respiratoria " + fr + " rpm (21 a 24)");
        if (dolor >= 1 && dolor <= 4) nivel4.add("Dolor " + dolor + "/10 (leve)");

        int nivel;
        List<String> criterios;
        if (!nivel1.isEmpty()) {
            nivel = 1;
            criterios = nivel1;
        } else if (!nivel2.isEmpty()) {
            nivel = 2;
            criterios = nivel2;
        } else if (!nivel3.isEmpty()) {
            nivel = 3;
            criterios = nivel3;
        } else if (!nivel4.isEmpty()) {
            nivel = 4;
            criterios = nivel4;
        } else {
            nivel = 5;
            criterios = new ArrayList<>(List.of("Signos vitales dentro de rangos normales"));
        }

        List<String> advertencias = new ArrayList<>();
        if (edad != null && edad < EDAD_ADULTO) {
            advertencias.add("Paciente pediátrico (" + edad + " años): los umbrales son de adultos, "
                    + "clasifique con la escala pediátrica.");
        }
        return new SugerenciaTriage(nivel, NivelTriage.descripcion(nivel), criterios, advertencias);
    }

    private static String formato(BigDecimal temperatura) {
        return temperatura.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
