package co.edu.ucentral.clinica.laboratorio;

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
import java.time.Instant;

/**
 * Resultado de un analito. Guarda el rango con que se interpretó (por si luego se reconfigura)
 * y el valor previo del delta check.
 */
@Entity
@Table(name = "resultado_lab")
public class ResultadoLab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id")
    private OrdenLab orden;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "analito_id")
    private Analito analito;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Interpretacion interpretacion;

    @Column(name = "ref_min", precision = 10, scale = 3)
    private BigDecimal refMin;

    @Column(name = "ref_max", precision = 10, scale = 3)
    private BigDecimal refMax;

    @Column(name = "critico_min", precision = 10, scale = 3)
    private BigDecimal criticoMin;

    @Column(name = "critico_max", precision = 10, scale = 3)
    private BigDecimal criticoMax;

    @Column(name = "valor_previo", precision = 10, scale = 3)
    private BigDecimal valorPrevio;

    @Column(name = "fecha_previo")
    private Instant fechaPrevio;

    @Column(name = "delta_porcentaje", precision = 8, scale = 1)
    private BigDecimal deltaPorcentaje;

    @Column(name = "alerta_delta", nullable = false)
    private boolean alertaDelta;

    @Column(nullable = false)
    private int repeticiones;

    @Column(name = "registrado_por", nullable = false)
    private String registradoPor;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    protected ResultadoLab() {
    }

    public ResultadoLab(OrdenLab orden, Analito analito) {
        this.orden = orden;
        this.analito = analito;
    }

    /** Registra (o corrige antes de validar) el valor con su interpretación y delta check. */
    public void registrar(BigDecimal valor, Interpretacion interpretacion, RangoReferencia rango, BigDecimal valorPrevio,
                          Instant fechaPrevio, BigDecimal delta, boolean alertaDelta, String usuario) {
        this.valor = valor;
        this.interpretacion = interpretacion;
        this.refMin = rango == null ? null : rango.getRefMin();
        this.refMax = rango == null ? null : rango.getRefMax();
        this.criticoMin = rango == null ? null : rango.getCriticoMin();
        this.criticoMax = rango == null ? null : rango.getCriticoMax();
        this.valorPrevio = valorPrevio;
        this.fechaPrevio = fechaPrevio;
        this.deltaPorcentaje = delta;
        this.alertaDelta = alertaDelta;
        this.registradoPor = usuario;
        this.registradoEn = Instant.now();
    }

    public void contarRepeticion() {
        repeticiones++;
    }

    public Long getId() {
        return id;
    }

    public OrdenLab getOrden() {
        return orden;
    }

    public Analito getAnalito() {
        return analito;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Interpretacion getInterpretacion() {
        return interpretacion;
    }

    public BigDecimal getRefMin() {
        return refMin;
    }

    public BigDecimal getRefMax() {
        return refMax;
    }

    public BigDecimal getCriticoMin() {
        return criticoMin;
    }

    public BigDecimal getCriticoMax() {
        return criticoMax;
    }

    public BigDecimal getValorPrevio() {
        return valorPrevio;
    }

    public Instant getFechaPrevio() {
        return fechaPrevio;
    }

    public BigDecimal getDeltaPorcentaje() {
        return deltaPorcentaje;
    }

    public boolean isAlertaDelta() {
        return alertaDelta;
    }

    public int getRepeticiones() {
        return repeticiones;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }
}
