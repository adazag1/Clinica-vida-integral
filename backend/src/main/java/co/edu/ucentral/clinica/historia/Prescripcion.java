package co.edu.ucentral.clinica.historia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Línea de la fórmula médica (tabla "Tratamiento / Fórmula médica" del mockup). */
@Entity
@Table(name = "prescripcion")
public class Prescripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consulta_id")
    private Consulta consulta;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "medicamento_id")
    private Medicamento medicamento;

    @Column(nullable = false)
    private String dosis;

    @Column(nullable = false)
    private String frecuencia;

    @Column(name = "duracion_dias", nullable = false)
    private int duracionDias;

    private String indicaciones;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "creado_por")
    private String creadoPor;

    protected Prescripcion() {
    }

    public Prescripcion(Medicamento medicamento, String dosis, String frecuencia, int duracionDias,
                        String indicaciones, String creadoPor) {
        this.medicamento = medicamento;
        this.dosis = dosis;
        this.frecuencia = frecuencia;
        this.duracionDias = duracionDias;
        this.indicaciones = indicaciones;
        this.creadoPor = creadoPor;
        this.creadoEn = Instant.now();
    }

    void asignar(Consulta consulta) {
        this.consulta = consulta;
    }

    public Long getId() {
        return id;
    }

    public Medicamento getMedicamento() {
        return medicamento;
    }

    public String getDosis() {
        return dosis;
    }

    public String getFrecuencia() {
        return frecuencia;
    }

    public int getDuracionDias() {
        return duracionDias;
    }

    public String getIndicaciones() {
        return indicaciones;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public String getCreadoPor() {
        return creadoPor;
    }
}
