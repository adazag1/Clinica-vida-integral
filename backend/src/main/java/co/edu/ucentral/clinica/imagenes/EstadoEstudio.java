package co.edu.ucentral.clinica.imagenes;

public enum EstadoEstudio {
    ORDENADO("Pendiente de adquisición"),
    ADQUIRIDO("Pendiente de lectura"),
    EN_LECTURA("En lectura"),
    INFORMADO("Informado"),
    BLOQUEADO("Bloqueado por identificación");

    private final String descripcion;

    EstadoEstudio(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
