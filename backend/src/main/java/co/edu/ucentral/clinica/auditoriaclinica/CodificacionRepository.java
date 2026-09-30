package co.edu.ucentral.clinica.auditoriaclinica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CodificacionRepository extends JpaRepository<Codificacion, Long> {

    Optional<Codificacion> findByCuentaId(Long cuentaId);
}
