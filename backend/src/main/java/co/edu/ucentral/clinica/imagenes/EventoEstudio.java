package co.edu.ucentral.clinica.imagenes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "evento_estudio")
public class EventoEstudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "estudio_id", nullable = false)
    private Long estudioId;

    @Column(nullable = false)
    private String accion;

    private String detalle;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha;

    protected EventoEstudio() {
    }

    EventoEstudio(Long estudioId, String accion, String detalle, String usuario) {
        this.estudioId = estudioId;
        this.accion = accion;
        this.detalle = detalle == null || detalle.length() <= 500 ? detalle : detalle.substring(0, 497) + "…";
        this.usuario = usuario;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
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
