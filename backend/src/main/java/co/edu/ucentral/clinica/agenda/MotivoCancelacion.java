package co.edu.ucentral.clinica.agenda;

/** RFAGE-008: motivo estructurado, para poder medir por qué se pierden citas. */
public enum MotivoCancelacion {
    SOLICITUD_PACIENTE("Solicitud del paciente"),
    ENFERMEDAD_PACIENTE("Enfermedad o imprevisto del paciente"),
    AUSENCIA_PROFESIONAL("Ausencia del profesional"),
    AUTORIZACION_PENDIENTE("Autorización o cobertura pendiente"),
    ERROR_AGENDAMIENTO("Error al agendar"),
    OTRO("Otro");

    private final String descripcion;

    MotivoCancelacion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
