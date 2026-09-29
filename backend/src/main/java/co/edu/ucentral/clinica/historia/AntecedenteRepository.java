package co.edu.ucentral.clinica.historia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AntecedenteRepository extends JpaRepository<Antecedente, Long> {

    List<Antecedente> findByPacienteIdOrderByTipoAscCreadoEnDesc(Long pacienteId);

    List<Antecedente> findByPacienteIdAndActivoTrue(Long pacienteId);

    List<Antecedente> findByPacienteIdAndTipoAndActivoTrue(Long pacienteId, Antecedente.Tipo tipo);
}
