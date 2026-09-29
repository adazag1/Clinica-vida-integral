package co.edu.ucentral.clinica.documentos;

/** RFGAC-002: clasificación de los documentos clínicos. */
public enum TipoDocumento {
    HISTORIA_CLINICA("Nota de consulta / historia clínica", false),
    EPICRISIS("Epicrisis", false),
    RESULTADO_LABORATORIO("Resultado de laboratorio", false),
    INFORME_RADIOLOGIA("Informe de imágenes diagnósticas", false),
    INFORME_PATOLOGIA("Informe de patología", false),
    RESULTADO_EXTERNO("Resultado o documento externo", false),
    ORDEN_MEDICA("Orden médica", false),
    FORMULA("Fórmula médica", false),
    CONSENTIMIENTO("Consentimiento informado", true),
    IDENTIFICACION("Documento de identidad", true),
    AUTORIZACION("Autorización de la aseguradora", true),
    OTRO("Otro", false);

    private final String descripcion;
    /** Admisiones puede cargar y consultar los administrativos; el contenido clínico no. */
    private final boolean administrativo;

    TipoDocumento(String descripcion, boolean administrativo) {
        this.descripcion = descripcion;
        this.administrativo = administrativo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isAdministrativo() {
        return administrativo;
    }
}
