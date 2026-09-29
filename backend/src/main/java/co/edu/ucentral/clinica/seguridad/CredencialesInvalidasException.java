package co.edu.ucentral.clinica.seguridad;

/** Login rechazado; el manejador global la responde con 401. */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}
