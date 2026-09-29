package co.edu.ucentral.clinica.laboratorio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Historial de la orden: quién hizo qué y cuándo (RNFLC-006). */
@Entity
@Table(name = "evento_orden_lab")
public class EventoOrdenLab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "orden_id", nullable = false)
    private Long ordenId;

    @Column(nullable = false)
    private String accion;

    private String detalle;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha;

    protected EventoOrdenLab() {
    }

    public EventoOrdenLab(Long ordenId, String accion, String detalle, String usuario) {
        this.ordenId = ordenId;
        this.accion = accion;
        this.detalle = detalle;
        this.usuario = usuario;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrdenId() {
        return ordenId;
    }

    public String getAccion() {
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
