package co.edu.ucentral.clinica.agenda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoCitaRepository extends JpaRepository<EventoCita, Long> {

    List<EventoCita> findByCitaIdOrderByFechaAscIdAsc(Long citaId);
}
