package co.edu.ucentral.clinica.admisiones;

public enum EstadoEpisodio {
    /** Recién admitido: faltan triage, cobertura o consentimiento. */
    EN_ADMISION("En admisión"),
    /** Con todos los requisitos: se avisa a los médicos y aparece en su lista (RFADM-012 / RFADM-013). */
    EN_ESPERA_CONSULTA("En espera de consulta"),
    /** El médico abrió la consulta (Fase 4). */
    EN_ATENCION("En atención"),
    CERRADO("Cerrado"),
    ANULADO("Anulado");

    private final String descripcion;

    EstadoEpisodio(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isAbierto() {
        return this == EN_ADMISION || this == EN_ESPERA_CONSULTA || this == EN_ATENCION;
    }
}
