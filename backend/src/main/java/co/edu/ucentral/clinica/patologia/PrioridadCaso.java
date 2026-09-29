package co.edu.ucentral.clinica.patologia;

import java.time.Duration;

/** RFPAT-003: el orden de declaración es el orden de atención; el intraoperatorio tiene cola aparte. */
public enum PrioridadCaso {
    INTRAOPERATORIO("Intraoperatorio (congelación)", Duration.ofMinutes(30)),
    URGENTE("Urgente", Duration.ofHours(24)),
    RUTINA("Rutina", Duration.ofDays(7));

    private final String descripcion;
    /** Tiempo objetivo de respuesta, desde la recepción de la muestra. */
    private final Duration objetivo;

    PrioridadCaso(String descripcion, Duration objetivo) {
        this.descripcion = descripcion;
        this.objetivo = objetivo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Duration getObjetivo() {
        return objetivo;
    }
}
