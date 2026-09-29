package co.edu.ucentral.clinica.laboratorio;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ResultadoLabRepository extends JpaRepository<ResultadoLab, Long> {

    /** RFLC-004: último resultado validado del mismo analito para el paciente, sin contar esta orden. */
    @Query("""
            select r from ResultadoLab r join fetch r.orden o
            where r.analito.id = :analitoId and o.pacienteId = :pacienteId and o.id <> :ordenId
              and o.estado = co.edu.ucentral.clinica.laboratorio.EstadoOrden.VALIDADA
            order by o.validadaEn desc
            """)
    List<ResultadoLab> previos(Long analitoId, Long pacienteId, Long ordenId, Pageable limite);

    /** RFMT-002 / RFMT-005: resultados validados más recientes del paciente. */
    @Query("""
            select r from ResultadoLab r join fetch r.orden o
            where o.pacienteId = :pacienteId and o.estado = co.edu.ucentral.clinica.laboratorio.EstadoOrden.VALIDADA
            order by o.validadaEn desc, r.analito.orden
            """)
    List<ResultadoLab> validadosDePaciente(Long pacienteId, Pageable limite);
}
