package co.edu.ucentral.clinica.laboratorio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReprocesoLabRepository extends JpaRepository<ReprocesoLab, Long> {

    List<ReprocesoLab> findByResultadoIdInOrderByFecha(Collection<Long> resultadoIds);
}
