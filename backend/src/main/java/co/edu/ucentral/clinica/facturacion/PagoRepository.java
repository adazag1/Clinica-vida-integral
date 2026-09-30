package co.edu.ucentral.clinica.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    List<Pago> findByFacturaIdOrderByFecha(Long facturaId);
}
