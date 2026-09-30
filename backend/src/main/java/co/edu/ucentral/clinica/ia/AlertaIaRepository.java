package co.edu.ucentral.clinica.ia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AlertaIaRepository extends JpaRepository<AlertaIa, Long> {

    List<AlertaIa> findByPacienteIdAndVigenteTrue(Long pacienteId);

    List<AlertaIa> findByPacienteIdOrderByGeneradaEnDesc(Long pacienteId);

    /** Precisión por regla: [regla, total, útiles, no útiles]. */
    @Query("""
            select a.regla, count(a),
                   sum(case when a.retroalimentacion = co.edu.ucentral.clinica.ia.AlertaIa.Retroalimentacion.UTIL then 1 else 0 end),
                   sum(case when a.retroalimentacion = co.edu.ucentral.clinica.ia.AlertaIa.Retroalimentacion.NO_UTIL then 1 else 0 end)
            from AlertaIa a group by a.regla
            """)
    List<Object[]> precisionPorRegla();

    List<AlertaIa> findTop20ByReglaAndRetroalimentacionOrderByRetroEnDesc(String regla, AlertaIa.Retroalimentacion r);
}
