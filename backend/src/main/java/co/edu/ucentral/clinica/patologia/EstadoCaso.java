package co.edu.ucentral.clinica.patologia;

public enum EstadoCaso {
    SOLICITADO("Pendiente de recepción"),
    RECIBIDO("Recibido: pendiente de macroscopía"),
    MACROSCOPIA("En microscopía"),
    INFORMADO("Informado");

    private final String descripcion;

    EstadoCaso(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
