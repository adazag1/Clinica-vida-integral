package co.edu.ucentral.clinica.notificaciones;

import co.edu.ucentral.clinica.seguridad.Rol;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    @Query("""
            select n from Notificacion n
            where lower(n.usuarioDestino) = lower(:usuario) or n.rolDestino = :rol
            order by n.fecha desc
            """)
    List<Notificacion> dePara(String usuario, Rol rol, Pageable limite);

    @Query("""
            select count(n) from Notificacion n
            where (lower(n.usuarioDestino) = lower(:usuario) or n.rolDestino = :rol) and n.leidaEn is null
            """)
    long noLeidas(String usuario, Rol rol);

    @Query("""
            select n from Notificacion n
            where (lower(n.usuarioDestino) = lower(:usuario) or n.rolDestino = :rol) and n.leidaEn is null
            """)
    List<Notificacion> pendientes(String usuario, Rol rol);
}
