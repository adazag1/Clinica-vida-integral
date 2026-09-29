package co.edu.ucentral.clinica.agenda;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProfesionalRepository extends JpaRepository<Profesional, Long> {

    List<Profesional> findByActivoTrueOrderByNombre();

    Optional<Profesional> findByUsuarioId(Long usuarioId);

    /**
     * RNFAGE-001: bloquea la fila del profesional (SELECT ... FOR UPDATE) hasta el fin de la
     * transacción. Dos personas que agendan al mismo médico a la vez quedan en fila: la segunda
     * ve la cita de la primera y no puede sobre-reservar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profesional p where p.id = :id")
    Optional<Profesional> bloquearParaAgendar(Long id);
}
