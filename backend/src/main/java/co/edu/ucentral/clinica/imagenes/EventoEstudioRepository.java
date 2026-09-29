package co.edu.ucentral.clinica.imagenes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoEstudioRepository extends JpaRepository<EventoEstudio, Long> {

    List<EventoEstudio> findByEstudioIdOrderByFechaAscIdAsc(Long estudioId);
}
