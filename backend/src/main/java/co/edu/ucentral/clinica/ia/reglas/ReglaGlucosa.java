package co.edu.ucentral.clinica.ia.reglas;

import co.edu.ucentral.clinica.ia.ContextoClinico;
import co.edu.ucentral.clinica.ia.Hallazgo;
import co.edu.ucentral.clinica.ia.ReglaClinica;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Hipoglucemia (< 70 mg/dL), crítica si el paciente recibe insulina; hiperglucemia marcada (> 300) con insulina. */
@Component
public class ReglaGlucosa implements ReglaClinica {

    @Override
    public String codigo() {
        return "GLUCOSA";
    }

    @Override
    public String nombre() {
        return "Glucosa alterada con insulina";
    }

    @Override
    public String descripcion() {
        return "Glucosa menor de 70 mg/dL en 24 horas (crítica con insulina activa) o mayor de 300 mg/dL con insulina activa.";
    }

    @Override
    public String referencia() {
        return "ADA Standards of Care in Diabetes (2024), sección 6: hipoglucemia nivel 1 < 70 mg/dL";
    }

    @Override
    public Optional<Hallazgo> evaluar(ContextoClinico c) {
        Optional<ContextoClinico.Resultado> glu = c.ultimo("GLU", Duration.ofHours(24));
        if (glu.isEmpty()) return Optional.empty();
        double v = glu.get().valor().doubleValue();
        boolean insulina = c.tomaMedicamento("insulina");
        List<String> evidencia = new ArrayList<>();
        evidencia.add(Fmt.resultado("Glucosa", glu.get(), c.ahora()));
        if (insulina) evidencia.add("Medicamento activo: " + String.join(", ", c.medicamentosCon("insulina")));
        if (v < 70) {
            return Optional.of(new Hallazgo(insulina || v < 54 ? Hallazgo.Severidad.CRITICA : Hallazgo.Severidad.ALERTA, glu.get().valor(),
                    "Glucosa (mg/dL)", insulina ? "Hipoglucemia en paciente con insulina" : "Hipoglucemia",
                    "Glucosa de " + Fmt.num(glu.get().valor()) + " mg/dL" + (v < 54 ? " (nivel 2)." : "."), evidencia,
                    "Aplicar el protocolo de hipoglucemia, glucometría de control en 15 minutos y revisar la dosis de insulina."));
        }
        if (v > 300 && insulina) {
            return Optional.of(new Hallazgo(Hallazgo.Severidad.ALERTA, glu.get().valor(), "Glucosa (mg/dL)",
                    "Hiperglucemia marcada con insulina", "Glucosa de " + Fmt.num(glu.get().valor()) + " mg/dL pese a la insulina.",
                    evidencia, "Descartar cetoacidosis o estado hiperosmolar y ajustar el esquema de insulina."));
        }
        return Optional.empty();
    }
}
