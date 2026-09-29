package co.edu.ucentral.clinica.historia;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RFMT-010: compara cada signo con rangos parametrizados en {@code app.signos-vitales}
 * (application.yml) y lo clasifica como un semáforo: normal, anormal o crítico.
 */
@Component
public class EvaluadorSignos {

    /** Rango normal y umbrales críticos de un signo. Fuera de normal = anormal; fuera de crítico = crítico. */
    public record Rango(double normalMin, double normalMax, double criticoMin, double criticoMax) {
    }

    @ConfigurationProperties("app.signos-vitales")
    public record Rangos(Rango presionSistolica, Rango presionDiastolica, Rango frecuenciaCardiaca,
                         Rango frecuenciaRespiratoria, Rango temperatura, Rango saturacionOxigeno) {
    }

    public enum Nivel {
        NORMAL, ANORMAL, CRITICO
    }

    public record Alerta(String campo, String etiqueta, String valor, Nivel nivel, String mensaje) {
    }

    private final Rangos rangos;

    public EvaluadorSignos(Rangos rangos) {
        this.rangos = rangos;
    }

    public List<Alerta> evaluar(RegistroSignos s) {
        List<Alerta> alertas = new ArrayList<>();
        revisar(alertas, "presionSistolica", "Presión sistólica", s.getPresionSistolica(), "mmHg", rangos.presionSistolica());
        revisar(alertas, "presionDiastolica", "Presión diastólica", s.getPresionDiastolica(), "mmHg", rangos.presionDiastolica());
        revisar(alertas, "frecuenciaCardiaca", "Frecuencia cardiaca", s.getFrecuenciaCardiaca(), "lpm", rangos.frecuenciaCardiaca());
        revisar(alertas, "frecuenciaRespiratoria", "Frecuencia respiratoria", s.getFrecuenciaRespiratoria(), "rpm",
                rangos.frecuenciaRespiratoria());
        revisar(alertas, "temperatura", "Temperatura", s.getTemperatura() == null ? null : s.getTemperatura().doubleValue(),
                "°C", rangos.temperatura());
        revisar(alertas, "saturacionOxigeno", "Saturación de oxígeno", s.getSaturacionOxigeno(), "%", rangos.saturacionOxigeno());
        return alertas;
    }

    public static boolean hayCritica(List<Alerta> alertas) {
        return alertas.stream().anyMatch(a -> a.nivel() == Nivel.CRITICO);
    }

    private static void revisar(List<Alerta> alertas, String campo, String etiqueta, Number valor, String unidad, Rango r) {
        if (valor == null || r == null) {
            return;
        }
        double v = valor.doubleValue();
        Nivel nivel = v < r.criticoMin() || v > r.criticoMax() ? Nivel.CRITICO
                : v < r.normalMin() || v > r.normalMax() ? Nivel.ANORMAL
                : Nivel.NORMAL;
        if (nivel == Nivel.NORMAL) {
            return;
        }
        String texto = formato(v) + " " + unidad;
        alertas.add(new Alerta(campo, etiqueta, texto, nivel, etiqueta + " " + texto + (nivel == Nivel.CRITICO ? " (crítico" : " (fuera de rango")
                + "; normal " + formato(r.normalMin()) + "–" + formato(r.normalMax()) + ")"));
    }

    private static String formato(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }
}
