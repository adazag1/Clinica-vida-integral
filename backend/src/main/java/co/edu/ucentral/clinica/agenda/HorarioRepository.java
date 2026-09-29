package co.edu.ucentral.clinica.agenda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HorarioRepository extends JpaRepository<HorarioProfesional, Long> {

    List<HorarioProfesional> findByProfesionalIdOrderByDiaSemanaAscHoraInicioAsc(Long profesionalId);

    List<HorarioProfesional> findByProfesionalIdIn(Collection<Long> profesionales);

    boolean existsByProfesionalId(Long profesionalId);
}
