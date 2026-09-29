package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.agenda.dto.ProfesionalDto;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.ListaEsperaRequest;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** RFAGE-007: la reasignación automática de cupos está en {@link CitaService#reasignarDesdeListaEspera}. */
@Service
public class ListaEsperaService {

    private final ListaEsperaRepository repository;
    private final EspecialidadRepository especialidades;
    private final ProfesionalRepository profesionales;
    private final TipoCitaRepository tipos;
    private final PacienteService pacienteService;

    public ListaEsperaService(ListaEsperaRepository repository, EspecialidadRepository especialidades,
                              ProfesionalRepository profesionales, TipoCitaRepository tipos,
                              PacienteService pacienteService) {
        this.repository = repository;
        this.especialidades = especialidades;
        this.profesionales = profesionales;
        this.tipos = tipos;
        this.pacienteService = pacienteService;
    }

    public record EntradaDto(Long id, PacienteResumenDto paciente, String especialidad, ProfesionalDto profesional,
                             String tipoCita, int duracionMinutos, String prioridad, String prioridadDescripcion,
                             String motivo, Instant desde, String registradoPor) {
        static EntradaDto de(EntradaListaEspera e) {
            return new EntradaDto(e.getId(), PacienteResumenDto.de(e.getPaciente()), e.getEspecialidad().getNombre(),
                    e.getProfesional() == null ? null : ProfesionalDto.de(e.getProfesional()), e.getTipo().getNombre(),
                    e.getTipo().getDuracionMinutos(), e.getPrioridad().name(), e.getPrioridad().getDescripcion(),
                    e.getMotivo(), e.getCreadoEn(), e.getCreadoPor());
        }
    }

    /** En el orden en que se asignarán los cupos: prioridad y luego antigüedad. */
    @Transactional(readOnly = true)
    public List<EntradaDto> activas() {
        return repository.activas().stream()
                .sorted(Comparator.comparing(EntradaListaEspera::getPrioridad)
                        .thenComparing(EntradaListaEspera::getCreadoEn))
                .map(EntradaDto::de)
                .toList();
    }

    @Transactional
    public EntradaDto agregar(ListaEsperaRequest r) {
        Paciente paciente = pacienteService.buscarEntidad(r.pacienteId());
        if (paciente.isIdentificacionTemporal()) {
            throw new ReglaNegocioException("PACIENTE_SIN_DOCUMENTO",
                    "Registre el documento del paciente antes de ponerlo en lista de espera.");
        }
        Especialidad especialidad = especialidades.findById(r.especialidadId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad", r.especialidadId()));
        if (repository.existsByPacienteIdAndEspecialidadIdAndEstado(paciente.getId(), especialidad.getId(),
                EntradaListaEspera.Estado.ACTIVA)) {
            throw new ReglaNegocioException("YA_EN_LISTA_ESPERA",
                    "El paciente ya está en lista de espera para " + especialidad.getNombre() + ".");
        }
        Profesional profesional = r.profesionalId() == null ? null : profesionales.findById(r.profesionalId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Profesional", r.profesionalId()));
        if (profesional != null && !profesional.getEspecialidad().getId().equals(especialidad.getId())) {
            throw new ReglaNegocioException("PROFESIONAL_OTRA_ESPECIALIDAD",
                    profesional.getNombre() + " no es de " + especialidad.getNombre() + ".");
        }
        TipoCita tipo = tipos.findById(r.tipoCitaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de cita", r.tipoCitaId()));
        String motivo = r.motivo() == null || r.motivo().isBlank() ? null : r.motivo().trim();
        return EntradaDto.de(repository.save(
                new EntradaListaEspera(paciente, especialidad, profesional, tipo, r.prioridad(), motivo)));
    }

    @Transactional
    public void retirar(Long id) {
        repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Lista de espera", id)).retirar();
    }
}
