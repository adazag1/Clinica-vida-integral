package co.edu.ucentral.clinica.facturacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** RFRC-006: condición del contrato sobre un servicio. */
@Entity
@Table(name = "contrato_regla")
public class ContratoRegla {

    public enum Tipo {
        TARIFA_PACTADA("Tarifa pactada"),
        EXCLUSION("Excluido: lo paga el paciente"),
        TOPE_CANTIDAD("Tope de cantidad por cuenta"),
        AUTORIZACION("Exige autorización");

        private final String descripcion;

        Tipo(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "servicio_id")
    private ServicioFacturable servicio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    private BigDecimal valor;

    @Column(name = "cantidad_max")
    private Integer cantidadMax;

    protected ContratoRegla() {
    }

    ContratoRegla(Contrato contrato, ServicioFacturable servicio, Tipo tipo, BigDecimal valor, Integer cantidadMax) {
        this.contrato = contrato;
        this.servicio = servicio;
        this.tipo = tipo;
        this.valor = valor;
        this.cantidadMax = cantidadMax;
    }

    public Long getId() {
        return id;
    }

    public ServicioFacturable getServicio() {
        return servicio;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Integer getCantidadMax() {
        return cantidadMax;
    }
}
