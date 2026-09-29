package co.edu.ucentral.clinica.laboratorio;

/** Semáforo del resultado frente a su rango (normal / anormal / crítico). */
public enum Interpretacion {
    NORMAL("Normal"),
    BAJO("Bajo"),
    ALTO("Alto"),
    CRITICO_BAJO("Crítico bajo"),
    CRITICO_ALTO("Crítico alto");

    private final String descripcion;

    Interpretacion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isCritico() {
        return this == CRITICO_BAJO || this == CRITICO_ALTO;
    }
}
