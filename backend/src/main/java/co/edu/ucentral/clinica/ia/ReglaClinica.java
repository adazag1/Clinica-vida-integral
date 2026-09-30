package co.edu.ucentral.clinica.ia;

import java.util.Optional;

/**
 * Regla del motor: correlaciona datos del paciente y, si encuentra un patrón, devuelve un hallazgo
 * con su evidencia. Cada regla cita su referencia para que el comité médico la valide.
 */
public interface ReglaClinica {

    String codigo();

    String nombre();

    /** Qué busca, en palabras del médico. */
    String descripcion();

    /** Guía o escala en la que se basa. */
    String referencia();

    Optional<Hallazgo> evaluar(ContextoClinico c);
}
