package co.edu.ucentral.clinica.historia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Toma de signos vitales (RFMT-010). Los campos no medidos quedan vacíos. */
@Entity
@Table(name = "signos_vitales")
public class RegistroSignos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "consulta_id")
    private Long consultaId;

    @Column(name = "presion_sistolica")
    private Integer presionSistolica;

    @Column(name = "presion_diastolica")
    private Integer presionDiastolica;

    @Column(name = "frecuencia_cardiaca")
    private Integer frecuenciaCardiaca;

    @Column(name = "frecuencia_respiratoria")
    private Integer frecuenciaRespiratoria;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperatura;

    @Column(name = "saturacion_oxigeno")
    private Integer saturacionOxigeno;

    @Column(precision = 5, scale = 1)
    private BigDecimal peso;

    private Integer talla;

    @Column(name = "registrado_por", nullable = false)
    private String registradoPor;

    @Column(nullable = false)
    private Instant fecha;

    protected RegistroSignos() {
    }

    public RegistroSignos(Long pacienteId, Long consultaId, Integer presionSistolica, Integer presionDiastolica,
                          Integer frecuenciaCardiaca, Integer frecuenciaRespiratoria, BigDecimal temperatura,
                          Integer saturacionOxigeno, BigDecimal peso, Integer talla, String registradoPor) {
        this.pacienteId = pacienteId;
        this.consultaId = consultaId;
        this.presionSistolica = presionSistolica;
        this.presionDiastolica = presionDiastolica;
        this.frecuenciaCardiaca = frecuenciaCardiaca;
        this.frecuenciaRespiratoria = frecuenciaRespiratoria;
        this.temperatura = temperatura;
        this.saturacionOxigeno = saturacionOxigeno;
        this.peso = peso;
        this.talla = talla;
        this.registradoPor = registradoPor;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public Long getConsultaId() {
        return consultaId;
    }

    public Integer getPresionSistolica() {
        return presionSistolica;
    }

    public Integer getPresionDiastolica() {
        return presionDiastolica;
    }

    public Integer getFrecuenciaCardiaca() {
        return frecuenciaCardiaca;
    }

    public Integer getFrecuenciaRespiratoria() {
        return frecuenciaRespiratoria;
    }

    public BigDecimal getTemperatura() {
        return temperatura;
    }

    public Integer getSaturacionOxigeno() {
        return saturacionOxigeno;
    }

    public BigDecimal getPeso() {
        return peso;
    }

    public Integer getTalla() {
        return talla;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public Instant getFecha() {
        return fecha;
    }
}
