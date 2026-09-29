package co.edu.ucentral.clinica.admisiones;

/**
 * RFADM-008 / RFADM-009. La verificación la hace hoy admisiones con la aseguradora y deja aquí el
 * resultado; la integración automática con el módulo administrativo llega con facturación (Fase 8).
 */
public enum EstadoCobertura {
    PENDIENTE("Pendiente de verificar", false),
    VERIFICADA("Cobertura verificada", true),
    NO_CUBIERTA("Sin cobertura", false),
    PARTICULAR("Pago particular", true),
    /** Urgencia que no puede esperar la validación administrativa. */
    EXCEPCION_URGENCIA("Atención sin autorización (urgencia)", true);

    private final String descripcion;
    private final boolean permiteAtencion;

    EstadoCobertura(String descripcion, boolean permiteAtencion) {
        this.descripcion = descripcion;
        this.permiteAtencion = permiteAtencion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isPermiteAtencion() {
        return permiteAtencion;
    }
}
