package co.edu.ucentral.clinica.auditoriaclinica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodificacionCambioRepository extends JpaRepository<CodificacionCambio, Long> {

    List<CodificacionCambio> findByCuentaIdOrderByFechaDescIdDesc(Long cuentaId);
}
