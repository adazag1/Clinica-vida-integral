package co.edu.ucentral.clinica.admisiones;

import co.edu.ucentral.clinica.admisiones.dto.SignosVitales;
import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Clasificación de triage de un episodio de urgencias (RFADM-005 / RFADM-006). Si el paciente
 * se reclasifica, se actualiza el mismo registro: quién y cuándo quedan en modificado_por/en y
 * en la tabla de auditoría.
 */
@Entity
@Table(name = "triage")
public class Triage extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "episodio_id", nullable = false, unique = true)
    private Long episodioId;

    @Column(name = "frecuencia_cardiaca", nullable = false)
    private int frecuenciaCardiaca;

    @Column(name = "frecuencia_respiratoria", nullable = false)
    private int frecuenciaRespiratoria;

    @Column(name = "presion_sistolica", nullable = false)
    private int presionSistolica;

    @Column(name = "presion_diastolica", nullable = false)
    private int presionDiastolica;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal temperatura;

    @Column(name = "saturacion_oxigeno", nullable = false)
    private int saturacionOxigeno;

    @Column(nullable = false)
    private int glasgow;

    @Column(nullable = false)
    private int dolor;

    @Column(name = "sintoma_principal", nullable = false)
    private String sintomaPrincipal;

    @Column(name = "nivel_sugerido", nullable = false)
    private int nivelSugerido;

    @Column(name = "nivel_asignado", nullable = false)
    private int nivelAsignado;

    @Column(name = "justificacion_ajuste")
    private String justificacionAjuste;

    protected Triage() {
    }

    public Triage(Long episodioId) {
        this.episodioId = episodioId;
    }

    public void clasificar(SignosVitales s, String sintomaPrincipal, int nivelSugerido, int nivelAsignado,
                           String justificacionAjuste) {
        this.frecuenciaCardiaca = s.frecuenciaCardiaca();
        this.frecuenciaRespiratoria = s.frecuenciaRespiratoria();
        this.presionSistolica = s.presionSistolica();
        this.presionDiastolica = s.presionDiastolica();
        this.temperatura = s.temperatura();
        this.saturacionOxigeno = s.saturacionOxigeno();
        this.glasgow = s.glasgow();
        this.dolor = s.dolor();
        this.sintomaPrincipal = sintomaPrincipal;
        this.nivelSugerido = nivelSugerido;
        this.nivelAsignado = nivelAsignado;
        this.justificacionAjuste = nivelAsignado == nivelSugerido ? null : justificacionAjuste;
    }

    public SignosVitales getSignosVitales() {
        return new SignosVitales(frecuenciaCardiaca, frecuenciaRespiratoria, presionSistolica, presionDiastolica,
                temperatura, saturacionOxigeno, glasgow, dolor);
    }

    public Long getId() {
        return id;
    }

    public Long getEpisodioId() {
        return episodioId;
    }

    public String getSintomaPrincipal() {
        return sintomaPrincipal;
    }

    public int getNivelSugerido() {
        return nivelSugerido;
    }

    public int getNivelAsignado() {
        return nivelAsignado;
    }

    public String getJustificacionAjuste() {
        return justificacionAjuste;
    }
}
