package co.edu.ucentral.clinica.patologia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InformePatologiaRepository extends JpaRepository<InformePatologia, Long> {

    List<InformePatologia> findByCasoIdOrderByVersionDesc(Long casoId);
}
