package co.edu.ucentral.clinica.documentos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DocumentoEventoRepository extends JpaRepository<DocumentoEvento, Long> {

    List<DocumentoEvento> findTop100ByDocumentoIdOrderByFechaDescIdDesc(Long documentoId);

    @Query("select e.accion, count(e) from DocumentoEvento e where e.fecha >= :desde and e.fecha < :hasta group by e.accion")
    List<Object[]> contarPorAccion(Instant desde, Instant hasta);

    @Query("""
            select e.usuario, count(e) from DocumentoEvento e
            where e.accion in :acciones and e.fecha >= :desde and e.fecha < :hasta
            group by e.usuario order by count(e) desc
            """)
    List<Object[]> accesosPorUsuario(Collection<String> acciones, Instant desde, Instant hasta);
}
