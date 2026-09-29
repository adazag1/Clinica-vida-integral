package co.edu.ucentral.clinica.agenda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SedeRepository extends JpaRepository<Sede, Long> {

    List<Sede> findAllByOrderByNombre();

    Optional<Sede> findByNombre(String nombre);
}
