package co.edu.ucentral.clinica.imagenes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SegundaOpinionRepository extends JpaRepository<SegundaOpinion, Long> {

    List<SegundaOpinion> findByEstudioIdOrderBySolicitadaEn(Long estudioId);

    List<SegundaOpinion> findByAsignadaAIgnoreCaseAndRespondidaEnIsNull(String usuario);
}
