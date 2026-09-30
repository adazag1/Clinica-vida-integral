package co.edu.ucentral.clinica.facturacion;

public enum TipoServicio {
    CONSULTA("Consulta"),
    LABORATORIO("Laboratorio clínico"),
    IMAGEN("Imágenes diagnósticas"),
    PATOLOGIA("Patología"),
    MEDICAMENTO("Medicamento"),
    PROCEDIMIENTO("Procedimiento"),
    INSUMO("Insumo"),
    ESTANCIA("Estancia");

    private final String descripcion;

    TipoServicio(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
