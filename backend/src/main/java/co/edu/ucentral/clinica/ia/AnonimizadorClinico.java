package co.edu.ucentral.clinica.ia;

import co.edu.ucentral.clinica.pacientes.Normalizador;
import co.edu.ucentral.clinica.pacientes.Paciente;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Quita lo que identifica al paciente antes de que un dato salga del sistema (asistente de lenguaje
 * o dataset): nombre, documento, teléfono, correo, direcciones y fechas exactas. La edad se pasa a
 * un grupo de 5 años y las fechas a tiempos relativos. Es una desidentificación básica por reglas:
 * reduce el riesgo pero no reemplaza la revisión del oficial de protección de datos (Ley 1581 de 2012).
 */
public final class AnonimizadorClinico {

    private static final Pattern PALABRA = Pattern.compile("\\p{L}+");
    private static final Pattern CORREO = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern FECHA = Pattern.compile("\\b\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}\\b");
    /** Documentos, teléfonos, historias: 6 o más dígitos seguidos, con o sin separadores. */
    private static final Pattern NUMERO_LARGO = Pattern.compile("\\+?\\d[\\d .-]{4,}\\d");
    private static final Pattern DIRECCION = Pattern.compile(
            "(?i)\\b(calle|cll|carrera|cra|kr|avenida|av|diagonal|dg|transversal|tv)\\.?\\s*\\d+[a-z]?\\s*(#|no\\.?|n°)?\\s*[\\d a-z-]*");
    /** Palabras de 3 letras o menos no se buscan como nombre ("de", "la", "del"). */
    private static final int MIN_LETRAS_NOMBRE = 3;

    private AnonimizadorClinico() {
    }

    public static String grupoEdad(Integer edad) {
        if (edad == null) return "desconocida";
        if (edad >= 90) return "90+";
        int desde = edad / 5 * 5;
        return desde + "-" + (desde + 4);
    }

    public static String hace(Instant fecha, Instant ahora) {
        long horas = Duration.between(fecha, ahora).toHours();
        if (horas < 1) return "hace menos de 1 h";
        if (horas < 48) return "hace " + horas + " h";
        return "hace " + (horas / 24) + " días";
    }

    /** Reemplaza en un texto libre los datos que identifican a este paciente y los patrones genéricos. */
    public static String limpiar(String texto, Paciente p) {
        if (texto == null || texto.isBlank()) return "";
        String t = CORREO.matcher(texto).replaceAll("[CORREO]");
        t = DIRECCION.matcher(t).replaceAll("[DIRECCIÓN]");
        t = FECHA.matcher(t).replaceAll("[FECHA]");
        t = NUMERO_LARGO.matcher(t).replaceAll(m -> m.group().replaceAll("\\D", "").length() >= 6 ? "[NÚMERO]" : m.group());
        Set<String> nombres = Stream.of(p.getNombres(), p.getApellidos())
                .flatMap(n -> Arrays.stream(Normalizador.texto(n).split(" ")))
                .filter(n -> n.length() >= MIN_LETRAS_NOMBRE).collect(Collectors.toSet());
        Matcher m = PALABRA.matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, nombres.contains(Normalizador.texto(m.group())) ? "[PACIENTE]" : Matcher.quoteReplacement(m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Resumen estructurado del contexto, sin identificadores, para el asistente de lenguaje. */
    public static String resumen(ContextoClinico c, List<ConstructorContexto.TextoClinico> textos, Paciente p) {
        StringBuilder sb = new StringBuilder();
        sb.append("Paciente: sexo ").append(c.sexo() == null ? "no registrado" : c.sexo().toLowerCase())
                .append(", grupo de edad ").append(grupoEdad(c.edad())).append(" años.\n");
        if (!c.diagnosticos().isEmpty()) sb.append("Diagnósticos CIE-10 recientes: ").append(String.join(", ", c.diagnosticos())).append(".\n");
        if (!c.medicamentos().isEmpty()) {
            sb.append("Medicamentos activos: ").append(c.medicamentos().stream().map(ContextoClinico.Medicamento::nombre)
                    .distinct().collect(Collectors.joining(", "))).append(".\n");
        }
        c.signoReciente(Duration.ofDays(3)).ifPresent(s -> sb.append("Signos vitales (").append(hace(s.fecha(), c.ahora())).append("): ")
                .append("PA ").append(s.pas()).append("/").append(s.pad()).append(" mmHg, FC ").append(s.fc())
                .append(", FR ").append(s.fr()).append(", T ").append(s.temperatura()).append(" °C, SpO2 ").append(s.spo2()).append("%.\n"));
        if (!c.resultados().isEmpty()) {
            sb.append("Laboratorio (último valor de cada analito en 7 días):\n");
            c.resultados().forEach((analito, lista) -> lista.stream().filter(r -> r.fecha().isAfter(c.ahora().minus(Duration.ofDays(7))))
                    .findFirst().ifPresent(r -> sb.append("- ").append(analito).append(": ").append(r.valor().stripTrailingZeros().toPlainString())
                            .append(r.unidad() == null ? "" : " " + r.unidad()).append(" (").append(r.interpretacion().toLowerCase().replace('_', ' '))
                            .append(", ").append(hace(r.fecha(), c.ahora())).append(")\n")));
        }
        for (ConstructorContexto.TextoClinico t : textos) {
            sb.append("\nNota clínica (").append(hace(t.fecha(), c.ahora())).append(", ")
                    .append("FIRMADA".equals(t.estado()) ? "firmada" : "en curso").append("):\n").append(limpiar(t.texto(), p)).append("\n");
        }
        return sb.toString();
    }
}
