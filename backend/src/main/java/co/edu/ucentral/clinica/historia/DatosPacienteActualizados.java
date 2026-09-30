package co.edu.ucentral.clinica.historia;

/**
 * Llegó información clínica nueva del paciente que no genera un cargo (p. ej. una toma de signos).
 * El motor de IA la usa para reevaluar sus reglas.
 */
public record DatosPacienteActualizados(Long pacienteId, String origen) {
}
