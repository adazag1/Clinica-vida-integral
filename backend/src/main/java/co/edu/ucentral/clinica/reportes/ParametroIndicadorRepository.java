package co.edu.ucentral.clinica.reportes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParametroIndicadorRepository extends JpaRepository<ParametroIndicador, String> {

    List<ParametroIndicador> findAllByOrderByOrden();
}
