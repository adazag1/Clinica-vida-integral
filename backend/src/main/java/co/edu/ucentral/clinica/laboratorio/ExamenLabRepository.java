package co.edu.ucentral.clinica.laboratorio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamenLabRepository extends JpaRepository<ExamenLab, Long> {

    List<ExamenLab> findAllByOrderByNombre();
}
