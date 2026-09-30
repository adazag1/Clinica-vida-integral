package co.edu.ucentral.clinica.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FacturaRepository extends JpaRepository<Factura, Long> {

    Optional<Factura> findFirstByCuentaIdOrderByFechaEmisionDesc(Long cuentaId);

    List<Factura> findBySaldoGreaterThanOrderByFechaVencimiento(BigDecimal saldo);

    @Query(value = "select nextval('seq_numero_factura')", nativeQuery = true)
    long siguienteNumero();
}
