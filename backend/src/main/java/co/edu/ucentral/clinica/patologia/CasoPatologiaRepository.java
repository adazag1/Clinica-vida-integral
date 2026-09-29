package co.edu.ucentral.clinica.patologia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface CasoPatologiaRepository extends JpaRepository<CasoPatologia, Long> {

    List<CasoPatologia> findByEstadoInOrderBySolicitadoEn(Collection<EstadoCaso> estados);

    List<CasoPatologia> findByEstadoAndValidadoEnAfterOrderByValidadoEnDesc(EstadoCaso estado, Instant desde);

    List<CasoPatologia> findByPacienteIdOrderBySolicitadoEnDesc(Long pacienteId);

    List<CasoPatologia> findByConsultaIdOrderBySolicitadoEnDesc(Long consultaId);

    @Query(value = "select nextval('seq_numero_caso_patologia')", nativeQuery = true)
    long siguienteNumero();
}
