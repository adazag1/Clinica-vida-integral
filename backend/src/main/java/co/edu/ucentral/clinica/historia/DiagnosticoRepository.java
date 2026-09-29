package co.edu.ucentral.clinica.historia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiagnosticoRepository extends JpaRepository<Diagnostico, Long> {

    List<Diagnostico> findAllByOrderByCodigo();
}
