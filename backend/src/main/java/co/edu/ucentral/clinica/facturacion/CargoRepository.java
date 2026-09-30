package co.edu.ucentral.clinica.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CargoRepository extends JpaRepository<Cargo, Long> {

    boolean existsByOrigenModuloAndReferenciaOrigenAndEstado(String modulo, String referencia, Cargo.Estado estado);
}
