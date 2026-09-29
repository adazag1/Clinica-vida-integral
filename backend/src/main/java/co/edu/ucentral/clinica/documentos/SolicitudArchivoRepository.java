package co.edu.ucentral.clinica.documentos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SolicitudArchivoRepository extends JpaRepository<SolicitudArchivo, Long> {

    List<SolicitudArchivo> findByEstadoInOrderByUrgenteDescSolicitadaEnAsc(Collection<SolicitudArchivo.Estado> estados);

    List<SolicitudArchivo> findTop50BySolicitanteIgnoreCaseOrderBySolicitadaEnDesc(String solicitante);

    List<SolicitudArchivo> findBySolicitadaEnGreaterThanEqualAndSolicitadaEnLessThan(Instant desde, Instant hasta);
}
