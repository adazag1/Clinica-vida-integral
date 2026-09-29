package co.edu.ucentral.clinica.agenda;

public enum EstadoCita {
    PROGRAMADA("Programada", true),
    CONFIRMADA("Confirmada", true),
    ATENDIDA("Atendida", true),
    NO_ASISTIO("No asistió", true),
    CANCELADA("Cancelada", false),
    /** Se movió a otro horario: la cita nueva queda enlazada a esta. */
    REPROGRAMADA("Reprogramada", false);

    private final String descripcion;
    private final boolean ocupaCupo;

    EstadoCita(String descripcion, boolean ocupaCupo) {
        this.descripcion = descripcion;
        this.ocupaCupo = ocupaCupo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Si la cita sigue ocupando su horario en la agenda del profesional. */
    public boolean isOcupaCupo() {
        return ocupaCupo;
    }

    public boolean isPendiente() {
        return this == PROGRAMADA || this == CONFIRMADA;
    }
}
