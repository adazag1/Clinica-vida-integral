package co.edu.ucentral.clinica.comun.excepciones;

import java.util.Map;

/**
 * Validación que depende de varios campos a la vez (p. ej. el formato del documento según su tipo)
 * y por eso no cabe en una anotación. Se responde igual que una validación normal: 400 con los campos.
 */
public class DatosInvalidosException extends RuntimeException {

    private final Map<String, String> campos;

    public DatosInvalidosException(Map<String, String> campos) {
        super("Hay campos obligatorios o inválidos");
        this.campos = Map.copyOf(campos);
    }

    public Map<String, String> getCampos() {
        return campos;
    }
}
