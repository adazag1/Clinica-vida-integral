package co.edu.ucentral.clinica.documentos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentoRepository extends JpaRepository<Documento, Long>, JpaSpecificationExecutor<Documento> {

    Optional<Documento> findByModuloOrigenAndReferenciaOrigen(String modulo, String referencia);

    List<Documento> findByPacienteIdOrderByFechaDocumentoDescIdDesc(Long pacienteId);

    @Query("select d.tipo, count(d) from Documento d group by d.tipo")
    List<Object[]> contarPorTipo();

    @Query("select d.estado, count(d) from Documento d group by d.estado")
    List<Object[]> contarPorEstado();

    @Query("select d.origen, count(d) from Documento d group by d.origen")
    List<Object[]> contarPorOrigen();
}
