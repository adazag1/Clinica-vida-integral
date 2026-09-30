package co.edu.ucentral.clinica.facturacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** RFRC-014: una línea del historial de la cuenta. Solo se inserta. */
@Entity
@Table(name = "evento_cuenta")
public class EventoCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(nullable = false)
    private String accion;

    private String detalle;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha;

    protected EventoCuenta() {
    }

    public EventoCuenta(Long cuentaId, String accion, String detalle, String usuario) {
        this.cuentaId = cuentaId;
        this.accion = accion;
        this.detalle = detalle == null || detalle.length() <= 500 ? detalle : detalle.substring(0, 497) + "…";
        this.usuario = usuario;
        this.fecha = Instant.now();
    }

    public Long getCuentaId() {
        return cuentaId;
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
