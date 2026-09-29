package co.edu.ucentral.clinica.comun.seguridad;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.seguridad.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Confirma la identidad de quien firma un informe pidiéndole de nuevo la contraseña. Responde 422
 * (no 401): la sesión sigue siendo válida, solo falló la confirmación. Los errores cuentan como
 * intentos fallidos de la cuenta, así la firma no sirve para adivinar contraseñas.
 */
@Component
public class Reautenticador {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarios;
    private final int maxIntentos;

    public Reautenticador(AuthenticationManager authenticationManager, UsuarioRepository usuarios,
                          @Value("${app.seguridad.max-intentos-fallidos}") int maxIntentos) {
        this.authenticationManager = authenticationManager;
        this.usuarios = usuarios;
        this.maxIntentos = maxIntentos;
    }

    /** Quien llama debe declarar {@code noRollbackFor = ReglaNegocioException.class} para conservar el conteo. */
    public void confirmar(String usuario, String password) {
        confirmar(usuario, password, "Contraseña incorrecta. El informe no se firmó.");
    }

    public void confirmar(String usuario, String password, String mensajeError) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(usuario, password == null ? "" : password));
        } catch (BadCredentialsException e) {
            usuarios.findByUsernameIgnoreCase(usuario).ifPresent(u -> u.registrarIntentoFallido(maxIntentos));
            throw new ReglaNegocioException("FIRMA_INVALIDA", mensajeError);
        } catch (AuthenticationException e) {
            throw new ReglaNegocioException("FIRMA_INVALIDA", "No fue posible confirmar su identidad.");
        }
    }
}
