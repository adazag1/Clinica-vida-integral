package co.edu.ucentral.clinica.ia.reglas;

import co.edu.ucentral.clinica.ia.ContextoClinico;
import co.edu.ucentral.clinica.ia.Hallazgo;
import co.edu.ucentral.clinica.ia.ReglaClinica;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Potasio alterado en un paciente que recibe un medicamento que lo empeora. */
@Component
public class ReglaPotasio implements ReglaClinica {

    private static final String[] ELEVAN = {"antagonistas del receptor de angiotensina", "ieca", "heparina", "ahorrador"};
    private static final String[] BAJAN = {"tiazid", "diuretico", "insulina", "agonistas beta"};

    @Override
    public String codigo() {
        return "POTASIO";
    }

    @Override
    public String nombre() {
        return "Potasio alterado con medicamento que lo empeora";
    }

    @Override
    public String descripcion() {
        return "Hiperpotasemia (≥ 5,5 mmol/L) con IECA, ARA II o heparina; o hipopotasemia (< 3,5 mmol/L) con diuréticos, "
                + "insulina o agonistas beta 2.";
    }

    @Override
    public String referencia() {
        return "Fichas técnicas de los medicamentos; KDIGO 2020 para manejo del potasio en enfermedad renal";
    }

    @Override
    public Optional<Hallazgo> evaluar(ContextoClinico c) {
        Optional<ContextoClinico.Resultado> k = c.ultimo("K", Duration.ofHours(72));
        if (k.isEmpty()) return Optional.empty();
        double valor = k.get().valor().doubleValue();
        boolean alto = valor >= 5.5;
        boolean bajo = valor < 3.5;
        if (!alto && !bajo) return Optional.empty();
        List<String> meds = alto ? c.medicamentosCon(ELEVAN) : c.medicamentosCon(BAJAN);
        if (meds.isEmpty()) return Optional.empty();
        List<String> evidencia = new ArrayList<>();
        evidencia.add(Fmt.resultado("Potasio", k.get(), c.ahora()));
        evidencia.add("Medicamento activo: " + String.join(", ", meds));
        boolean grave = valor >= 6.0 || valor < 3.0;
        return Optional.of(new Hallazgo(grave ? Hallazgo.Severidad.CRITICA : Hallazgo.Severidad.ALERTA, k.get().valor(), "Potasio (mmol/L)",
                alto ? "Hiperpotasemia con medicamento que eleva el potasio" : "Hipopotasemia con medicamento que baja el potasio",
                "El potasio está en " + Fmt.num(k.get().valor()) + " mmol/L y el paciente recibe " + String.join(", ", meds) + ".",
                evidencia,
                alto ? "Revisar la necesidad del medicamento, tomar un electrocardiograma y repetir el potasio."
                        : "Reponer potasio según protocolo y revisar la dosis del medicamento."));
    }
}
