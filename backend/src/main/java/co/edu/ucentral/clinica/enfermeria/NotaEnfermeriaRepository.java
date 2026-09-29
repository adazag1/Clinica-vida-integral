package co.edu.ucentral.clinica.enfermeria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface NotaEnfermeriaRepository extends JpaRepository<NotaEnfermeria, Long> {

    List<NotaEnfermeria> findTop30ByPacienteIdOrderByFechaDesc(Long pacienteId);

    List<NotaEnfermeria> findByPacienteIdAndFechaAfterOrderByFecha(Long pacienteId, Instant desde);
}
