package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de la agenda sin base de datos: qué cupos hay libres (RFAGE-003) y si un horario se
 * puede reservar (RFAGE-005, RFAGE-006, RFAGE-014, RFAGE-015). Las horas de las franjas son
 * hora local de la clínica.
 */
public class CalculadoraDisponibilidad {

    /** Periodo ocupado: una cita que ocupa cupo o un bloqueo de agenda. */
    public record Intervalo(Instant inicio, Instant fin) {
        boolean cruza(Instant desde, Instant hasta) {
            return inicio.isBefore(hasta) && fin.isAfter(desde);
        }
    }

    public record Cupo(Instant inicio, Instant fin, boolean reservado) {
    }

    private final ZoneId zona;

    public CalculadoraDisponibilidad(ZoneId zona) {
        this.zona = zona;
    }

    /**
     * Cupos libres de un día. Recorre cada franja desde su inicio: si el cupo choca con algo,
     * salta al final de lo que estorba; así no se pierden huecos entre citas de distinta duración.
     */
    public List<Cupo> cupos(LocalDate fecha, List<HorarioProfesional> franjas, List<Intervalo> ocupados,
                            Duration duracion, PrioridadCita prioridad, Instant ahora) {
        List<Cupo> cupos = new ArrayList<>();
        for (HorarioProfesional franja : franjasDelDia(fecha, franjas)) {
            boolean reservada = franja.getTipo() == HorarioProfesional.Tipo.RESERVADA_PRIORIDAD;
            if (reservada && !prioridad.isPuedeUsarReservadas()) {
                continue;
            }
            Instant finFranja = instante(fecha, franja.getHoraFin());
            Instant t = instante(fecha, franja.getHoraInicio());
            while (!t.plus(duracion).isAfter(finFranja)) {
                Instant desde = t;
                Instant hasta = t.plus(duracion);
                Optional<Instant> finChoque = ocupados.stream()
                        .filter(o -> o.cruza(desde, hasta))
                        .map(Intervalo::fin)
                        .max(Comparator.naturalOrder());
                if (finChoque.isPresent()) {
                    t = finChoque.get();
                } else if (desde.isBefore(ahora)) {
                    t = hasta;
                } else {
                    cupos.add(new Cupo(desde, hasta, reservada));
                    t = hasta;
                }
            }
        }
        cupos.sort(Comparator.comparing(Cupo::inicio));
        return cupos;
    }

    /**
     * Verifica que [inicio, fin) quede dentro de una sola franja del profesional, que no esté en
     * el pasado y que las franjas reservadas solo las usen prioridades. Los choques con otras
     * citas y bloqueos se revisan aparte, con la agenda bloqueada.
     */
    public void validarHorario(Instant inicio, Instant fin, List<HorarioProfesional> franjas, PrioridadCita prioridad,
                               Instant ahora) {
        if (inicio.isBefore(ahora)) {
            throw new ReglaNegocioException("HORARIO_PASADO", "No se puede agendar en una hora que ya pasó.");
        }
        LocalDate fecha = inicio.atZone(zona).toLocalDate();
        HorarioProfesional franja = franjasDelDia(fecha, franjas).stream()
                .filter(f -> !instante(fecha, f.getHoraInicio()).isAfter(inicio)
                        && !instante(fecha, f.getHoraFin()).isBefore(fin))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("FUERA_DE_HORARIO",
                        "El horario está fuera de las franjas de atención del profesional."));
        if (franja.getTipo() == HorarioProfesional.Tipo.RESERVADA_PRIORIDAD && !prioridad.isPuedeUsarReservadas()) {
            throw new ReglaNegocioException("CUPO_RESERVADO",
                    "Ese horario está reservado para citas prioritarias o urgentes.");
        }
    }

    public Instant inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay(zona).toInstant();
    }

    public ZoneId getZona() {
        return zona;
    }

    private List<HorarioProfesional> franjasDelDia(LocalDate fecha, List<HorarioProfesional> franjas) {
        return franjas.stream().filter(f -> f.getDia() == fecha.getDayOfWeek()).toList();
    }

    private Instant instante(LocalDate fecha, LocalTime hora) {
        return ZonedDateTime.of(fecha, hora, zona).toInstant();
    }
}
