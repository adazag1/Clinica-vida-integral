package co.edu.ucentral.clinica.imagenes;

public enum Modalidad {
    RX("Radiografía"),
    TC("Tomografía"),
    RM("Resonancia magnética"),
    ECO("Ecografía"),
    MG("Mamografía");

    private final String descripcion;

    Modalidad(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
