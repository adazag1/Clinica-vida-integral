package co.edu.ucentral.clinica.agenda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** RFAGE-008 / RFAGE-012: historial inmutable de todo lo que le pasa a una cita. */
@Entity
@Table(name = "evento_cita")
public class EventoCita {

    public enum Accion {
        CREADA("Cita creada"),
        ASIGNADA_LISTA_ESPERA("Asignada desde la lista de espera"),
        CONFIRMADA("Confirmada"),
        CANCELADA("Cancelada"),
        REPROGRAMADA("Reprogramada"),
        RECORDATORIO("Recordatorio enviado"),
        ATENDIDA("Paciente atendido"),
        NO_ASISTIO("Paciente no asistió");

        private final String descripcion;

        Accion(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cita_id", nullable = false)
    private Long citaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Accion accion;

    private String detalle;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha;

    protected EventoCita() {
    }

    public EventoCita(Long citaId, Accion accion, String detalle, String usuario) {
        this.citaId = citaId;
        this.accion = accion;
        this.detalle = detalle;
        this.usuario = usuario;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCitaId() {
        return citaId;
    }

    public Accion getAccion() {
        return accion;
    }

    public String getDetalle() {
        return detalle;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getFecha() {
        return fecha;
    }
}
