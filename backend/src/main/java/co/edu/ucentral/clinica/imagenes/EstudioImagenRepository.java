package co.edu.ucentral.clinica.imagenes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface EstudioImagenRepository extends JpaRepository<EstudioImagen, Long> {

    List<EstudioImagen> findByEstadoInOrderByOrdenadoEn(Collection<EstadoEstudio> estados);

    List<EstudioImagen> findByEstadoAndInformadoEnAfterOrderByInformadoEnDesc(EstadoEstudio estado, Instant desde);

    List<EstudioImagen> findByPacienteIdOrderByOrdenadoEnDesc(Long pacienteId);

    List<EstudioImagen> findByConsultaIdOrderByOrdenadoEnDesc(Long consultaId);

    /** RFRAD-012: estudios informados en un periodo, para las métricas de tiempo de respuesta. */
    List<EstudioImagen> findByInformadoEnGreaterThanEqualAndInformadoEnLessThan(Instant desde, Instant hasta);

    @Query(value = "select nextval('seq_numero_estudio')", nativeQuery = true)
    long siguienteNumero();
}
