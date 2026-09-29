package co.edu.ucentral.clinica.admisiones;

/** RFADM-007. */
public enum TipoConsentimiento {
    CONSENTIMIENTO_INFORMADO("Consentimiento informado de atención"),
    AUTORIZACION_TRATAMIENTO("Autorización de tratamiento"),
    TRATAMIENTO_DATOS("Autorización de tratamiento de datos personales (Ley 1581 de 2012)");

    private final String descripcion;

    TipoConsentimiento(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
