package co.edu.ucentral.clinica.patologia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantillaPatologiaRepository extends JpaRepository<PlantillaPatologia, Long> {

    List<PlantillaPatologia> findAllByOrderByTipoAscServicioAscNombreAsc();

    Optional<PlantillaPatologia> findFirstByTipoAndServicioIsNull(TipoCaso tipo);
}
