package co.edu.ucentral.clinica.admisiones;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TriageRepository extends JpaRepository<Triage, Long> {

    Optional<Triage> findByEpisodioId(Long episodioId);

    List<Triage> findByEpisodioIdIn(Collection<Long> episodioIds);
}
