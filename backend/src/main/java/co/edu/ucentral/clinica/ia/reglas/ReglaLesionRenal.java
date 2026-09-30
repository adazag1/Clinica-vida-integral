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

/** Lesión renal aguda por creatinina: +0,3 mg/dL en 48 h o ≥ 1,5 veces el valor previo en 7 días. */
@Component
public class ReglaLesionRenal implements ReglaClinica {

    @Override
    public String codigo() {
        return "IRA";
    }

    @Override
    public String nombre() {
        return "Lesión renal aguda";
    }

    @Override
    public String descripcion() {
        return "Compara la creatinina más reciente con las de los 7 días previos y clasifica el estadio KDIGO.";
    }

    @Override
    public String referencia() {
        return "KDIGO Clinical Practice Guideline for Acute Kidney Injury (2012)";
    }

    @Override
    public Optional<Hallazgo> evaluar(ContextoClinico c) {
        List<ContextoClinico.Resultado> crea = c.resultados("CREA", Duration.ofDays(7));
        if (crea.size() < 2) return Optional.empty();
        ContextoClinico.Resultado actual = crea.getFirst();
        ContextoClinico.Resultado basal = crea.subList(1, crea.size()).stream()
                .min((a, b) -> a.valor().compareTo(b.valor())).orElseThrow();
        BigDecimal delta = actual.valor().subtract(basal.valor());
        double razon = actual.valor().doubleValue() / basal.valor().doubleValue();
        boolean en48h = Duration.between(basal.fecha(), actual.fecha()).toHours() <= 48;
        if (!(razon >= 1.5 || (en48h && delta.doubleValue() >= 0.3))) return Optional.empty();
        int estadio = razon >= 3 || actual.valor().doubleValue() >= 4.0 ? 3 : razon >= 2 ? 2 : 1;
        return Optional.of(new Hallazgo(estadio >= 2 ? Hallazgo.Severidad.CRITICA : Hallazgo.Severidad.ALERTA,
                BigDecimal.valueOf(estadio), "KDIGO estadio " + estadio,
                "Posible lesión renal aguda (KDIGO " + estadio + ")",
                "La creatinina pasó de " + Fmt.num(basal.valor()) + " a " + Fmt.num(actual.valor()) + " mg/dL ("
                        + BigDecimal.valueOf(razon).setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " veces).",
                List.of(Fmt.resultado("Creatinina actual", actual, c.ahora()), Fmt.resultado("Creatinina de referencia", basal, c.ahora())),
                "Revisar volemia, gasto urinario y medicamentos nefrotóxicos; ajustar dosis a la función renal."));
    }
}
