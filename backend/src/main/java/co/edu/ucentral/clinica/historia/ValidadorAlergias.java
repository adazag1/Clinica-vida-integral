package co.edu.ucentral.clinica.historia;

import co.edu.ucentral.clinica.pacientes.Normalizador;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * RFMT-003: cruza la sustancia de cada alergia activa con el principio activo, la familia y los
 * sinónimos del medicamento, sin importar tildes ni plurales. Así "Penicilina" bloquea la
 * amoxicilina (familia penicilinas) y "Aspirina" bloquea el ácido acetilsalicílico.
 */
@Component
public class ValidadorAlergias {

    /** Términos muy cortos ("asa") solo cuentan si coinciden completos, para no dar falsos positivos. */
    private static final int MINIMO_PARCIAL = 4;

    public Optional<Antecedente> conflicto(Collection<Antecedente> alergias, Medicamento m) {
        List<String> terminos = terminosMedicamento(m);
        return alergias.stream()
                .filter(a -> a.getTipo() == Antecedente.Tipo.ALERGIA && a.isActivo())
                .filter(a -> coincide(singular(Normalizador.texto(a.getDescripcion())), terminos))
                .findFirst();
    }

    private static List<String> terminosMedicamento(Medicamento m) {
        List<String> terminos = new ArrayList<>();
        terminos.add(m.getPrincipioActivo());
        terminos.add(m.getFamilia());
        terminos.add(m.getNombre());
        if (m.getSinonimos() != null) {
            terminos.addAll(Arrays.asList(m.getSinonimos().split(",")));
        }
        return terminos.stream().map(Normalizador::texto).map(ValidadorAlergias::singular).filter(t -> !t.isEmpty()).toList();
    }

    private static boolean coincide(String alergia, List<String> terminos) {
        if (alergia.isEmpty()) {
            return false;
        }
        for (String t : terminos) {
            if (t.equals(alergia)) {
                return true;
            }
            if (alergia.length() >= MINIMO_PARCIAL && (t.contains(alergia) || contienePalabra(alergia, t))) {
                return true;
            }
        }
        return false;
    }

    /** "alergia a la amoxicilina" contiene el término "amoxicilina". */
    private static boolean contienePalabra(String alergia, String termino) {
        return termino.length() >= MINIMO_PARCIAL && (" " + alergia + " ").contains(" " + termino + " ");
    }

    /** Quita plurales simples palabra por palabra: "penicilinas" → "penicilina", "aines" → "aine". */
    static String singular(String texto) {
        return Arrays.stream(texto.split(" "))
                .map(p -> p.length() > 4 && p.endsWith("s") ? p.substring(0, p.length() - 1) : p)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }
}
