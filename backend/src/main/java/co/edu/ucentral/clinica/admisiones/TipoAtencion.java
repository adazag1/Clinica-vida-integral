package co.edu.ucentral.clinica.admisiones;

/** RFADM-004: cada tipo activa un flujo distinto (solo urgencias pasa por triage). */
public enum TipoAtencion {
    URGENCIAS("Urgencias"),
    CONSULTA_EXTERNA("Consulta externa"),
    HOSPITALIZACION("Hospitalización");

    private final String descripcion;

    TipoAtencion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
