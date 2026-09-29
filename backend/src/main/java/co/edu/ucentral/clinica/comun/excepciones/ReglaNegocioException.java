package co.edu.ucentral.clinica.comun.excepciones;

/**
 * Violación de una regla clínica o administrativa (p. ej. prescribir un medicamento
 * al que el paciente es alérgico). Se responde con 422 y un código que el frontend
 * puede usar para mostrar la alerta bloqueante adecuada.
 */
public class ReglaNegocioException extends RuntimeException {

    private final String codigo;

    public ReglaNegocioException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
