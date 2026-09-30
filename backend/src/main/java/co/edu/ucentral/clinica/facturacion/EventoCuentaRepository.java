package co.edu.ucentral.clinica.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoCuentaRepository extends JpaRepository<EventoCuenta, Long> {

    List<EventoCuenta> findByCuentaIdOrderByFechaAscIdAsc(Long cuentaId);
}
