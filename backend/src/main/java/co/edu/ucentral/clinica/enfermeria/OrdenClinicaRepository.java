package co.edu.ucentral.clinica.enfermeria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface OrdenClinicaRepository extends JpaRepository<OrdenClinica, Long> {

    /** Órdenes vigentes o que terminaron hace poco (para mostrar lo vencido o suspendido del turno). */
    @Query("select o from OrdenClinica o where o.pacienteId = :pacienteId and o.fin > :desde order by o.tipo, o.inicio")
    List<OrdenClinica> recientesDePaciente(Long pacienteId, Instant desde);

    List<OrdenClinica> findByConsultaIdOrderByInicioDesc(Long consultaId);

    /** Pacientes con alguna orden activa y vigente: el censo de enfermería. */
    @Query("""
            select distinct o.pacienteId from OrdenClinica o
            where o.estado = co.edu.ucentral.clinica.enfermeria.OrdenClinica.Estado.ACTIVA and o.fin > :ahora
            """)
    List<Long> pacientesConOrdenesVigentes(Instant ahora);
}
