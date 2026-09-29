package co.edu.ucentral.clinica.historia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegistroSignosRepository extends JpaRepository<RegistroSignos, Long> {

    Optional<RegistroSignos> findFirstByPacienteIdOrderByFechaDesc(Long pacienteId);

    List<RegistroSignos> findTop20ByPacienteIdOrderByFechaDesc(Long pacienteId);

    List<RegistroSignos> findByConsultaIdOrderByFechaDesc(Long consultaId);
}
