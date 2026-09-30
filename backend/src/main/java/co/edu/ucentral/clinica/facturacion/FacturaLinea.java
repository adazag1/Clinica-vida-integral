package co.edu.ucentral.clinica.facturacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "factura_linea")
public class FacturaLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id")
    private Factura factura;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "valor_unitario", nullable = false)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal;

    private String nota;

    protected FacturaLinea() {
    }

    FacturaLinea(Factura factura, int orden, Liquidacion.Linea l) {
        this.factura = factura;
        this.orden = orden;
        this.codigo = l.codigo();
        this.descripcion = l.descripcion().length() > 250 ? l.descripcion().substring(0, 250) : l.descripcion();
        this.cantidad = l.cantidad();
        this.valorUnitario = l.valorUnitario();
        this.valorTotal = l.valorTotal();
        this.nota = l.nota();
    }

    public int getOrden() {
        return orden;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public String getNota() {
        return nota;
    }
}
