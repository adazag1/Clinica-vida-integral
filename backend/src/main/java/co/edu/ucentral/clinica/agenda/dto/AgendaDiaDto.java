package co.edu.ucentral.clinica.agenda.dto;

import co.edu.ucentral.clinica.agenda.BloqueoAgenda;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Agenda de un día: una columna por profesional, como en el mockup de agendamiento. */
public record AgendaDiaDto(LocalDate fecha, List<Columna> profesionales) {

    public record Columna(ProfesionalDto profesional, List<Franja> franjas, List<BloqueoDto> bloqueos,
                          List<CitaDto> citas) {
    }

    /** @param reservada solo para citas prioritarias o urgentes (RFAGE-006) */
    public record Franja(Instant inicio, Instant fin, boolean reservada) {
    }

    public record BloqueoDto(Long id, Long profesionalId, Instant inicio, Instant fin, String motivo,
                             String motivoDescripcion, String descripcion, String creadoPor) {
        public static BloqueoDto de(BloqueoAgenda b) {
            return new BloqueoDto(b.getId(), b.getProfesionalId(), b.getInicio(), b.getFin(), b.getMotivo().name(),
                    b.getMotivo().getDescripcion(), b.getDescripcion(), b.getCreadoPor());
        }
    }
}
