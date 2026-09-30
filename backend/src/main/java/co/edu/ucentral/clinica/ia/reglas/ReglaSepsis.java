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

/**
 * Tamizaje de sepsis: respuesta inflamatoria (SIRS) con un foco infeccioso probable, o qSOFA ≥ 2.
 * El estado mental del qSOFA no se registra en el sistema, así que se evalúa con 2 de sus 3 criterios.
 */
@Component
public class ReglaSepsis implements ReglaClinica {

    @Override
    public String codigo() {
        return "SEPSIS";
    }

    @Override
    public String nombre() {
        return "Riesgo de sepsis";
    }

    @Override
    public String descripcion() {
        return "Fiebre o hipotermia, taquicardia, taquipnea o leucocitos alterados junto con un foco infeccioso probable "
                + "(PCR elevada, diagnóstico o texto clínico de infección); o qSOFA de 2 o más.";
    }

    @Override
    public String referencia() {
        return "Sepsis-3 (Singer et al., JAMA 2016) para qSOFA; criterios SIRS (ACCP/SCCM 1992)";
    }

    @Override
    public Optional<Hallazgo> evaluar(ContextoClinico c) {
        Optional<ContextoClinico.Signo> signo = c.signoReciente(Duration.ofHours(24));
        if (signo.isEmpty()) return Optional.empty();
        ContextoClinico.Signo s = signo.get();
        List<String> evidencia = new ArrayList<>();
        int sirs = 0;
        if (s.temperatura() != null && (s.temperatura().doubleValue() > 38 || s.temperatura().doubleValue() < 36)) {
            sirs++;
            evidencia.add("Temperatura " + Fmt.num(s.temperatura()) + " °C (" + Fmt.hace(s.fecha(), c.ahora()) + ")");
        }
        if (s.fc() != null && s.fc() > 90) {
            sirs++;
            evidencia.add("Frecuencia cardiaca " + s.fc() + " lpm");
        }
        if (s.fr() != null && s.fr() > 20) {
            sirs++;
            evidencia.add("Frecuencia respiratoria " + s.fr() + " rpm");
        }
        Optional<ContextoClinico.Resultado> wbc = c.ultimo("WBC", Duration.ofHours(48));
        if (wbc.isPresent() && (Fmt.mayor(wbc.get().valor(), 12) || Fmt.menor(wbc.get().valor(), 4))) {
            sirs++;
            evidencia.add(Fmt.resultado("Leucocitos", wbc.get(), c.ahora()));
        }
        int qsofa = (s.fr() != null && s.fr() >= 22 ? 1 : 0) + (s.pas() != null && s.pas() <= 100 ? 1 : 0);
        if (s.pas() != null && s.pas() <= 100) evidencia.add("Presión sistólica " + s.pas() + " mmHg");

        List<String> foco = new ArrayList<>();
        c.ultimo("PCR", Duration.ofHours(48)).filter(r -> Fmt.mayor(r.valor(), 10))
                .ifPresent(r -> foco.add(Fmt.resultado("PCR", r, c.ahora())));
        if (c.tieneDiagnostico("A", "B", "J1", "J2", "N39", "L03", "K35")) foco.add("Diagnóstico infeccioso registrado");
        else if (c.textoMenciona("neumonia", "infeccion", "celulitis", "absceso", "pielonefritis", "fiebre")) foco.add("El texto clínico menciona un proceso infeccioso");

        boolean dispara = qsofa >= 2 || (sirs >= 2 && !foco.isEmpty());
        if (!dispara) return Optional.empty();
        evidencia.addAll(foco);
        Hallazgo.Severidad severidad = qsofa >= 2 ? Hallazgo.Severidad.CRITICA : Hallazgo.Severidad.ALERTA;
        return Optional.of(new Hallazgo(severidad, BigDecimal.valueOf(qsofa), "qSOFA " + qsofa + "/2 evaluables · SIRS " + sirs + "/4",
                qsofa >= 2 ? "Posible sepsis: qSOFA positivo" : "Respuesta inflamatoria con foco infeccioso probable",
                "Hay " + sirs + " criterio(s) de respuesta inflamatoria" + (foco.isEmpty() ? "" : " y un foco infeccioso probable")
                        + ". El qSOFA no incluye el estado mental porque no está registrado.",
                evidencia,
                "Valorar estado de conciencia, lactato y hemocultivos; considerar el paquete de la primera hora de sepsis según el protocolo institucional."));
    }
}
