package co.edu.ucentral.clinica.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface BloqueoRepository extends JpaRepository<BloqueoAgenda, Long> {

    @Query("""
            select b from BloqueoAgenda b
            where b.profesionalId in :profesionales and b.inicio < :hasta and b.fin > :desde
            order by b.inicio
            """)
    List<BloqueoAgenda> enRango(Collection<Long> profesionales, Instant desde, Instant hasta);

    @Query("select b from BloqueoAgenda b where b.fin > :desde order by b.inicio")
    List<BloqueoAgenda> vigentes(Instant desde);
}
