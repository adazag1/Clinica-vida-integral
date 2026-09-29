package co.edu.ucentral.clinica.agenda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Franja en la que un profesional atiende (RFAGE-015). Fuera de sus franjas no se puede agendar.
 * Las franjas RESERVADA_PRIORIDAD solo aceptan citas prioritarias o urgentes (RFAGE-006).
 */
@Entity
@Table(name = "horario_profesional")
public class HorarioProfesional {

    public enum Tipo {
        NORMAL,
        RESERVADA_PRIORIDAD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profesional_id", nullable = false)
    private Long profesionalId;

    /** 1 = lunes … 7 = domingo (ISO-8601). */
    @Column(name = "dia_semana", nullable = false)
    private int diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    protected HorarioProfesional() {
    }

    public HorarioProfesional(Long profesionalId, DayOfWeek dia, LocalTime horaInicio, LocalTime horaFin, Tipo tipo) {
        this.profesionalId = profesionalId;
        this.diaSemana = dia.getValue();
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.tipo = tipo;
    }

    public Long getId() {
        return id;
    }

    public Long getProfesionalId() {
        return profesionalId;
    }

    public DayOfWeek getDia() {
        return DayOfWeek.of(diaSemana);
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public Tipo getTipo() {
        return tipo;
    }
}
