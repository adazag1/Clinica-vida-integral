package co.edu.ucentral.clinica.enfermeria;

import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * RFENF-007: evento adverso o incidente, clasificado según la política de seguridad del paciente
 * (incidente sin daño vs. evento adverso con daño leve, moderado, grave o mortal).
 */
@Entity
@Table(name = "evento_adverso")
public class EventoAdverso extends EntidadAuditable {

    public enum Tipo {
        CAIDA("Caída"),
        ERROR_MEDICACION("Error de medicación"),
        REACCION_ADVERSA_MEDICAMENTO("Reacción adversa a medicamento"),
        LESION_POR_PRESION("Lesión por presión"),
        FLEBITIS("Flebitis o extravasación"),
        INFECCION_ASOCIADA("Infección asociada a la atención"),
        IDENTIFICACION_INCORRECTA("Identificación incorrecta del paciente"),
        OTRO("Otro");

        private final String descripcion;

        Tipo(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    public enum Severidad {
        INCIDENTE_SIN_DANO("Incidente sin daño"),
        LEVE("Daño leve"),
        MODERADO("Daño moderado"),
        GRAVE("Daño grave"),
        MORTAL("Muerte");

        private final String descripcion;

        Severidad(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }

        public boolean isGrave() {
            return this == GRAVE || this == MORTAL;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severidad severidad;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    private String lugar;

    @Column(nullable = false)
    private String descripcion;

    @Column(name = "acciones_inmediatas", nullable = false)
    private String accionesInmediatas;

    @Column(name = "medico_notificado")
    private String medicoNotificado;

    @Column(name = "orden_id")
    private Long ordenId;

    protected EventoAdverso() {
    }

    public EventoAdverso(Long pacienteId, Tipo tipo, Severidad severidad, Instant ocurridoEn, String lugar,
                         String descripcion, String accionesInmediatas, String medicoNotificado, Long ordenId) {
        this.pacienteId = pacienteId;
        this.tipo = tipo;
        this.severidad = severidad;
        this.ocurridoEn = ocurridoEn;
        this.lugar = lugar;
        this.descripcion = descripcion;
        this.accionesInmediatas = accionesInmediatas;
        this.medicoNotificado = medicoNotificado;
        this.ordenId = ordenId;
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public Severidad getSeveridad() {
        return severidad;
    }

    public Instant getOcurridoEn() {
        return ocurridoEn;
    }

    public String getLugar() {
        return lugar;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getAccionesInmediatas() {
        return accionesInmediatas;
    }

    public String getMedicoNotificado() {
        return medicoNotificado;
    }

    public Long getOrdenId() {
        return ordenId;
    }
}
