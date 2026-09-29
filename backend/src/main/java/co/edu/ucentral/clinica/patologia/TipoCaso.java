package co.edu.ucentral.clinica.patologia;

public enum TipoCaso {
    BIOPSIA("Biopsia", "B"),
    CITOLOGIA("Citología", "C"),
    PIEZA_QUIRURGICA("Pieza quirúrgica", "Q");

    private final String descripcion;
    private final String prefijo;

    TipoCaso(String descripcion, String prefijo) {
        this.descripcion = descripcion;
        this.prefijo = prefijo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getPrefijo() {
        return prefijo;
    }
}
