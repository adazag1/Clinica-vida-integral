package co.edu.ucentral.clinica.auditoriaclinica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** RFAU-009: cada cambio de codificación, con el valor anterior, el nuevo, el motivo y quién lo hizo. Solo se inserta. */
@Entity
@Table(name = "codificacion_cambio")
public class CodificacionCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(nullable = false)
    private String campo;

    private String anterior;

    private String nuevo;

    private String motivo;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha;

    protected CodificacionCambio() {
    }

    CodificacionCambio(Long cuentaId, String campo, String anterior, String nuevo, String motivo, String usuario) {
        this.cuentaId = cuentaId;
        this.campo = campo;
        this.anterior = anterior;
        this.nuevo = nuevo;
        this.motivo = motivo;
        this.usuario = usuario;
        this.fecha = Instant.now();
    }

    public String getCampo() {
        return campo;
    }

    public String getAnterior() {
        return anterior;
    }

    public String getNuevo() {
        return nuevo;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getFecha() {
        return fecha;
    }
}
