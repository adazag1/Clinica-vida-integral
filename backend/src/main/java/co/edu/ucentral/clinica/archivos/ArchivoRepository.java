package co.edu.ucentral.clinica.archivos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;

public interface ArchivoRepository extends JpaRepository<Archivo, Long> {

    @Modifying
    @Query(value = "update archivo set contenido = :contenido where id = :id", nativeQuery = true)
    void guardarContenido(Long id, byte[] contenido);

    @Query(value = "select contenido from archivo where id = :id", nativeQuery = true)
    byte[] contenido(Long id);

    /** Eliminación según normativa: se borra el contenido y se conservan nombre, fecha y huella como evidencia. */
    @Modifying
    @Query(value = "update archivo set contenido = null where id in (:ids)", nativeQuery = true)
    void purgar(Collection<Long> ids);
}
