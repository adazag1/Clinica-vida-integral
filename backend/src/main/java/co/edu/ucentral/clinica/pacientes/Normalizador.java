package co.edu.ucentral.clinica.pacientes;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;

/**
 * Normaliza nombres para buscar y comparar sin importar tildes, mayúsculas ni espacios
 * ("José  Pérez" == "jose perez"). Se hace en Java para no depender de extensiones de
 * PostgreSQL (unaccent, pg_trgm) que en Azure hay que habilitar aparte.
 */
public final class Normalizador {

    private Normalizador() {
    }

    public static String texto(String valor) {
        if (valor == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Deja solo letras y dígitos: "1.020-345" → "1020345". */
    public static String documento(String valor) {
        return valor == null ? "" : valor.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    /** Deja solo dígitos: "(+57) 300 123-4567" → "573001234567". */
    public static String telefono(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    /**
     * Similitud entre 0 y 1 de dos nombres ya normalizados. Toma la mejor entre la comparación
     * directa y la de palabras ordenadas, para que "perez juan" y "juan perez" coincidan.
     */
    public static double similitud(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        return Math.max(ratio(a, b), ratio(ordenarPalabras(a), ordenarPalabras(b)));
    }

    private static String ordenarPalabras(String s) {
        return String.join(" ", Arrays.stream(s.split(" ")).sorted().toList());
    }

    private static double ratio(String a, String b) {
        int max = Math.max(a.length(), b.length());
        return 1.0 - (double) distancia(a, b) / max;
    }

    /**
     * Distancia de edición de Damerau-Levenshtein (variante OSA): letras cambiadas, sobrantes,
     * faltantes o intercambiadas cuentan como un error cada una. Así "Jhon" vs "John" es 1 error, no 2.
     */
    private static int distancia(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + costo);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
