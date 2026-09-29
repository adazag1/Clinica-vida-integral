package co.edu.ucentral.clinica.pacientes;

/** Tipos de documento de identidad usados en Colombia (RIPS), más el identificador temporal. */
public enum TipoDocumento {
    CC("Cédula de ciudadanía", true),
    TI("Tarjeta de identidad", true),
    RC("Registro civil", true),
    CE("Cédula de extranjería", false),
    PA("Pasaporte", false),
    PPT("Permiso por protección temporal", false),
    TEMPORAL("Sin documento (identificador temporal)", false);

    private final String descripcion;
    private final boolean soloNumeros;

    TipoDocumento(String descripcion, boolean soloNumeros) {
        this.descripcion = descripcion;
        this.soloNumeros = soloNumeros;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** CC, TI y RC solo llevan dígitos; los documentos de extranjeros pueden tener letras. */
    public boolean isSoloNumeros() {
        return soloNumeros;
    }
}
