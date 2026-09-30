package co.edu.ucentral.clinica.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServicioFacturableRepository extends JpaRepository<ServicioFacturable, Long> {

    Optional<ServicioFacturable> findByClaveOrigen(String claveOrigen);

    Optional<ServicioFacturable> findByCodigo(String codigo);

    List<ServicioFacturable> findAllByOrderByTipoAscNombreAsc();
}
