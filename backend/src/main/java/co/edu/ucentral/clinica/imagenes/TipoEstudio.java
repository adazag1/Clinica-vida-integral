package co.edu.ucentral.clinica.imagenes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/** Tipo de estudio con su tiempo objetivo por prioridad (RFRAD-004) y su plantilla de informe (RFRAD-006). */
@Entity
@Table(name = "tipo_estudio")
public class TipoEstudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Modalidad modalidad;

    @Column(nullable = false)
    private String region;

    @Column(name = "tat_urgente_min", nullable = false)
    private int tatUrgenteMin;

    @Column(name = "tat_hospitalizado_min", nullable = false)
    private int tatHospitalizadoMin;

    @Column(name = "tat_ambulatorio_min", nullable = false)
    private int tatAmbulatorioMin;

    @Column(name = "plantilla_hallazgos")
    private String plantillaHallazgos;

    @Column(name = "plantilla_impresion")
    private String plantillaImpresion;

    @Column(name = "plantilla_recomendaciones")
    private String plantillaRecomendaciones;

    @Column(name = "modificado_en")
    private Instant modificadoEn;

    @Column(name = "modificado_por")
    private String modificadoPor;

    protected TipoEstudio() {
    }

    public Duration tat(PrioridadEstudio p) {
        return Duration.ofMinutes(switch (p) {
            case URGENTE -> tatUrgenteMin;
            case HOSPITALIZADO -> tatHospitalizadoMin;
            case AMBULATORIO -> tatAmbulatorioMin;
        });
    }

    void configurar(int urgente, int hospitalizado, int ambulatorio, String hallazgos, String impresion,
                    String recomendaciones, String usuario) {
        this.tatUrgenteMin = urgente;
        this.tatHospitalizadoMin = hospitalizado;
        this.tatAmbulatorioMin = ambulatorio;
        this.plantillaHallazgos = hallazgos;
        this.plantillaImpresion = impresion;
        this.plantillaRecomendaciones = recomendaciones;
        this.modificadoPor = usuario;
        this.modificadoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public Modalidad getModalidad() {
        return modalidad;
    }

    public String getRegion() {
        return region;
    }

    public int getTatUrgenteMin() {
        return tatUrgenteMin;
    }

    public int getTatHospitalizadoMin() {
        return tatHospitalizadoMin;
    }

    public int getTatAmbulatorioMin() {
        return tatAmbulatorioMin;
    }

    public String getPlantillaHallazgos() {
        return plantillaHallazgos;
    }

    public String getPlantillaImpresion() {
        return plantillaImpresion;
    }

    public String getPlantillaRecomendaciones() {
        return plantillaRecomendaciones;
    }

    public Instant getModificadoEn() {
        return modificadoEn;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }
}
