package co.edu.ucentral.clinica.agenda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TipoCitaRepository extends JpaRepository<TipoCita, Long> {

    List<TipoCita> findAllByOrderByNombre();

    Optional<TipoCita> findByCodigo(String codigo);
}
