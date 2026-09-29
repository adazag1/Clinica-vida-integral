package co.edu.ucentral.clinica.laboratorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface OrdenLabRepository extends JpaRepository<OrdenLab, Long> {

    List<OrdenLab> findByEstadoInOrderByOrdenadaEn(Collection<EstadoOrden> estados);

    /** Validadas recientes, para la cola del laboratorio cuando se filtra por ese estado. */
    List<OrdenLab> findByEstadoAndValidadaEnAfterOrderByValidadaEnDesc(EstadoOrden estado, Instant desde);

    List<OrdenLab> findByPacienteIdOrderByOrdenadaEnDesc(Long pacienteId);

    List<OrdenLab> findByConsultaIdOrderByOrdenadaEnDesc(Long consultaId);

    @Query(value = "select nextval('seq_numero_orden_lab')", nativeQuery = true)
    long siguienteNumero();
}
