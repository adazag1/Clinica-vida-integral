package co.edu.ucentral.clinica.auditoriaclinica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {

    List<Devolucion> findByCuentaIdOrderByDevueltaEnDesc(Long cuentaId);

    List<Devolucion> findByCuentaIdAndCorregidaEnIsNull(Long cuentaId);

    List<Devolucion> findByDevueltaEnGreaterThanEqualAndDevueltaEnLessThan(Instant desde, Instant hasta);
}
