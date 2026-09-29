package co.edu.ucentral.clinica.pacientes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CambioDemograficoRepository extends JpaRepository<CambioDemografico, Long> {

    List<CambioDemografico> findByPacienteIdOrderByFechaDescIdDesc(Long pacienteId);
}
