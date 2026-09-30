package co.edu.ucentral.clinica.facturacion;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Factura emitida a la aseguradora. El saldo baja con los pagos y las glosas aceptadas. */
@Entity
@Table(name = "factura")
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id")
    private Cuenta cuenta;

    @Column(nullable = false)
    private String aseguradora;

    @Column(name = "fecha_emision", nullable = false)
    private Instant fechaEmision;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal ajustes;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "valor_paciente", nullable = false)
    private BigDecimal valorPaciente;

    @Column(nullable = false)
    private BigDecimal saldo;

    @Column(nullable = false)
    private String cufe;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String xml;

    @Column(name = "estado_dian", nullable = false)
    private String estadoDian;

    @Column(name = "emitida_por", nullable = false)
    private String emitidaPor;

    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL)
    @OrderBy("orden")
    private List<FacturaLinea> lineas = new ArrayList<>();

    protected Factura() {
    }

    Factura(String numero, Cuenta cuenta, Instant fechaEmision, LocalDate fechaVencimiento, BigDecimal subtotal,
            BigDecimal ajustes, BigDecimal total, BigDecimal valorPaciente, String emitidaPor) {
        this.numero = numero;
        this.cuenta = cuenta;
        this.aseguradora = cuenta.getAseguradora();
        this.fechaEmision = fechaEmision;
        this.fechaVencimiento = fechaVencimiento;
        this.subtotal = subtotal;
        this.ajustes = ajustes;
        this.total = total;
        this.valorPaciente = valorPaciente;
        this.saldo = total;
        this.emitidaPor = emitidaPor;
        this.cufe = "";
        this.xml = "";
        this.estadoDian = "SIMULADA";
    }

    void firmarElectronicamente(String cufe, String xml, String estadoDian) {
        this.cufe = cufe;
        this.xml = xml;
        this.estadoDian = estadoDian;
    }

    void abonar(BigDecimal valor, String concepto) {
        if (valor.signum() <= 0) {
            throw new ReglaNegocioException("VALOR_INVALIDO", "El valor debe ser mayor que cero.");
        }
        if (valor.compareTo(saldo) > 0) {
            throw new ReglaNegocioException("SUPERA_SALDO", "El " + concepto + " supera el saldo de la factura ($"
                    + saldo.toPlainString() + ").");
        }
        saldo = saldo.subtract(valor);
    }

    public boolean isSaldada() {
        return saldo.signum() == 0;
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public Cuenta getCuenta() {
        return cuenta;
    }

    public String getAseguradora() {
        return aseguradora;
    }

    public Instant getFechaEmision() {
        return fechaEmision;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getAjustes() {
        return ajustes;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getValorPaciente() {
        return valorPaciente;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public String getCufe() {
        return cufe;
    }

    public String getXml() {
        return xml;
    }

    public String getEstadoDian() {
        return estadoDian;
    }

    public String getEmitidaPor() {
        return emitidaPor;
    }

    public List<FacturaLinea> getLineas() {
        return lineas;
    }
}
