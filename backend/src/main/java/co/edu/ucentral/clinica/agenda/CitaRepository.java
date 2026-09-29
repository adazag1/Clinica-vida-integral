package co.edu.ucentral.clinica.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CitaRepository extends JpaRepository<Cita, Long> {

    /** Citas que se cruzan con [desde, hasta) y siguen ocupando el horario del profesional. */
    @Query("""
            select c from Cita c
            where c.profesional.id = :profesionalId and c.estado in :estados
              and c.inicio < :hasta and c.fin > :desde
            """)
    List<Cita> ocupadasDelProfesional(Long profesionalId, Instant desde, Instant hasta, Collection<EstadoCita> estados);

    @Query("""
            select c from Cita c
            where c.paciente.id = :pacienteId and c.estado in :estados
              and c.inicio < :hasta and c.fin > :desde
            """)
    List<Cita> ocupadasDelPaciente(Long pacienteId, Instant desde, Instant hasta, Collection<EstadoCita> estados);

    /** Agenda del día: todas las citas (también canceladas) de varios profesionales. */
    @Query("""
            select c from Cita c join fetch c.paciente join fetch c.tipo join fetch c.profesional
            where c.profesional.id in :profesionales and c.inicio < :hasta and c.fin > :desde
            order by c.inicio
            """)
    List<Cita> agenda(Collection<Long> profesionales, Instant desde, Instant hasta);

    @Query("""
            select c from Cita c join fetch c.tipo join fetch c.profesional
            where c.paciente.id = :pacienteId order by c.inicio desc
            """)
    List<Cita> dePaciente(Long pacienteId);

    /** RFAGE-009: citas próximas sin recordatorio. */
    @Query("""
            select c from Cita c join fetch c.paciente join fetch c.profesional join fetch c.tipo
            where c.estado in :estados and c.recordatorioEnviadoEn is null
              and c.inicio > :desde and c.inicio <= :hasta
            """)
    List<Cita> sinRecordatorio(Instant desde, Instant hasta, Collection<EstadoCita> estados);

    Optional<Cita> findByTokenPublico(String token);
}
