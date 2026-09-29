package co.edu.ucentral.clinica.laboratorio;

/** Secciones del laboratorio. Cada una procesa en su equipo y lleva su propio control de calidad. */
public enum AreaLab {
    HEMATOLOGIA("Hematología"),
    QUIMICA("Química clínica"),
    INMUNOLOGIA("Inmunología");

    private final String descripcion;

    AreaLab(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
