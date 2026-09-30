package co.edu.ucentral.clinica.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface GlosaRepository extends JpaRepository<Glosa, Long> {

    List<Glosa> findByFacturaIdOrderByRecibidaEn(Long facturaId);

    List<Glosa> findByEstadoOrderByLimiteRespuesta(Glosa.Estado estado);

    List<Glosa> findByRecibidaEnBetween(LocalDate desde, LocalDate hasta);
}
