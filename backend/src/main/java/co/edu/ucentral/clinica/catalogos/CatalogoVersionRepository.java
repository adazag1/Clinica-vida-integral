package co.edu.ucentral.clinica.catalogos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface CatalogoVersionRepository extends JpaRepository<CatalogoVersion, String> {

    /** Bloquea la fila: dos cambios simultáneos no pueden quedar con el mismo número de versión. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from CatalogoVersion v where v.catalogo = :catalogo")
    Optional<CatalogoVersion> paraActualizar(String catalogo);
}
