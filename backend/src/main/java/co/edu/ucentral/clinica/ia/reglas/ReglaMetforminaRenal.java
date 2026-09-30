package co.edu.ucentral.clinica.ia.reglas;

import co.edu.ucentral.clinica.ia.ContextoClinico;
import co.edu.ucentral.clinica.ia.Hallazgo;
import co.edu.ucentral.clinica.ia.ReglaClinica;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Metformina con tasa de filtración reducida: contraindicada bajo 30 y a revisar entre 30 y 45 mL/min/1,73 m². */
@Component
public class ReglaMetforminaRenal implements ReglaClinica {

    @Override
    public String codigo() {
        return "METFORMINA_TFG";
    }

    @Override
    public String nombre() {
        return "Metformina con función renal reducida";
    }

    @Override
    public String descripcion() {
        return "Calcula la tasa de filtración glomerular (CKD-EPI 2021) con la última creatinina, la edad y el sexo, "
                + "y la cruza con los medicamentos activos del paciente.";
    }

    @Override
    public String referencia() {
        return "Ecuación CKD-EPI 2021 (Inker et al., NEJM 2021); advertencia de la FDA sobre metformina y TFG (2016)";
    }

    @Override
    public Optional<Hallazgo> evaluar(ContextoClinico c) {
        if (!c.tomaMedicamento("metformina") || c.edad() == null || c.sexo() == null) return Optional.empty();
        Optional<ContextoClinico.Resultado> crea = c.ultimo("CREA", Duration.ofDays(30));
        if (crea.isEmpty()) return Optional.empty();
        double tfg = tfgCkdEpi2021(crea.get().valor().doubleValue(), c.edad(), "FEMENINO".equals(c.sexo()));
        if (tfg >= 45) return Optional.empty();
        boolean contraindicada = tfg < 30;
        BigDecimal valor = BigDecimal.valueOf(tfg).setScale(0, RoundingMode.HALF_UP);
        return Optional.of(new Hallazgo(contraindicada ? Hallazgo.Severidad.CRITICA : Hallazgo.Severidad.ALERTA, valor,
                "TFG CKD-EPI 2021 (mL/min/1,73 m²)",
                contraindicada ? "Metformina contraindicada por función renal" : "Metformina con función renal reducida",
                "La tasa de filtración estimada es " + valor + " mL/min/1,73 m²" + (contraindicada ? " (menor de 30)." : " (entre 30 y 45)."),
                List.of("Medicamento activo: " + String.join(", ", c.medicamentosCon("metformina")), Fmt.resultado("Creatinina", crea.get(), c.ahora()),
                        "Edad " + c.edad() + " años, sexo " + c.sexo().toLowerCase()),
                contraindicada ? "Suspender la metformina y considerar otra alternativa terapéutica."
                        : "No iniciar metformina; si ya la recibe, evaluar reducir la dosis y vigilar la función renal."));
    }

    /** CKD-EPI 2021 sin coeficiente de raza. */
    static double tfgCkdEpi2021(double creatinina, int edad, boolean mujer) {
        double kappa = mujer ? 0.7 : 0.9;
        double alfa = mujer ? -0.241 : -0.302;
        double r = creatinina / kappa;
        return 142 * Math.pow(Math.min(r, 1), alfa) * Math.pow(Math.max(r, 1), -1.200) * Math.pow(0.9938, edad) * (mujer ? 1.012 : 1);
    }
}
