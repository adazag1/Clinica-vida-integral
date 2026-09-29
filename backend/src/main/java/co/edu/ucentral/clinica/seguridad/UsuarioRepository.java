package co.edu.ucentral.clinica.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<Usuario> findByRolAndActivoTrueOrderByNombreCompleto(Rol rol);

    /** Permite iniciar sesión con usuario o con correo (mockup de Login). */
    @Query("select u from Usuario u where lower(u.username) = lower(:login) or lower(u.email) = lower(:login)")
    Optional<Usuario> buscarPorLogin(String login);
}
