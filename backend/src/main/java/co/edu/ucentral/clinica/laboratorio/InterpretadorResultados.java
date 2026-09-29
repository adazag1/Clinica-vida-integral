package co.edu.ucentral.clinica.laboratorio;

import co.edu.ucentral.clinica.pacientes.Sexo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * RFLC-003, RFLC-004 y RFLC-006: elige el rango que aplica al paciente, clasifica el valor y
 * calcula el delta check contra el resultado anterior.
 */
@Component
public class InterpretadorResultados {

    /**
     * El rango más específico que aplica: primero el del sexo del paciente sobre el de ambos
     * sexos, y entre ellos el de rango de edad más estrecho (p. ej. el pediátrico sobre el general).
     */
    public Optional<RangoReferencia> rango(List<RangoReferencia> rangos, Sexo sexo, int edad) {
        return rangos.stream()
                .filter(r -> r.aplica(sexo, edad))
                .min(Comparator.comparing((RangoReferencia r) -> r.getSexo() == null ? 1 : 0)
                        .thenComparingInt(r -> r.getEdadMax() - r.getEdadMin()));
    }

    public Interpretacion interpretar(BigDecimal valor, RangoReferencia r) {
        if (r == null) {
            return Interpretacion.NORMAL;
        }
        if (r.getCriticoMin() != null && valor.compareTo(r.getCriticoMin()) < 0) return Interpretacion.CRITICO_BAJO;
        if (r.getCriticoMax() != null && valor.compareTo(r.getCriticoMax()) > 0) return Interpretacion.CRITICO_ALTO;
        if (r.getRefMin() != null && valor.compareTo(r.getRefMin()) < 0) return Interpretacion.BAJO;
        if (r.getRefMax() != null && valor.compareTo(r.getRefMax()) > 0) return Interpretacion.ALTO;
        return Interpretacion.NORMAL;
    }

    /** Cambio porcentual contra el valor previo (null si no hay previo o es cero). */
    public BigDecimal delta(BigDecimal actual, BigDecimal previo) {
        if (previo == null || previo.signum() == 0) {
            return null;
        }
        return actual.subtract(previo).multiply(BigDecimal.valueOf(100))
                .divide(previo.abs(), 1, RoundingMode.HALF_UP);
    }

    public boolean superaDelta(BigDecimal delta, BigDecimal umbral) {
        return delta != null && umbral != null && delta.abs().compareTo(umbral) > 0;
    }
}
