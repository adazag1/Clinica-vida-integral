package co.edu.ucentral.clinica.laboratorio;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

/** Examen dentro de una orden, con la trazabilidad técnica de su procesamiento (RFLC-008). */
@Entity
@Table(name = "orden_examen")
public class OrdenExamen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id")
    private OrdenLab orden;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "examen_id")
    private ExamenLab examen;

    private String equipo;

    @Column(name = "lote_reactivo")
    private String loteReactivo;

    private String calibracion;

    protected OrdenExamen() {
    }

    OrdenExamen(OrdenLab orden, ExamenLab examen) {
        this.orden = orden;
        this.examen = examen;
    }

    public void registrarTrazabilidad(String equipo, String loteReactivo, String calibracion) {
        this.equipo = equipo;
        this.loteReactivo = loteReactivo;
        this.calibracion = calibracion;
    }

    public boolean isTrazabilidadCompleta() {
        return equipo != null && loteReactivo != null && calibracion != null;
    }

    public Long getId() {
        return id;
    }

    public OrdenLab getOrden() {
        return orden;
    }

    public ExamenLab getExamen() {
        return examen;
    }

    public String getEquipo() {
        return equipo;
    }

    public String getLoteReactivo() {
        return loteReactivo;
    }

    public String getCalibracion() {
        return calibracion;
    }
}
