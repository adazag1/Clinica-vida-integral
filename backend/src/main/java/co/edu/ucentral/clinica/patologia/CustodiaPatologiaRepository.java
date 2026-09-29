package co.edu.ucentral.clinica.patologia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustodiaPatologiaRepository extends JpaRepository<CustodiaPatologia, Long> {

    List<CustodiaPatologia> findByCasoIdOrderByFechaAscIdAsc(Long casoId);
}
