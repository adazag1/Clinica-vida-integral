package co.edu.ucentral.clinica.facturacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "factura_id", nullable = false)
    private Long facturaId;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private String medio;

    private String referencia;

    @Column(name = "registrado_por", nullable = false)
    private String registradoPor;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    protected Pago() {
    }

    Pago(Long facturaId, BigDecimal valor, LocalDate fecha, String medio, String referencia, String usuario) {
        this.facturaId = facturaId;
        this.valor = valor;
        this.fecha = fecha;
        this.medio = medio;
        this.referencia = referencia;
        this.registradoPor = usuario;
        this.registradoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getFacturaId() {
        return facturaId;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getMedio() {
        return medio;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }
}
