package co.edu.ucentral.clinica.catalogos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "catalogo_cambio")
public class CatalogoCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String catalogo;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String accion;

    private String detalle;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha;

    protected CatalogoCambio() {
    }

    CatalogoCambio(String catalogo, int version, String codigo, String accion, String detalle, String usuario) {
        this.catalogo = catalogo;
        this.version = version;
        this.codigo = codigo;
        this.accion = accion;
        this.detalle = detalle == null || detalle.length() <= 500 ? detalle : detalle.substring(0, 497) + "…";
        this.usuario = usuario;
        this.fecha = Instant.now();
    }

    public String getCatalogo() {
        return catalogo;
    }

    public int getVersion() {
        return version;
    }

    public String getCodigo() {
        return codigo;
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
