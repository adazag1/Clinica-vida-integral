package co.edu.ucentral.clinica.laboratorio;

/** RFLC-005: niveles de prioridad del laboratorio, en el orden en que se procesan. */
public enum PrioridadLab {
    URGENTE("Urgente"),
    HOSPITALIZADO("Hospitalizado"),
    AMBULATORIO("Ambulatorio");

    private final String descripcion;

    PrioridadLab(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
