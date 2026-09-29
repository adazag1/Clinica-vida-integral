package co.edu.ucentral.clinica.notificaciones;

import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.seguridad.Rol;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificacionService {

    private static final int MAX_LISTA = 30;

    private final NotificacionRepository repository;

    public NotificacionService(NotificacionRepository repository) {
        this.repository = repository;
    }

    /** Se guarda en la misma transacción de la operación: si esta falla, no queda un aviso falso. */
    @Transactional
    public void paraRol(Rol rol, String titulo, String mensaje, String enlace) {
        repository.save(new Notificacion(rol, null, titulo, mensaje, enlace));
    }

    @Transactional
    public void paraUsuario(String usuario, String titulo, String mensaje, String enlace) {
        repository.save(new Notificacion(null, usuario, titulo, mensaje, enlace));
    }

    public record NotificacionDto(Long id, String titulo, String mensaje, String enlace, Instant fecha, boolean leida) {
        static NotificacionDto de(Notificacion n) {
            return new NotificacionDto(n.getId(), n.getTitulo(), n.getMensaje(), n.getEnlace(), n.getFecha(),
                    n.getLeidaEn() != null);
        }
    }

    public record Bandeja(long noLeidas, List<NotificacionDto> notificaciones) {
    }

    @Transactional(readOnly = true)
    public Bandeja bandeja(String usuario, Rol rol) {
        List<NotificacionDto> lista = repository.dePara(usuario, rol, PageRequest.of(0, MAX_LISTA)).stream()
                .map(NotificacionDto::de).toList();
        return new Bandeja(repository.noLeidas(usuario, rol), lista);
    }

    @Transactional
    public void marcarLeida(Long id, String usuario, Rol rol) {
        Notificacion n = repository.findById(id)
                .filter(x -> x.esPara(usuario, rol))
                .orElseThrow(() -> new RecursoNoEncontradoException("Notificación", id));
        n.marcarLeida();
    }

    @Transactional
    public void marcarTodas(String usuario, Rol rol) {
        repository.pendientes(usuario, rol).forEach(Notificacion::marcarLeida);
    }
}
