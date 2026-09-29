package co.edu.ucentral.clinica.imagenes;

/** RFRAD-004: el orden de declaración es el orden de atención. */
public enum PrioridadEstudio {
    URGENTE("Urgente"),
    HOSPITALIZADO("Hospitalizado"),
    AMBULATORIO("Ambulatorio");

    private final String descripcion;

    PrioridadEstudio(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
