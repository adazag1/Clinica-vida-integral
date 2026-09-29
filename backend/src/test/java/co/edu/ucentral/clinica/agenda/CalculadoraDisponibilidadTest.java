package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.agenda.CalculadoraDisponibilidad.Cupo;
import co.edu.ucentral.clinica.agenda.CalculadoraDisponibilidad.Intervalo;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraDisponibilidadTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final CalculadoraDisponibilidad calc = new CalculadoraDisponibilidad(BOGOTA);

    /** Un lunes futuro, para que ningún cupo quede en el pasado. */
    private final LocalDate lunes = LocalDate.now(BOGOTA).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1);
    private final Instant ahora = Instant.now();

    private final List<HorarioProfesional> franjas = List.of(
            new HorarioProfesional(1L, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0), HorarioProfesional.Tipo.NORMAL),
            new HorarioProfesional(1L, DayOfWeek.MONDAY, LocalTime.of(16, 0), LocalTime.of(17, 0),
                    HorarioProfesional.Tipo.RESERVADA_PRIORIDAD));

    private Instant a(int hora, int minuto) {
        return ZonedDateTime.of(lunes, LocalTime.of(hora, minuto), BOGOTA).toInstant();
    }

    @Test
    void divideLaFranjaSegunLaDuracionDelTipo() {
        List<Cupo> cupos = calc.cupos(lunes, franjas, List.of(), Duration.ofMinutes(20), PrioridadCita.NORMAL, ahora);
        assertThat(cupos).extracting(Cupo::inicio).containsExactly(a(8, 0), a(8, 20), a(8, 40));
    }

    @Test
    void saltaLoOcupadoSinPerderElHuecoSiguiente() {
        // Cita de 8:10 a 8:30: el siguiente cupo de 20 min empieza a las 8:30, no a las 8:40
        List<Cupo> cupos = calc.cupos(lunes, franjas, List.of(new Intervalo(a(8, 10), a(8, 30))),
                Duration.ofMinutes(20), PrioridadCita.NORMAL, ahora);
        assertThat(cupos).extracting(Cupo::inicio).containsExactly(a(8, 30));
    }

    @Test
    void lasFranjasReservadasSoloAparecenParaPrioridades() {
        assertThat(calc.cupos(lunes, franjas, List.of(), Duration.ofMinutes(30), PrioridadCita.NORMAL, ahora))
                .noneMatch(Cupo::reservado);
        assertThat(calc.cupos(lunes, franjas, List.of(), Duration.ofMinutes(30), PrioridadCita.URGENTE, ahora))
                .filteredOn(Cupo::reservado).extracting(Cupo::inicio).containsExactly(a(16, 0), a(16, 30));
    }

    @Test
    void unaCitaQueNoCabeEnLaFranjaNoGeneraCupo() {
        assertThat(calc.cupos(lunes, franjas, List.of(), Duration.ofMinutes(90), PrioridadCita.NORMAL, ahora)).isEmpty();
    }

    @Test
    void sinHorarioEseDiaNoHayCupos() {
        assertThat(calc.cupos(lunes.plusDays(1), franjas, List.of(), Duration.ofMinutes(20), PrioridadCita.NORMAL, ahora))
                .isEmpty();
    }

    @Test
    void rechazaHorariosFueraDeLasFranjas() {
        assertThatThrownBy(() -> calc.validarHorario(a(8, 50), a(9, 10), franjas, PrioridadCita.NORMAL, ahora))
                .isInstanceOf(ReglaNegocioException.class).extracting("codigo").isEqualTo("FUERA_DE_HORARIO");
        assertThatThrownBy(() -> calc.validarHorario(a(12, 0), a(12, 20), franjas, PrioridadCita.NORMAL, ahora))
                .extracting("codigo").isEqualTo("FUERA_DE_HORARIO");
    }

    @Test
    void laFranjaReservadaExigePrioridad() {
        assertThatThrownBy(() -> calc.validarHorario(a(16, 0), a(16, 20), franjas, PrioridadCita.NORMAL, ahora))
                .extracting("codigo").isEqualTo("CUPO_RESERVADO");
        calc.validarHorario(a(16, 0), a(16, 20), franjas, PrioridadCita.PRIORITARIA, ahora);
    }

    @Test
    void noSeAgendaEnElPasado() {
        Instant ayer = Instant.now().minus(Duration.ofDays(1));
        assertThatThrownBy(() -> calc.validarHorario(ayer, ayer.plusSeconds(1200), franjas, PrioridadCita.NORMAL, ahora))
                .extracting("codigo").isEqualTo("HORARIO_PASADO");
    }
}
