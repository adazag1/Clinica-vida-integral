package co.edu.ucentral.clinica.laboratorio;

import co.edu.ucentral.clinica.pacientes.Sexo;
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
 * RFLC-003 / RFLC-006 / RFLC-014: rango de referencia y límites críticos de un analito para un
 * sexo y un rango de edad. Lo ajusta el laboratorio desde la pantalla de configuración.
 * Un límite vacío significa "sin límite" por ese lado.
 */
@Entity
@Table(name = "rango_referencia")
public class RangoReferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analito_id")
    private Analito analito;

    /** null = aplica a ambos sexos. */
    @Enumerated(EnumType.STRING)
    private Sexo sexo;

    @Column(name = "edad_min", nullable = false)
    private int edadMin;

    @Column(name = "edad_max", nullable = false)
    private int edadMax;

    @Column(name = "ref_min", precision = 10, scale = 3)
    private BigDecimal refMin;

    @Column(name = "ref_max", precision = 10, scale = 3)
    private BigDecimal refMax;

    @Column(name = "critico_min", precision = 10, scale = 3)
    private BigDecimal criticoMin;

    @Column(name = "critico_max", precision = 10, scale = 3)
    private BigDecimal criticoMax;

    @Column(name = "modificado_en")
    private Instant modificadoEn;

    @Column(name = "modificado_por")
    private String modificadoPor;

    protected RangoReferencia() {
    }

    public boolean aplica(Sexo sexoPaciente, int edad) {
        return (sexo == null || sexo == sexoPaciente) && edad >= edadMin && edad <= edadMax;
    }

    public void actualizar(BigDecimal refMin, BigDecimal refMax, BigDecimal criticoMin, BigDecimal criticoMax,
                           String usuario) {
        this.refMin = refMin;
        this.refMax = refMax;
        this.criticoMin = criticoMin;
        this.criticoMax = criticoMax;
        this.modificadoPor = usuario;
        this.modificadoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Analito getAnalito() {
        return analito;
    }

    public Sexo getSexo() {
        return sexo;
    }

    public int getEdadMin() {
        return edadMin;
    }

    public int getEdadMax() {
        return edadMax;
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

    public Instant getModificadoEn() {
        return modificadoEn;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }
}
