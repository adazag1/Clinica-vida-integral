package co.edu.ucentral.clinica.seguridad;

import co.edu.ucentral.clinica.comun.auditoria.AuditoriaService;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.seguridad.dto.LoginRequest;
import co.edu.ucentral.clinica.seguridad.dto.LoginResponse;
import co.edu.ucentral.clinica.seguridad.dto.UsuarioActualDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarios;
    private final JwtService jwtService;
    private final AuditoriaService auditoria;
    private final int maxIntentos;

    public AuthService(AuthenticationManager authenticationManager, UsuarioRepository usuarios,
                       JwtService jwtService, AuditoriaService auditoria,
                       @Value("${app.seguridad.max-intentos-fallidos}") int maxIntentos) {
        this.authenticationManager = authenticationManager;
        this.usuarios = usuarios;
        this.jwtService = jwtService;
        this.auditoria = auditoria;
        this.maxIntentos = maxIntentos;
    }

    /** RFxx-001 Inicio de Sesión: valida credenciales y emite un token con el rol del usuario. */
    @Transactional(noRollbackFor = CredencialesInvalidasException.class) // conservar el conteo de intentos
    public LoginResponse login(LoginRequest request, String ip) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.usuario().trim(), request.password()));
        } catch (LockedException e) {
            auditoria.registrarEvento(request.usuario(), "LOGIN_BLOQUEADO", "Cuenta bloqueada", ip);
            throw new CredencialesInvalidasException(
                    "Su cuenta está bloqueada por intentos fallidos. Contacte al administrador.");
        } catch (DisabledException e) {
            auditoria.registrarEvento(request.usuario(), "LOGIN_FALLIDO", "Cuenta inactiva", ip);
            throw new CredencialesInvalidasException("Su cuenta está inactiva.");
        } catch (BadCredentialsException e) {
            usuarios.buscarPorLogin(request.usuario().trim())
                    .ifPresent(u -> u.registrarIntentoFallido(maxIntentos));
            auditoria.registrarEvento(request.usuario(), "LOGIN_FALLIDO", "Credenciales inválidas", ip);
            // Mensaje genérico: no revelar si el usuario existe.
            throw new CredencialesInvalidasException("Usuario o contraseña incorrectos.");
        }

        Usuario usuario = usuarios.buscarPorLogin(request.usuario().trim()).orElseThrow();
        usuario.registrarAccesoExitoso();
        auditoria.registrarEvento(usuario.getUsername(), "LOGIN_EXITOSO", null, ip);
        JwtService.TokenEmitido token = jwtService.emitir(usuario);
        return new LoginResponse(token.valor(), token.expiraEn(), UsuarioActualDto.de(usuario));
    }

    @Transactional(readOnly = true)
    public UsuarioActualDto usuarioActual(String username) {
        return usuarios.findByUsernameIgnoreCase(username)
                .map(UsuarioActualDto::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", username));
    }
}
