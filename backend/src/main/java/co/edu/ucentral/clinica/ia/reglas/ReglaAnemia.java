package co.edu.ucentral.clinica.ia.reglas;

import co.edu.ucentral.clinica.ia.ContextoClinico;
import co.edu.ucentral.clinica.ia.Hallazgo;
import co.edu.ucentral.clinica.ia.ReglaClinica;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Anemia grave (< 7 g/dL) o caída de la hemoglobina de 2 g/dL o más en 7 días (posible sangrado). */
@Component
public class ReglaAnemia implements ReglaClinica {

    @Override
    public String codigo() {
        return "ANEMIA";
    }

    @Override
    public String nombre() {
        return "Descenso de hemoglobina";
    }

    @Override
    public String descripcion() {
        return "Hemoglobina menor de 7 g/dL, o una caída de 2 g/dL o más frente al valor de los 7 días previos.";
    }

    @Override
    public String referencia() {
        return "AABB 2023 (umbral transfusional restrictivo de 7 g/dL); definición de sangrado mayor ISTH (caída ≥ 2 g/dL)";
    }

    @Override
    public Optional<Hallazgo> evaluar(ContextoClinico c) {
        List<ContextoClinico.Resultado> hgb = c.resultados("HGB", Duration.ofDays(7));
        if (hgb.isEmpty()) return Optional.empty();
        ContextoClinico.Resultado actual = hgb.getFirst();
        Optional<ContextoClinico.Resultado> previo = hgb.stream().skip(1).max((a, b) -> a.valor().compareTo(b.valor()));
        BigDecimal caida = previo.map(p -> p.valor().subtract(actual.valor())).orElse(BigDecimal.ZERO);
        boolean grave = actual.valor().doubleValue() < 7;
        boolean cae = caida.doubleValue() >= 2;
        if (!grave && !cae) return Optional.empty();
        List<String> evidencia = new ArrayList<>();
        evidencia.add(Fmt.resultado("Hemoglobina actual", actual, c.ahora()));
        previo.filter(p -> cae).ifPresent(p -> evidencia.add(Fmt.resultado("Hemoglobina previa", p, c.ahora())));
        if (c.tomaMedicamento("heparina")) evidencia.add("Recibe anticoagulante: " + String.join(", ", c.medicamentosCon("heparina")));
        return Optional.of(new Hallazgo(grave ? Hallazgo.Severidad.CRITICA : Hallazgo.Severidad.ALERTA, cae ? caida : actual.valor(),
                cae ? "Caída de hemoglobina (g/dL)" : "Hemoglobina (g/dL)",
                grave ? "Anemia grave" : "Caída de hemoglobina: posible sangrado",
                cae ? "La hemoglobina bajó " + Fmt.num(caida) + " g/dL." : "Hemoglobina de " + Fmt.num(actual.valor()) + " g/dL.",
                evidencia, "Buscar sangrado activo, repetir hemograma y valorar la necesidad de transfusión."));
    }
}
