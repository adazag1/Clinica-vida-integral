package co.edu.ucentral.clinica.agenda.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * RFAGE-011: resumen del día y carga por profesional.
 *
 * @param asistencia porcentaje de atendidas sobre (atendidas + no asistió); null si aún no hay datos
 */
public record IndicadoresDto(LocalDate fecha, long citas, long confirmadas, long canceladas, long enListaEspera,
                             Double asistencia, List<Carga> carga) {

    /** @param ocupacion minutos agendados sobre minutos de atención del día, en porcentaje */
    public record Carga(Long profesionalId, String profesional, String especialidad, long citas,
                        long minutosAgendados, long minutosDisponibles, Double ocupacion) {
    }
}
