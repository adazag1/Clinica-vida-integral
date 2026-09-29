package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** RFAGE-014: periodo en que el profesional no atiende y no se puede agendar. */
@Entity
@Table(name = "bloqueo_agenda")
public class BloqueoAgenda extends EntidadAuditable {

    public enum Motivo {
        VACACIONES("Vacaciones"),
        PERMISO("Permiso"),
        INCAPACIDAD("Incapacidad"),
        CAPACITACION("Capacitación"),
        REUNION("Reunión"),
        OTRO("Otro");

        private final String descripcion;

        Motivo(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profesional_id", nullable = false)
    private Long profesionalId;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Motivo motivo;

    private String descripcion;

    protected BloqueoAgenda() {
    }

    public BloqueoAgenda(Long profesionalId, Instant inicio, Instant fin, Motivo motivo, String descripcion) {
        this.profesionalId = profesionalId;
        this.inicio = inicio;
        this.fin = fin;
        this.motivo = motivo;
        this.descripcion = descripcion;
    }

    public Long getId() {
        return id;
    }

    public Long getProfesionalId() {
        return profesionalId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public Motivo getMotivo() {
        return motivo;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
