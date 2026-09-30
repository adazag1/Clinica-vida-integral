package co.edu.ucentral.clinica.reportes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CostoOperativoRepository extends JpaRepository<CostoOperativo, Long> {

    List<CostoOperativo> findByMesBetweenOrderByMesAscCategoriaAsc(LocalDate desde, LocalDate hasta);

    Optional<CostoOperativo> findByMesAndCategoria(LocalDate mes, CostoOperativo.Categoria categoria);
}
