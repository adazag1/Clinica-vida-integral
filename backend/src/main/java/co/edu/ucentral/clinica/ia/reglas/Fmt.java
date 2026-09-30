package co.edu.ucentral.clinica.ia.reglas;

import co.edu.ucentral.clinica.ia.ContextoClinico;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Texto de la evidencia: valores con coma decimal y antigüedad relativa ("hace 3 h"). */
final class Fmt {

    private Fmt() {
    }

    static String num(BigDecimal v) {
        return v == null ? "—" : v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    static String hace(Instant fecha, Instant ahora) {
        long min = Math.max(0, Duration.between(fecha, ahora).toMinutes());
        if (min < 60) return "hace " + min + " min";
        if (min < 48 * 60) return "hace " + (min / 60) + " h";
        return "hace " + (min / 1440) + " días";
    }

    static String resultado(String nombre, ContextoClinico.Resultado r, Instant ahora) {
        return nombre + " " + num(r.valor()) + (r.unidad() == null ? "" : " " + r.unidad()) + " (" + hace(r.fecha(), ahora) + ")";
    }

    static boolean mayor(BigDecimal v, double umbral) {
        return v != null && v.doubleValue() > umbral;
    }

    static boolean menor(BigDecimal v, double umbral) {
        return v != null && v.doubleValue() < umbral;
    }
}
