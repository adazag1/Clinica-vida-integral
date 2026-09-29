package co.edu.ucentral.clinica.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ListaEsperaRepository extends JpaRepository<EntradaListaEspera, Long> {

    @Query("""
            select e from EntradaListaEspera e join fetch e.paciente
            where e.estado = co.edu.ucentral.clinica.agenda.EntradaListaEspera.Estado.ACTIVA
            order by e.creadoEn
            """)
    List<EntradaListaEspera> activas();

    boolean existsByPacienteIdAndEspecialidadIdAndEstado(Long pacienteId, Long especialidadId,
                                                         EntradaListaEspera.Estado estado);
}
