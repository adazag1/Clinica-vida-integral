package co.edu.ucentral.clinica.notificaciones;

import co.edu.ucentral.clinica.seguridad.Rol;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notificaciones")
@Tag(name = "Notificaciones", description = "Avisos internos de la campana")
public class NotificacionController {

    private final NotificacionService service;

    public NotificacionController(NotificacionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Avisos del usuario y de su rol, del más reciente al más antiguo")
    public NotificacionService.Bandeja bandeja(Authentication auth) {
        return service.bandeja(auth.getName(), rol(auth));
    }

    @PostMapping("/{id}/leida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leida(@PathVariable Long id, Authentication auth) {
        service.marcarLeida(id, auth.getName(), rol(auth));
    }

    @PostMapping("/leidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void todas(Authentication auth) {
        service.marcarTodas(auth.getName(), rol(auth));
    }

    /** El token trae un solo rol, como autoridad ROLE_xxx. */
    private static Rol rol(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> Rol.valueOf(a.substring(5)))
                .findFirst()
                .orElse(null);
    }
}
