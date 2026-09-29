package co.edu.ucentral.clinica.laboratorio;

/** RFMT-006 / RFLC-002: estado visible para el médico y para el laboratorio. */
public enum EstadoOrden {
    ORDENADA("Pendiente de muestra"),
    MUESTRA_RECIBIDA("Muestra recibida"),
    EN_PROCESO("En proceso"),
    VALIDADA("Validada"),
    RECHAZADA("Muestra rechazada");

    private final String descripcion;

    EstadoOrden(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isAbierta() {
        return this != VALIDADA && this != RECHAZADA;
    }
}
