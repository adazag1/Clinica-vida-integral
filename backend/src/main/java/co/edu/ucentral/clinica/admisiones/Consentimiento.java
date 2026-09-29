package co.edu.ucentral.clinica.admisiones;

import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * RFADM-007: la fecha, hora y el responsable que lo registró quedan en creado_en / creado_por.
 * {@code otorgadoPor} es quien firma: el paciente o su acompañante.
 */
@Entity
@Table(name = "consentimiento")
public class Consentimiento extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "episodio_id", nullable = false)
    private Long episodioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoConsentimiento tipo;

    @Column(name = "otorgado_por", nullable = false)
    private String otorgadoPor;

    private String parentesco;

    @Column(nullable = false)
    private boolean aceptado;

    private String observaciones;

    protected Consentimiento() {
    }

    public Consentimiento(Long episodioId, TipoConsentimiento tipo, String otorgadoPor, String parentesco,
                          boolean aceptado, String observaciones) {
        this.episodioId = episodioId;
        this.tipo = tipo;
        this.otorgadoPor = otorgadoPor;
        this.parentesco = parentesco;
        this.aceptado = aceptado;
        this.observaciones = observaciones;
    }

    public Long getId() {
        return id;
    }

    public Long getEpisodioId() {
        return episodioId;
    }

    public TipoConsentimiento getTipo() {
        return tipo;
    }

    public String getOtorgadoPor() {
        return otorgadoPor;
    }

    public String getParentesco() {
        return parentesco;
    }

    public boolean isAceptado() {
        return aceptado;
    }

    public String getObservaciones() {
        return observaciones;
    }
}
