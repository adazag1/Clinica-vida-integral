package co.edu.ucentral.clinica.comun.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    Page<Auditoria> findByUsuarioOrderByFechaDesc(String usuario, Pageable pageable);

    Page<Auditoria> findAllByOrderByFechaDesc(Pageable pageable);
}
