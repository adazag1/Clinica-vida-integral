package co.edu.ucentral.clinica.auditoriaclinica;

import co.edu.ucentral.clinica.historia.Diagnostico;
import co.edu.ucentral.clinica.pacientes.Normalizador;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RFAU-003: sugiere códigos CIE-10 a partir de lo que el médico escribió (motivo, enfermedad actual,
 * examen físico, plan). Busca las palabras del nombre del diagnóstico y términos clínicos frecuentes,
 * y muestra la evidencia encontrada. Es una ayuda: el auditor siempre elige y confirma el código.
 * (En la Fase 9 se complementa con el motor de IA.)
 */
@Component
public class SugeridorDiagnosticos {

    public record Sugerencia(String codigo, String nombre, double puntaje, List<String> evidencia, boolean registradoPorMedico) {
    }

    /** Palabras del nombre CIE-10 que no dicen nada del diagnóstico. */
    private static final Set<String> GENERICAS = Set.of("especificada", "especificado", "especificadas", "otros", "otras", "otro",
            "sitio", "presunto", "origen", "primaria", "esencial", "complicaciones", "tipo", "infeccioso", "aguda", "agudo",
            "cronica", "cronico", "general", "medico", "vias", "respiratorias", "superiores", "enfermedad", "trastorno", "episodio");

    /** Términos clínicos frecuentes que el médico escribe para cada código. */
    private static final Map<String, List<String>> TERMINOS = Map.ofEntries(
            Map.entry("R50.9", List.of("fiebre", "febril", "hipertermia")),
            Map.entry("R05", List.of("tos")),
            Map.entry("R51", List.of("cefalea", "dolor de cabeza")),
            Map.entry("J18.9", List.of("neumonia", "consolidacion", "broncograma")),
            Map.entry("I10", List.of("hipertension", "hta", "cifras tensionales elevadas")),
            Map.entry("E11.9", List.of("diabetes", "dm2", "hiperglicemia")),
            Map.entry("K29.7", List.of("gastritis", "epigastralgia")),
            Map.entry("N39.0", List.of("infeccion urinaria", "disuria", "polaquiuria", "ivu")),
            Map.entry("J45.9", List.of("asma", "sibilancias", "broncoespasmo")),
            Map.entry("R10.4", List.of("dolor abdominal")),
            Map.entry("R07.4", List.of("dolor toracico", "dolor en el pecho", "precordialgia")),
            Map.entry("J02.9", List.of("faringitis", "odinofagia")),
            Map.entry("J03.9", List.of("amigdalitis", "placas amigdalinas")),
            Map.entry("A09", List.of("diarrea", "gastroenteritis", "deposiciones liquidas")),
            Map.entry("M54.5", List.of("lumbalgia", "lumbago", "dolor lumbar")),
            Map.entry("S93.4", List.of("esguince", "torcedura de tobillo")),
            Map.entry("I21.9", List.of("infarto", "iam", "supradesnivel")),
            Map.entry("R11", List.of("nausea", "vomito", "emesis")),
            Map.entry("J00", List.of("resfriado", "rinorrea", "congestion nasal")),
            Map.entry("F41.1", List.of("ansiedad")),
            Map.entry("G43.9", List.of("migrana", "jaqueca")),
            Map.entry("E66.9", List.of("obesidad")),
            Map.entry("E78.5", List.of("dislipidemia", "hipercolesterolemia", "hipertrigliceridemia")),
            Map.entry("K35.8", List.of("apendicitis", "signo de mcburney", "blumberg")),
            Map.entry("L03.9", List.of("celulitis")),
            Map.entry("I50.9", List.of("insuficiencia cardiaca", "falla cardiaca")),
            Map.entry("J44.9", List.of("epoc")));

    private static final int MAX_SUGERENCIAS = 6;

    public List<Sugerencia> sugerir(Collection<String> textos, Collection<Diagnostico> catalogo, Set<String> registradosPorMedico) {
        String texto = " " + textos.stream().filter(t -> t != null && !t.isBlank()).map(Normalizador::texto)
                .collect(Collectors.joining(" ")) + " ";
        Set<String> raices = Arrays.stream(texto.trim().split(" ")).filter(p -> p.length() >= 4).map(SugeridorDiagnosticos::raiz)
                .collect(Collectors.toSet());
        List<Sugerencia> resultado = new ArrayList<>();
        for (Diagnostico d : catalogo) {
            if (!d.isActivo()) continue;
            List<String> evidencia = new ArrayList<>();
            double puntaje = 0;
            for (String termino : TERMINOS.getOrDefault(d.getCodigo(), List.of())) {
                if (texto.contains(" " + termino + " ") || (termino.length() >= 5 && texto.contains(" " + termino))) {
                    evidencia.add(termino);
                    puntaje += 2;
                }
            }
            List<String> significativas = Arrays.stream(Normalizador.texto(d.getNombre()).split(" "))
                    .filter(p -> p.length() >= 4 && !GENERICAS.contains(p)).distinct().toList();
            List<String> encontradas = significativas.stream().filter(p -> raices.contains(raiz(p))).toList();
            if (!significativas.isEmpty() && !encontradas.isEmpty()) {
                double fraccion = (double) encontradas.size() / significativas.size();
                // Con una sola palabra en común de dos ("pecho" en "angina de pecho") la evidencia es débil
                if (fraccion > 0.5 || significativas.size() == 1) {
                    puntaje += 1 + fraccion;
                    encontradas.stream().filter(p -> evidencia.stream().noneMatch(e -> e.contains(p))).forEach(evidencia::add);
                }
            }
            boolean delMedico = registradosPorMedico.contains(d.getCodigo());
            if (delMedico) puntaje += 5;
            if (puntaje > 0) {
                resultado.add(new Sugerencia(d.getCodigo(), d.getNombre(), Math.round(puntaje * 10) / 10.0,
                        List.copyOf(new LinkedHashSet<>(evidencia)), delMedico));
            }
        }
        return resultado.stream().sorted(Comparator.comparingDouble(Sugerencia::puntaje).reversed()).limit(MAX_SUGERENCIAS).toList();
    }

    /** Raíz de 6 letras: "neumonia"/"neumonias", "consolidacion"/"consolidaciones". */
    private static String raiz(String palabra) {
        return palabra.length() <= 6 ? palabra : palabra.substring(0, 6);
    }
}
