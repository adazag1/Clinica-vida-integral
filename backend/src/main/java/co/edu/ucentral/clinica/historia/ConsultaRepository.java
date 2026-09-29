package co.edu.ucentral.clinica.historia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    List<Consulta> findByPacienteIdOrderByInicioDesc(Long pacienteId);

    List<Consulta> findByMedicoIgnoreCaseAndEstado(String medico, Consulta.Estado estado);

    Optional<Consulta> findFirstByEpisodioIdAndEstado(Long episodioId, Consulta.Estado estado);

    Optional<Consulta> findFirstByCitaIdAndEstado(Long citaId, Consulta.Estado estado);

    /** Consultas firmadas desde cierta fecha: su fórmula puede seguir vigente. */
    @Query("""
            select c from Consulta c
            where c.pacienteId = :pacienteId and c.estado = co.edu.ucentral.clinica.historia.Consulta.Estado.FIRMADA
              and c.firmadaEn >= :desde
            order by c.firmadaEn desc
            """)
    List<Consulta> firmadasDesde(Long pacienteId, Instant desde);
}
