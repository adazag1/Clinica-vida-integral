package co.edu.ucentral.clinica.patologia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoCasoPatologiaRepository extends JpaRepository<EventoCasoPatologia, Long> {

    List<EventoCasoPatologia> findByCasoIdOrderByFechaAscIdAsc(Long casoId);
}
