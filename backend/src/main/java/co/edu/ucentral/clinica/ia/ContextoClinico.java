package co.edu.ucentral.clinica.ia;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que el motor sabe del paciente en un momento dado: signos, resultados validados, medicamentos,
 * diagnósticos, texto clínico reciente y hallazgos críticos. Las reglas solo leen este objeto, así
 * que se prueban sin base de datos.
 *
 * @param sexo          MASCULINO, FEMENINO u otro; puede ser nulo
 * @param signos        tomas de signos, la más reciente primero
 * @param resultados    resultados validados por código de analito (HGB, CREA, K...), el más reciente primero
 * @param textoClinico  motivo, enfermedad actual, examen y plan de las consultas recientes, normalizado
 * @param ultimaNotaFirmada firma de la consulta más reciente, para saber si alguien vio un hallazgo
 */
public record ContextoClinico(Long pacienteId, Integer edad, String sexo, Instant ahora, List<Signo> signos,
                              Map<String, List<Resultado>> resultados, List<Medicamento> medicamentos,
                              List<String> diagnosticos, String textoClinico, List<Critico> criticos,
                              Instant ultimaNotaFirmada) {

    public record Signo(Instant fecha, Integer pas, Integer pad, Integer fc, Integer fr, BigDecimal temperatura, Integer spo2) {
    }

    public record Resultado(Instant fecha, BigDecimal valor, BigDecimal refMin, BigDecimal refMax, String interpretacion,
                            String unidad) {
        public boolean critico() {
            return interpretacion != null && interpretacion.startsWith("CRITICO");
        }
    }

    /** @param familia y principio activo normalizados (sin tildes, en minúscula) */
    public record Medicamento(String nombre, String principioActivo, String familia) {
    }

    /** Hallazgo crítico (resultado de laboratorio o informe de imágenes) con su fecha. */
    public record Critico(String origen, String descripcion, Instant fecha) {
    }

    /** Signo más reciente dentro de la ventana, si lo hay. */
    public Optional<Signo> signoReciente(Duration ventana) {
        return signos.stream().filter(s -> s.fecha().isAfter(ahora.minus(ventana))).findFirst();
    }

    public List<Signo> signosDesde(Duration ventana) {
        return signos.stream().filter(s -> s.fecha().isAfter(ahora.minus(ventana))).toList();
    }

    /** Resultados de un analito dentro de la ventana, el más reciente primero. */
    public List<Resultado> resultados(String analito, Duration ventana) {
        return resultados.getOrDefault(analito, List.of()).stream().filter(r -> r.fecha().isAfter(ahora.minus(ventana))).toList();
    }

    public Optional<Resultado> ultimo(String analito, Duration ventana) {
        return resultados(analito, ventana).stream().findFirst();
    }

    public boolean tomaMedicamento(String... terminos) {
        return medicamentos.stream().anyMatch(m -> {
            for (String t : terminos) {
                if (m.principioActivo().contains(t) || m.familia().contains(t)) return true;
            }
            return false;
        });
    }

    public List<String> medicamentosCon(String... terminos) {
        return medicamentos.stream().filter(m -> {
            for (String t : terminos) {
                if (m.principioActivo().contains(t) || m.familia().contains(t)) return true;
            }
            return false;
        }).map(Medicamento::nombre).distinct().toList();
    }

    public boolean textoMenciona(String... terminos) {
        for (String t : terminos) {
            if (textoClinico.contains(t)) return true;
        }
        return false;
    }

    public boolean tieneDiagnostico(String... prefijos) {
        return diagnosticos.stream().anyMatch(d -> {
            for (String p : prefijos) {
                if (d.startsWith(p)) return true;
            }
            return false;
        });
    }
}
