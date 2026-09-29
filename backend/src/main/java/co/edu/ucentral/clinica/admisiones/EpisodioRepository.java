package co.edu.ucentral.clinica.admisiones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface EpisodioRepository extends JpaRepository<Episodio, Long> {

    boolean existsByPacienteIdAndEstadoIn(Long pacienteId, Collection<EstadoEpisodio> estados);

    List<Episodio> findByPacienteIdOrderByFechaIngresoDesc(Long pacienteId);

    /** RFADM-013: episodios activos con su paciente en una sola consulta. */
    @Query("select e from Episodio e join fetch e.paciente where e.estado in :estados order by e.fechaIngreso")
    List<Episodio> buscarPorEstados(Collection<EstadoEpisodio> estados);

    @Query("select e from Episodio e where e.paciente.id = :pacienteId and e.estado in :estados")
    List<Episodio> abiertosDePaciente(Long pacienteId, Collection<EstadoEpisodio> estados);

    @Query(value = "select nextval('seq_numero_episodio')", nativeQuery = true)
    long siguienteNumero();
}
