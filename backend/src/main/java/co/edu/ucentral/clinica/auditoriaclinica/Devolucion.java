package co.edu.ucentral.clinica.auditoriaclinica;

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

/** RFAU-004: la cuenta vuelve a quien debe corregirla, con motivo; se mide cuánto tarda la corrección. */
@Entity
@Table(name = "devolucion")
public class Devolucion {

    public enum Motivo {
        HISTORIA_INCOMPLETA("Historia clínica incompleta o sin firmar"),
        SIN_ORDEN_MEDICA("Procedimiento sin orden médica"),
        SOPORTE_FALTANTE("Falta soporte del cargo"),
        CODIFICACION("Codificación inconsistente"),
        AUTORIZACION("Falta autorización"),
        PERTINENCIA("Pertinencia clínica en duda"),
        OTRO("Otro");

        private final String descripcion;

        Motivo(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    /** A quién le toca corregir. */
    public enum Destino {
        FACTURACION, MEDICO, ADMISIONES
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Motivo motivo;

    @Column(nullable = false)
    private String detalle;

    @Enumerated(EnumType.STRING)
    @Column(name = "dirigida_a", nullable = false)
    private Destino dirigidaA;

    @Column(name = "devuelta_por", nullable = false)
    private String devueltaPor;

    @Column(name = "devuelta_en", nullable = false)
    private Instant devueltaEn;

    @Column(name = "corregida_en")
    private Instant corregidaEn;

    @Column(name = "corregida_por")
    private String corregidaPor;

    private String respuesta;

    protected Devolucion() {
    }

    Devolucion(Long cuentaId, Motivo motivo, String detalle, Destino dirigidaA, String usuario) {
        this.cuentaId = cuentaId;
        this.motivo = motivo;
        this.detalle = detalle;
        this.dirigidaA = dirigidaA;
        this.devueltaPor = usuario;
        this.devueltaEn = Instant.now();
    }

    void corregir(String usuario, String respuesta) {
        this.corregidaEn = Instant.now();
        this.corregidaPor = usuario;
        this.respuesta = respuesta;
    }

    public Duration tiempoCorreccion() {
        return corregidaEn == null ? null : Duration.between(devueltaEn, corregidaEn);
    }

    public Long getId() {
        return id;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public Motivo getMotivo() {
        return motivo;
    }

    public String getDetalle() {
        return detalle;
    }

    public Destino getDirigidaA() {
        return dirigidaA;
    }

    public String getDevueltaPor() {
        return devueltaPor;
    }

    public Instant getDevueltaEn() {
        return devueltaEn;
    }

    public Instant getCorregidaEn() {
        return corregidaEn;
    }

    public String getCorregidaPor() {
        return corregidaPor;
    }

    public String getRespuesta() {
        return respuesta;
    }
}
