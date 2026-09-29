package co.edu.ucentral.clinica.enfermeria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntregaTurnoRepository extends JpaRepository<EntregaTurno, Long> {

    List<EntregaTurno> findTop10ByPacienteIdOrderByFechaDesc(Long pacienteId);
}
