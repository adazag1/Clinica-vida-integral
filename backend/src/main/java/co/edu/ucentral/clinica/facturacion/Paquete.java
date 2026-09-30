package co.edu.ucentral.clinica.facturacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Paquete del contrato: si la cuenta tiene todos sus servicios, se cobran como una sola línea al
 * precio pactado en lugar de uno por uno.
 */
@Entity
@Table(name = "paquete")
public class Paquete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private BigDecimal precio;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "paquete_servicio", joinColumns = @JoinColumn(name = "paquete_id"),
            inverseJoinColumns = @JoinColumn(name = "servicio_id"))
    private Set<ServicioFacturable> servicios = new LinkedHashSet<>();

    protected Paquete() {
    }

    Paquete(Contrato contrato, String codigo, String nombre, BigDecimal precio, Set<ServicioFacturable> servicios) {
        this.contrato = contrato;
        this.codigo = codigo;
        this.nombre = nombre;
        this.precio = precio;
        this.servicios = new LinkedHashSet<>(servicios);
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

    public BigDecimal getPrecio() {
        return precio;
    }

    public Set<ServicioFacturable> getServicios() {
        return servicios;
    }
}
