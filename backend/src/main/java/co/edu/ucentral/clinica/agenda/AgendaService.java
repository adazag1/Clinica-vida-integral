package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.agenda.CalculadoraDisponibilidad.Cupo;
import co.edu.ucentral.clinica.agenda.CalculadoraDisponibilidad.Intervalo;
import co.edu.ucentral.clinica.agenda.dto.AgendaDiaDto;
import co.edu.ucentral.clinica.agenda.dto.CatalogosDto;
import co.edu.ucentral.clinica.agenda.dto.CitaDto;
import co.edu.ucentral.clinica.agenda.dto.IndicadoresDto;
import co.edu.ucentral.clinica.agenda.dto.ProfesionalDto;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.BloqueoRequest;
import co.edu.ucentral.clinica.agenda.dto.Solicitudes.FranjaRequest;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.seguridad.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Consulta de la agenda y su parametrización (RFAGE-003, 006, 011, 014, 015). */
@Service
public class AgendaService {

    static final Set<EstadoCita> OCUPAN_CUPO =
            Arrays.stream(EstadoCita.values()).filter(EstadoCita::isOcupaCupo)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(EstadoCita.class)));

    private static final Locale ESPANOL = Locale.forLanguageTag("es-CO");

    /** Máximo de días que se buscan cupos hacia adelante en una consulta. */
    private static final int MAX_DIAS_DISPONIBILIDAD = 31;

    private final SedeRepository sedes;
    private final EspecialidadRepository especialidades;
    private final TipoCitaRepository tipos;
    private final ProfesionalRepository profesionales;
    private final HorarioRepository horarios;
    private final BloqueoRepository bloqueos;
    private final CitaRepository citas;
    private final ListaEsperaRepository listaEspera;
    private final UsuarioRepository usuarios;
    private final CalculadoraDisponibilidad calculadora;

    public AgendaService(SedeRepository sedes, EspecialidadRepository especialidades, TipoCitaRepository tipos,
                         ProfesionalRepository profesionales, HorarioRepository horarios, BloqueoRepository bloqueos,
                         CitaRepository citas, ListaEsperaRepository listaEspera, UsuarioRepository usuarios,
                         CalculadoraDisponibilidad calculadora) {
        this.sedes = sedes;
        this.especialidades = especialidades;
        this.tipos = tipos;
        this.profesionales = profesionales;
        this.horarios = horarios;
        this.bloqueos = bloqueos;
        this.citas = citas;
        this.listaEspera = listaEspera;
        this.usuarios = usuarios;
        this.calculadora = calculadora;
    }

    @Transactional(readOnly = true)
    public CatalogosDto catalogos(String username) {
        Long miProfesional = usuarios.findByUsernameIgnoreCase(username)
                .flatMap(u -> profesionales.findByUsuarioId(u.getId()))
                .map(Profesional::getId)
                .orElse(null);
        return new CatalogosDto(
                sedes.findAllByOrderByNombre().stream().map(CatalogosDto.SedeDto::de).toList(),
                especialidades.findAllByOrderByNombre().stream().map(CatalogosDto.EspecialidadDto::de).toList(),
                tipos.findAllByOrderByNombre().stream().map(CatalogosDto.TipoCitaDto::de).toList(),
                profesionales.findByActivoTrueOrderByNombre().stream().map(ProfesionalDto::de).toList(),
                miProfesional);
    }

    /** Agenda de un día con franjas, bloqueos y citas por profesional, según los filtros. */
    @Transactional(readOnly = true)
    public AgendaDiaDto dia(LocalDate fecha, Long sedeId, Long especialidadId, Long profesionalId) {
        List<Profesional> lista = profesionales.findByActivoTrueOrderByNombre().stream()
                .filter(p -> sedeId == null || p.getSede().getId().equals(sedeId))
                .filter(p -> especialidadId == null || p.getEspecialidad().getId().equals(especialidadId))
                .filter(p -> profesionalId == null || p.getId().equals(profesionalId))
                .toList();
        if (lista.isEmpty()) {
            return new AgendaDiaDto(fecha, List.of());
        }
        List<Long> ids = lista.stream().map(Profesional::getId).toList();
        Instant desde = calculadora.inicioDelDia(fecha);
        Instant hasta = calculadora.inicioDelDia(fecha.plusDays(1));

        Map<Long, List<HorarioProfesional>> franjas = horarios.findByProfesionalIdIn(ids).stream()
                .collect(Collectors.groupingBy(HorarioProfesional::getProfesionalId));
        Map<Long, List<BloqueoAgenda>> bloqueosPor = bloqueos.enRango(ids, desde, hasta).stream()
                .collect(Collectors.groupingBy(BloqueoAgenda::getProfesionalId));
        Map<Long, List<Cita>> citasPor = citas.agenda(ids, desde, hasta).stream()
                .filter(c -> c.getEstado() != EstadoCita.REPROGRAMADA)
                .collect(Collectors.groupingBy(c -> c.getProfesional().getId()));

        List<AgendaDiaDto.Columna> columnas = lista.stream().map(p -> new AgendaDiaDto.Columna(
                ProfesionalDto.de(p),
                franjasDelDia(fecha, franjas.getOrDefault(p.getId(), List.of())),
                bloqueosPor.getOrDefault(p.getId(), List.of()).stream().map(AgendaDiaDto.BloqueoDto::de).toList(),
                citasPor.getOrDefault(p.getId(), List.of()).stream().map(CitaDto::resumen).toList()
        )).toList();
        return new AgendaDiaDto(fecha, columnas);
    }

    public record CupoDto(Instant inicio, Instant fin, boolean reservado, Long profesionalId, String profesional,
                          String especialidad, String sede) {
    }

    /**
     * RFAGE-003: cupos libres desde {@code fecha} durante {@code dias} días, para un profesional
     * o para todos los de una especialidad. Respeta horarios, bloqueos, citas y franjas reservadas.
     */
    @Transactional(readOnly = true)
    public List<CupoDto> disponibilidad(Long profesionalId, Long especialidadId, Long sedeId, Long tipoCitaId,
                                        PrioridadCita prioridad, LocalDate fecha, int dias) {
        if (profesionalId == null && especialidadId == null) {
            throw new ReglaNegocioException("FILTRO_REQUERIDO", "Seleccione un profesional o una especialidad.");
        }
        TipoCita tipo = tipos.findById(tipoCitaId).orElseThrow(() -> new RecursoNoEncontradoException("Tipo de cita", tipoCitaId));
        List<Profesional> lista = profesionales.findByActivoTrueOrderByNombre().stream()
                .filter(p -> profesionalId == null || p.getId().equals(profesionalId))
                .filter(p -> especialidadId == null || p.getEspecialidad().getId().equals(especialidadId))
                .filter(p -> sedeId == null || p.getSede().getId().equals(sedeId))
                .toList();
        if (lista.isEmpty()) {
            return List.of();
        }
        int n = Math.max(1, Math.min(dias, MAX_DIAS_DISPONIBILIDAD));
        List<Long> ids = lista.stream().map(Profesional::getId).toList();
        Instant desde = calculadora.inicioDelDia(fecha);
        Instant hasta = calculadora.inicioDelDia(fecha.plusDays(n));
        Map<Long, List<HorarioProfesional>> franjas = horarios.findByProfesionalIdIn(ids).stream()
                .collect(Collectors.groupingBy(HorarioProfesional::getProfesionalId));
        Map<Long, List<Intervalo>> ocupados = ocupados(ids, desde, hasta);
        Instant ahora = Instant.now();

        List<CupoDto> resultado = new ArrayList<>();
        for (Profesional p : lista) {
            for (int d = 0; d < n; d++) {
                for (Cupo c : calculadora.cupos(fecha.plusDays(d), franjas.getOrDefault(p.getId(), List.of()),
                        ocupados.getOrDefault(p.getId(), List.of()), tipo.getDuracion(), prioridad, ahora)) {
                    resultado.add(new CupoDto(c.inicio(), c.fin(), c.reservado(), p.getId(), p.getNombre(),
                            p.getEspecialidad().getNombre(), p.getSede().getNombre()));
                }
            }
        }
        resultado.sort(Comparator.comparing(CupoDto::inicio).thenComparing(CupoDto::profesional));
        return resultado;
    }

    /** Citas que ocupan cupo y bloqueos, por profesional. */
    Map<Long, List<Intervalo>> ocupados(List<Long> ids, Instant desde, Instant hasta) {
        Map<Long, List<Intervalo>> mapa = new HashMap<>();
        for (Long id : ids) {
            citas.ocupadasDelProfesional(id, desde, hasta, OCUPAN_CUPO)
                    .forEach(c -> mapa.computeIfAbsent(id, k -> new ArrayList<>()).add(new Intervalo(c.getInicio(), c.getFin())));
        }
        bloqueos.enRango(ids, desde, hasta)
                .forEach(b -> mapa.computeIfAbsent(b.getProfesionalId(), k -> new ArrayList<>())
                        .add(new Intervalo(b.getInicio(), b.getFin())));
        return mapa;
    }

    /** RFAGE-011: resumen del día y ocupación de cada profesional. */
    @Transactional(readOnly = true)
    public IndicadoresDto indicadores(LocalDate fecha, Long sedeId) {
        List<Profesional> lista = profesionales.findByActivoTrueOrderByNombre().stream()
                .filter(p -> sedeId == null || p.getSede().getId().equals(sedeId))
                .toList();
        List<Long> ids = lista.stream().map(Profesional::getId).toList();
        Instant desde = calculadora.inicioDelDia(fecha);
        Instant hasta = calculadora.inicioDelDia(fecha.plusDays(1));
        List<Cita> delDia = ids.isEmpty() ? List.of() : citas.agenda(ids, desde, hasta).stream()
                .filter(c -> !c.getInicio().isBefore(desde))
                .toList();
        Map<Long, List<HorarioProfesional>> franjas = ids.isEmpty() ? Map.of() : horarios.findByProfesionalIdIn(ids)
                .stream().collect(Collectors.groupingBy(HorarioProfesional::getProfesionalId));

        long ocupan = delDia.stream().filter(c -> c.getEstado().isOcupaCupo()).count();
        long confirmadas = delDia.stream().filter(c -> c.getEstado() == EstadoCita.CONFIRMADA).count();
        long canceladas = delDia.stream().filter(c -> c.getEstado() == EstadoCita.CANCELADA).count();
        long atendidas = delDia.stream().filter(c -> c.getEstado() == EstadoCita.ATENDIDA).count();
        long inasistencias = delDia.stream().filter(c -> c.getEstado() == EstadoCita.NO_ASISTIO).count();
        Double asistencia = atendidas + inasistencias == 0 ? null : 100.0 * atendidas / (atendidas + inasistencias);

        List<IndicadoresDto.Carga> carga = lista.stream().map(p -> {
            List<Cita> suyas = delDia.stream()
                    .filter(c -> c.getProfesional().getId().equals(p.getId()) && c.getEstado().isOcupaCupo())
                    .toList();
            long agendados = suyas.stream().mapToLong(c -> Duration.between(c.getInicio(), c.getFin()).toMinutes()).sum();
            long disponibles = franjasDelDia(fecha, franjas.getOrDefault(p.getId(), List.of())).stream()
                    .mapToLong(f -> Duration.between(f.inicio(), f.fin()).toMinutes()).sum();
            Double ocupacion = disponibles == 0 ? null : Math.min(100.0, 100.0 * agendados / disponibles);
            return new IndicadoresDto.Carga(p.getId(), p.getNombre(), p.getEspecialidad().getNombre(), suyas.size(),
                    agendados, disponibles, ocupacion);
        }).toList();

        return new IndicadoresDto(fecha, ocupan, confirmadas, canceladas, listaEspera.activas().size(), asistencia, carga);
    }

    private List<AgendaDiaDto.Franja> franjasDelDia(LocalDate fecha, List<HorarioProfesional> franjas) {
        return franjas.stream()
                .filter(f -> f.getDia() == fecha.getDayOfWeek())
                .sorted(Comparator.comparing(HorarioProfesional::getHoraInicio))
                .map(f -> new AgendaDiaDto.Franja(
                        ZonedDateTime.of(fecha, f.getHoraInicio(), calculadora.getZona()).toInstant(),
                        ZonedDateTime.of(fecha, f.getHoraFin(), calculadora.getZona()).toInstant(),
                        f.getTipo() == HorarioProfesional.Tipo.RESERVADA_PRIORIDAD))
                .toList();
    }

    // ---------- Parametrización: horarios (RFAGE-015) y bloqueos (RFAGE-014) ----------

    @Transactional(readOnly = true)
    public List<FranjaRequest> horario(Long profesionalId) {
        buscarProfesional(profesionalId);
        return horarios.findByProfesionalIdOrderByDiaSemanaAscHoraInicioAsc(profesionalId).stream()
                .map(h -> new FranjaRequest(h.getDia().getValue(), h.getHoraInicio(), h.getHoraFin(), h.getTipo()))
                .toList();
    }

    /** Reemplaza el horario semanal. Las citas ya agendadas no se tocan. */
    @Transactional
    public List<FranjaRequest> reemplazarHorario(Long profesionalId, List<FranjaRequest> franjas) {
        buscarProfesional(profesionalId);
        for (FranjaRequest f : franjas) {
            if (!f.horaInicio().isBefore(f.horaFin())) {
                throw new ReglaNegocioException("FRANJA_INVALIDA", "La hora de inicio debe ser anterior a la de fin.");
            }
            boolean cruza = franjas.stream().anyMatch(o -> o != f && o.dia().equals(f.dia())
                    && o.horaInicio().isBefore(f.horaFin()) && o.horaFin().isAfter(f.horaInicio()));
            if (cruza) {
                throw new ReglaNegocioException("FRANJAS_CRUZADAS",
                        "Hay franjas que se cruzan el "
                                + DayOfWeek.of(f.dia()).getDisplayName(TextStyle.FULL, ESPANOL) + ".");
            }
        }
        horarios.deleteAll(horarios.findByProfesionalIdOrderByDiaSemanaAscHoraInicioAsc(profesionalId));
        horarios.flush();
        franjas.forEach(f -> horarios.save(new HorarioProfesional(profesionalId, DayOfWeek.of(f.dia()),
                f.horaInicio(), f.horaFin(), f.tipo())));
        return horario(profesionalId);
    }

    @Transactional(readOnly = true)
    public List<AgendaDiaDto.BloqueoDto> bloqueosVigentes() {
        return bloqueos.vigentes(Instant.now()).stream().map(AgendaDiaDto.BloqueoDto::de).toList();
    }

    /**
     * RFAGE-014. Si ya hay citas en ese periodo no se bloquea en silencio: hay que reprogramarlas
     * primero para que ningún paciente llegue a una agenda cerrada.
     */
    @Transactional
    public AgendaDiaDto.BloqueoDto bloquear(BloqueoRequest r) {
        if (!r.inicio().isBefore(r.fin())) {
            throw new ReglaNegocioException("BLOQUEO_INVALIDO", "El inicio del bloqueo debe ser anterior al fin.");
        }
        Profesional p = profesionales.bloquearParaAgendar(r.profesionalId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Profesional", r.profesionalId()));
        long afectadas = citas.ocupadasDelProfesional(p.getId(), r.inicio(), r.fin(),
                EnumSet.of(EstadoCita.PROGRAMADA, EstadoCita.CONFIRMADA)).size();
        if (afectadas > 0) {
            throw new ReglaNegocioException("BLOQUEO_CON_CITAS", "Hay " + afectadas
                    + (afectadas == 1 ? " cita" : " citas") + " en ese periodo. Reprográmelas antes de bloquear la agenda.");
        }
        BloqueoAgenda b = bloqueos.save(new BloqueoAgenda(p.getId(), r.inicio(), r.fin(), r.motivo(),
                r.descripcion() == null || r.descripcion().isBlank() ? null : r.descripcion().trim()));
        return AgendaDiaDto.BloqueoDto.de(b);
    }

    @Transactional
    public void desbloquear(Long id) {
        bloqueos.delete(bloqueos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Bloqueo", id)));
    }

    private Profesional buscarProfesional(Long id) {
        return profesionales.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Profesional", id));
    }
}
