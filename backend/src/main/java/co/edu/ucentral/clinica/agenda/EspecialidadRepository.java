package co.edu.ucentral.clinica.agenda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EspecialidadRepository extends JpaRepository<Especialidad, Long> {

    List<Especialidad> findAllByOrderByNombre();

    Optional<Especialidad> findByNombre(String nombre);
}
