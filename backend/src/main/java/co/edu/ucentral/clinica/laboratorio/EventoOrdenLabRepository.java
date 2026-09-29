package co.edu.ucentral.clinica.laboratorio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoOrdenLabRepository extends JpaRepository<EventoOrdenLab, Long> {

    List<EventoOrdenLab> findByOrdenIdOrderByFechaAscIdAsc(Long ordenId);
}
