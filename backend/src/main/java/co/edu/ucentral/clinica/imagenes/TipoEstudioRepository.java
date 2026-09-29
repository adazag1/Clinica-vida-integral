package co.edu.ucentral.clinica.imagenes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoEstudioRepository extends JpaRepository<TipoEstudio, Long> {

    List<TipoEstudio> findAllByOrderByModalidadAscNombreAsc();
}
