package co.edu.ucentral.clinica.catalogos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogoCambioRepository extends JpaRepository<CatalogoCambio, Long> {

    List<CatalogoCambio> findTop100ByCatalogoOrderByFechaDescIdDesc(String catalogo);
}
