package co.edu.ucentral.clinica.documentos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoVersionRepository extends JpaRepository<DocumentoVersion, Long> {

    List<DocumentoVersion> findByDocumentoIdOrderByVersionDesc(Long documentoId);

    Optional<DocumentoVersion> findByDocumentoIdAndVersion(Long documentoId, int version);
}
