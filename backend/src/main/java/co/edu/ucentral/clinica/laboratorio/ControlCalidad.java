package co.edu.ucentral.clinica.laboratorio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** RFLC-007: control de calidad del día. Sin uno aceptado, el área no puede liberar resultados. */
@Entity
@Table(name = "control_calidad")
public class ControlCalidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AreaLab area;

    @Column(nullable = false)
    private String equipo;

    @Column(nullable = false)
    private LocalDate fecha;

    /** Niveles de control corridos, p. ej. "Normal y patológico". */
    @Column(nullable = false)
    private String niveles;

    @Column(name = "lote_control", nullable = false)
    private String loteControl;

    @Column(nullable = false)
    private boolean aceptado;

    private String observacion;

    @Column(name = "registrado_por", nullable = false)
    private String registradoPor;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    protected ControlCalidad() {
    }

    public ControlCalidad(AreaLab area, String equipo, LocalDate fecha, String niveles, String loteControl,
                          boolean aceptado, String observacion, String registradoPor) {
        this.area = area;
        this.equipo = equipo;
        this.fecha = fecha;
        this.niveles = niveles;
        this.loteControl = loteControl;
        this.aceptado = aceptado;
        this.observacion = observacion;
        this.registradoPor = registradoPor;
        this.registradoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AreaLab getArea() {
        return area;
    }

    public String getEquipo() {
        return equipo;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getNiveles() {
        return niveles;
    }

    public String getLoteControl() {
        return loteControl;
    }

    public boolean isAceptado() {
        return aceptado;
    }

    public String getObservacion() {
        return observacion;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }
}
