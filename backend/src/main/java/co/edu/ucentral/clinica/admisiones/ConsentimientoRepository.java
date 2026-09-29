package co.edu.ucentral.clinica.admisiones;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ConsentimientoRepository extends JpaRepository<Consentimiento, Long> {

    List<Consentimiento> findByEpisodioIdOrderByCreadoEn(Long episodioId);

    List<Consentimiento> findByEpisodioIdIn(Collection<Long> episodioIds);

    boolean existsByEpisodioIdAndTipo(Long episodioId, TipoConsentimiento tipo);
}
