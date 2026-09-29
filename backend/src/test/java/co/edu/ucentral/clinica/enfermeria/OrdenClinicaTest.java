package co.edu.ucentral.clinica.enfermeria;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdenClinicaTest {

    private static final Instant INICIO = Instant.parse("2026-09-29T12:00:00Z");

    private static OrdenClinica orden(OrdenClinica.Tipo tipo, Integer frecuencia, Duration duracion) {
        return new OrdenClinica(1L, null, tipo, "orden", null, null, null, frecuencia, INICIO, INICIO.plus(duracion),
                false, null, "medico", "Médico");
    }

    @Test
    void programaCadaFrecuenciaSinPasarDelFin() {
        OrdenClinica o = orden(OrdenClinica.Tipo.CUIDADO, 8, Duration.ofHours(24));
        assertThat(o.horarios(INICIO.minus(Duration.ofDays(1)), INICIO.plus(Duration.ofDays(2))))
                .containsExactly(INICIO, INICIO.plus(Duration.ofHours(8)), INICIO.plus(Duration.ofHours(16)));
        // Ventana que empieza a mitad de camino: no se desalinea del horario original
        assertThat(o.horarios(INICIO.plus(Duration.ofHours(5)), INICIO.plus(Duration.ofHours(20))))
                .containsExactly(INICIO.plus(Duration.ofHours(8)), INICIO.plus(Duration.ofHours(16)));
        assertThat(o.esHoraProgramada(INICIO.plus(Duration.ofHours(8)))).isTrue();
        assertThat(o.esHoraProgramada(INICIO.plus(Duration.ofHours(9)))).isFalse();
        assertThat(o.esHoraProgramada(INICIO.plus(Duration.ofHours(24)))).isFalse();
    }

    @Test
    void sinFrecuenciaEsDosisUnicaYLaDietaNoSeProgramaPorHora() {
        assertThat(orden(OrdenClinica.Tipo.MEDICAMENTO, null, Duration.ofHours(4)).horarios(INICIO, INICIO.plus(Duration.ofHours(4))))
                .containsExactly(INICIO);
        assertThat(orden(OrdenClinica.Tipo.DIETA, null, Duration.ofHours(24)).horarios(INICIO, INICIO.plus(Duration.ofHours(24))))
                .isEmpty();
    }

    @Test
    void vencidaOSuspendidaDejaDeSerEjecutable() {
        OrdenClinica o = orden(OrdenClinica.Tipo.CUIDADO, 6, Duration.ofHours(12));
        assertThat(o.isVigente(INICIO.plus(Duration.ofHours(1)))).isTrue();
        assertThat(o.situacion(INICIO.plus(Duration.ofHours(13)))).isEqualTo("VENCIDA");
        assertThat(o.isVigente(INICIO.plus(Duration.ofHours(13)))).isFalse();

        o.suspender("Cambio de plan", "medico");
        assertThat(o.situacion(INICIO.plus(Duration.ofHours(1)))).isEqualTo("SUSPENDIDA");
        assertThat(o.isVigente(INICIO.plus(Duration.ofHours(1)))).isFalse();
        assertThatThrownBy(() -> o.suspender("otra vez", "medico")).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void turnoSegunLaHora() {
        assertThat(EntregaTurno.Turno.de(java.time.LocalTime.of(7, 0))).isEqualTo(EntregaTurno.Turno.MANANA);
        assertThat(EntregaTurno.Turno.de(java.time.LocalTime.of(18, 59))).isEqualTo(EntregaTurno.Turno.TARDE);
        assertThat(EntregaTurno.Turno.de(java.time.LocalTime.of(2, 0))).isEqualTo(EntregaTurno.Turno.NOCHE);
    }
}
