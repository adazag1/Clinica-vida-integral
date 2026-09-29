package co.edu.ucentral.clinica.enfermeria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EventoAdversoRepository extends JpaRepository<EventoAdverso, Long> {

    List<EventoAdverso> findByPacienteIdOrderByOcurridoEnDesc(Long pacienteId);

    List<EventoAdverso> findByOcurridoEnAfterOrderByOcurridoEnDesc(Instant desde);
}
