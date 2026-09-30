package co.edu.ucentral.clinica.ia.reglas;

import co.edu.ucentral.clinica.ia.ContextoClinico;
import co.edu.ucentral.clinica.ia.Hallazgo;
import co.edu.ucentral.clinica.ia.ReglaClinica;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Un resultado o informe crítico de las últimas 72 h sin una nota médica firmada después. */
@Component
public class ReglaCriticoSinNota implements ReglaClinica {

    @Override
    public String codigo() {
        return "CRITICO_SIN_NOTA";
    }

    @Override
    public String nombre() {
        return "Hallazgo crítico sin nota médica posterior";
    }

    @Override
    public String descripcion() {
        return "Resultado de laboratorio o informe de imágenes crítico de las últimas 72 horas sin ninguna consulta firmada después.";
    }

    @Override
    public String referencia() {
        return "Joint Commission NPSG.02.03.01: comunicación oportuna de resultados críticos";
    }

    @Override
    public Optional<Hallazgo> evaluar(ContextoClinico c) {
        List<ContextoClinico.Critico> sinNota = c.criticos().stream()
                .filter(x -> x.fecha().isAfter(c.ahora().minus(Duration.ofHours(72))))
                .filter(x -> c.ultimaNotaFirmada() == null || c.ultimaNotaFirmada().isBefore(x.fecha()))
                .toList();
        if (sinNota.isEmpty()) return Optional.empty();
        return Optional.of(new Hallazgo(Hallazgo.Severidad.ALERTA, BigDecimal.valueOf(sinNota.size()), "Hallazgos críticos sin nota",
                "Hallazgo crítico sin nota médica posterior",
                sinNota.size() + " hallazgo(s) crítico(s) sin una consulta firmada que los registre.",
                sinNota.stream().map(x -> x.origen() + ": " + x.descripcion() + " (" + Fmt.hace(x.fecha(), c.ahora()) + ")").toList(),
                "Registrar en la historia clínica la conducta frente al hallazgo crítico."));
    }
}
