package co.edu.ucentral.clinica.ia.reglas;

import co.edu.ucentral.clinica.ia.ContextoClinico;
import co.edu.ucentral.clinica.ia.Hallazgo;
import co.edu.ucentral.clinica.ia.ReglaClinica;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Crisis hipertensiva (≥ 180/120) o hipertensión persistente (dos tomas ≥ 160/100 en 48 h). */
@Component
public class ReglaCrisisHipertensiva implements ReglaClinica {

    @Override
    public String codigo() {
        return "CRISIS_HTA";
    }

    @Override
    public String nombre() {
        return "Crisis hipertensiva";
    }

    @Override
    public String descripcion() {
        return "Presión arterial de 180/120 mmHg o más en las últimas 6 horas, o dos tomas de 160/100 o más en 48 horas.";
    }

    @Override
    public String referencia() {
        return "Guía ACC/AHA de hipertensión arterial (2017); ESC/ESH (2018)";
    }

    @Override
    public Optional<Hallazgo> evaluar(ContextoClinico c) {
        Optional<ContextoClinico.Signo> reciente = c.signoReciente(Duration.ofHours(6))
                .filter(s -> (s.pas() != null && s.pas() >= 180) || (s.pad() != null && s.pad() >= 120));
        if (reciente.isPresent()) {
            ContextoClinico.Signo s = reciente.get();
            return Optional.of(new Hallazgo(Hallazgo.Severidad.CRITICA, BigDecimal.valueOf(s.pas() == null ? 0 : s.pas()),
                    "Presión sistólica (mmHg)", "Crisis hipertensiva",
                    "Presión arterial " + s.pas() + "/" + s.pad() + " mmHg.",
                    List.of("PA " + s.pas() + "/" + s.pad() + " mmHg (" + Fmt.hace(s.fecha(), c.ahora()) + ")"),
                    "Buscar daño de órgano blanco (neurológico, cardiaco, renal, retiniano) para diferenciar urgencia de emergencia hipertensiva."));
        }
        List<ContextoClinico.Signo> altas = c.signosDesde(Duration.ofHours(48)).stream()
                .filter(s -> (s.pas() != null && s.pas() >= 160) || (s.pad() != null && s.pad() >= 100)).toList();
        if (altas.size() < 2) return Optional.empty();
        return Optional.of(new Hallazgo(Hallazgo.Severidad.ALERTA, BigDecimal.valueOf(altas.size()), "Tomas ≥ 160/100 en 48 h",
                "Hipertensión no controlada", altas.size() + " tomas con presión de 160/100 mmHg o más en 48 horas.",
                altas.stream().limit(4).map(s -> "PA " + s.pas() + "/" + s.pad() + " mmHg (" + Fmt.hace(s.fecha(), c.ahora()) + ")").toList(),
                "Revisar adherencia y esquema antihipertensivo."));
    }
}
