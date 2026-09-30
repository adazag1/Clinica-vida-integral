package co.edu.ucentral.clinica.ia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface ConsultaIaRepository extends JpaRepository<ConsultaIa, Long> {

    long countByFechaAfter(Instant desde);

    @Query("select coalesce(sum(c.costoUsd), 0) from ConsultaIa c where c.fecha > :desde")
    java.math.BigDecimal costoDesde(Instant desde);
}
