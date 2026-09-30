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

/** Troponina elevada, con o sin dolor torácico documentado. */
@Component
public class ReglaSindromeCoronario implements ReglaClinica {

    /** Límite superior si el resultado no trae rango (percentil 99 de un ensayo de troponina I convencional). */
    private static final BigDecimal LIMITE_POR_DEFECTO = new BigDecimal("0.04");

    @Override
    public String codigo() {
        return "SCA";
    }

    @Override
    public String nombre() {
        return "Posible síndrome coronario agudo";
    }

    @Override
    public String descripcion() {
        return "Troponina por encima del percentil 99 en las últimas 24 horas; es crítica si además hay dolor torácico "
                + "en el texto clínico o un diagnóstico de dolor torácico, angina o infarto.";
    }

    @Override
    public String referencia() {
        return "Cuarta definición universal de infarto de miocardio (ESC/ACC/AHA/WHF, 2018)";
    }

    @Override
    public Optional<Hallazgo> evaluar(ContextoClinico c) {
        Optional<ContextoClinico.Resultado> tni = c.ultimo("TNI", Duration.ofHours(24));
        if (tni.isEmpty()) return Optional.empty();
        BigDecimal limite = tni.get().refMax() != null ? tni.get().refMax() : LIMITE_POR_DEFECTO;
        if (tni.get().valor().compareTo(limite) <= 0) return Optional.empty();
        boolean dolor = c.tieneDiagnostico("R07", "I20", "I21") || c.textoMenciona("dolor toracico", "dolor en el pecho", "precordial", "opresivo");
        List<String> evidencia = new ArrayList<>();
        evidencia.add(Fmt.resultado("Troponina I", tni.get(), c.ahora()) + " · límite " + Fmt.num(limite));
        if (dolor) evidencia.add("Dolor torácico documentado en la consulta");
        List<ContextoClinico.Resultado> serie = c.resultados("TNI", Duration.ofHours(24));
        if (serie.size() >= 2) {
            BigDecimal previa = serie.get(1).valor();
            evidencia.add("Curva: " + Fmt.num(previa) + " → " + Fmt.num(tni.get().valor()) + " ng/mL");
        }
        return Optional.of(new Hallazgo(dolor ? Hallazgo.Severidad.CRITICA : Hallazgo.Severidad.ALERTA, tni.get().valor(),
                "Troponina I (ng/mL)", dolor ? "Posible síndrome coronario agudo" : "Troponina elevada sin dolor torácico documentado",
                "La troponina está por encima del percentil 99" + (dolor ? " y hay dolor torácico documentado." : "."),
                evidencia,
                "Electrocardiograma inmediato, troponina seriada a las 3 horas y valoración por el protocolo de dolor torácico."));
    }
}
