package co.edu.ucentral.clinica.historia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Long> {

    List<Medicamento> findAllByOrderByNombre();
}
