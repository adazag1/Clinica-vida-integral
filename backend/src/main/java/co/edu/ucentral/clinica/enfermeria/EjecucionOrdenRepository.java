package co.edu.ucentral.clinica.enfermeria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface EjecucionOrdenRepository extends JpaRepository<EjecucionOrden, Long> {

    List<EjecucionOrden> findByOrdenIdInOrderByRealizadaEnDesc(Collection<Long> ordenIds);

    boolean existsByOrdenIdAndProgramadaPara(Long ordenId, Instant programadaPara);

    List<EjecucionOrden> findByOrdenIdInAndRealizadaEnAfterOrderByRealizadaEn(Collection<Long> ordenIds, Instant desde);
}
