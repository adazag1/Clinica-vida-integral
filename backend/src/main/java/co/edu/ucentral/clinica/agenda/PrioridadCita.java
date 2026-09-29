package co.edu.ucentral.clinica.agenda;

/** RFAGE-006: solo las prioritarias y urgentes pueden usar las franjas reservadas. */
public enum PrioridadCita {
    URGENTE("Urgente"),
    PRIORITARIA("Prioritaria"),
    NORMAL("Normal");

    private final String descripcion;

    PrioridadCita(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isPuedeUsarReservadas() {
        return this != NORMAL;
    }
}
