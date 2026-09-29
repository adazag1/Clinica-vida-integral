package co.edu.ucentral.clinica.laboratorio;

/** Motivos estructurados del laboratorio. */
public final class MotivoLab {

    private MotivoLab() {
    }

    /** RFLC-011: por qué se rechaza una muestra (la mayoría de errores ocurren en la pre-analítica). */
    public enum Rechazo {
        HEMOLIZADA("Muestra hemolizada"),
        COAGULADA("Muestra coagulada"),
        INSUFICIENTE("Volumen insuficiente"),
        MAL_IDENTIFICADA("Identificación incorrecta"),
        RECIPIENTE_INCORRECTO("Tubo o recipiente incorrecto"),
        TIEMPO_EXCEDIDO("Tiempo de transporte excedido"),
        OTRO("Otro");

        private final String descripcion;

        Rechazo(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    /** RFLC-009: por qué se repite una prueba. */
    public enum Reproceso {
        CONFIRMAR_CRITICO("Confirmación de valor crítico"),
        DELTA_CHECK("Diferencia con resultado previo"),
        INCOHERENCIA("Incoherencia entre resultados"),
        FALLA_EQUIPO("Falla del equipo"),
        CONTROL_FUERA_DE_RANGO("Control de calidad fuera de rango"),
        OTRO("Otro");

        private final String descripcion;

        Reproceso(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }
}
