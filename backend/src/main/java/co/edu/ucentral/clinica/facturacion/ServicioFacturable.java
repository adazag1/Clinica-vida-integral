package co.edu.ucentral.clinica.facturacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Servicio que se factura (código CUPS de referencia) con su tarifa base. */
@Entity
@Table(name = "servicio_facturable")
public class ServicioFacturable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoServicio tipo;

    /** Enlace con el acto clínico que lo genera (LAB:HEMOGRAMA, IMG:RX_TORAX...); nulo = solo cargo manual. */
    @Column(name = "clave_origen", unique = true)
    private String claveOrigen;

    @Column(name = "tarifa_base", nullable = false)
    private BigDecimal tarifaBase;

    @Column(name = "requiere_autorizacion", nullable = false)
    private boolean requiereAutorizacion;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "modificado_en")
    private Instant modificadoEn;

    @Column(name = "modificado_por")
    private String modificadoPor;

    protected ServicioFacturable() {
    }

    ServicioFacturable(String codigo, String nombre, TipoServicio tipo, BigDecimal tarifaBase, boolean requiereAutorizacion,
                       String usuario) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.tipo = tipo;
        actualizar(nombre, tarifaBase, requiereAutorizacion, true, usuario);
    }

    void actualizar(String nombre, BigDecimal tarifaBase, boolean requiereAutorizacion, boolean activo, String usuario) {
        this.nombre = nombre;
        this.tarifaBase = tarifaBase;
        this.requiereAutorizacion = requiereAutorizacion;
        this.activo = activo;
        this.modificadoPor = usuario;
        this.modificadoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoServicio getTipo() {
        return tipo;
    }

    public String getClaveOrigen() {
        return claveOrigen;
    }

    public BigDecimal getTarifaBase() {
        return tarifaBase;
    }

    public boolean isRequiereAutorizacion() {
        return requiereAutorizacion;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getModificadoEn() {
        return modificadoEn;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }
}
