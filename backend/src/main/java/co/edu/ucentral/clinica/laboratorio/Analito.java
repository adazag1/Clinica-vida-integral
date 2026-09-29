package co.edu.ucentral.clinica.laboratorio;

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
import java.util.ArrayList;
import java.util.List;

/** Lo que se mide (hemoglobina, potasio...), con su unidad y sus rangos por sexo y edad. */
@Entity
@Table(name = "analito")
public class Analito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "examen_id")
    private ExamenLab examen;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String unidad;

    @Column(nullable = false)
    private int decimales;

    @Column(nullable = false)
    private int orden;

    /** RFLC-004: cambio porcentual contra el resultado anterior que exige revisión. */
    @Column(name = "delta_porcentaje", precision = 5, scale = 1)
    private BigDecimal deltaPorcentaje;

    @OneToMany(mappedBy = "analito")
    @OrderBy("sexo, edadMin")
    private List<RangoReferencia> rangos = new ArrayList<>();

    protected Analito() {
    }

    public Long getId() {
        return id;
    }

    public ExamenLab getExamen() {
        return examen;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getUnidad() {
        return unidad;
    }

    public int getDecimales() {
        return decimales;
    }

    public int getOrden() {
        return orden;
    }

    public BigDecimal getDeltaPorcentaje() {
        return deltaPorcentaje;
    }

    public List<RangoReferencia> getRangos() {
        return rangos;
    }
}
