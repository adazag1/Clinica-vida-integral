package co.edu.ucentral.clinica.laboratorio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ControlCalidadRepository extends JpaRepository<ControlCalidad, Long> {

    List<ControlCalidad> findByFechaOrderByRegistradoEnDesc(LocalDate fecha);

    /** El último control del día manda: si después de uno aceptado se registra uno rechazado, se bloquea. */
    Optional<ControlCalidad> findFirstByFechaAndAreaOrderByRegistradoEnDesc(LocalDate fecha, AreaLab area);
}
