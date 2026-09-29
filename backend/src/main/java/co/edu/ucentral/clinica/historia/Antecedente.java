package co.edu.ucentral.clinica.historia;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
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
 * Antecedente de la historia clínica. No se borra: se inactiva con motivo (p. ej. una alergia
 * descartada o un medicamento suspendido), para conservar la trazabilidad (RFMT-008).
 */
@Entity
@Table(name = "antecedente")
public class Antecedente extends EntidadAuditable {

    public enum Tipo {
        ALERGIA("Alergia"),
        MEDICAMENTO_ACTUAL("Medicamento actual"),
        PATOLOGICO("Enfermedad crónica"),
        QUIRURGICO("Quirúrgico"),
        FAMILIAR("Familiar"),
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
        LEVE, MODERADA, SEVERA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    /** En una alergia: la sustancia ("Penicilina", "AINE", "Sulfas"). */
    @Column(nullable = false)
    private String descripcion;

    /** Reacción, dosis, fecha de la cirugía, parentesco... */
    private String detalle;

    @Enumerated(EnumType.STRING)
    private Severidad severidad;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "motivo_inactivacion")
    private String motivoInactivacion;

    protected Antecedente() {
    }

    public Antecedente(Long pacienteId, Tipo tipo, String descripcion, String detalle, Severidad severidad) {
        this.pacienteId = pacienteId;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.detalle = detalle;
        this.severidad = severidad;
    }

    public void inactivar(String motivo) {
        if (!activo) {
            throw new ReglaNegocioException("ANTECEDENTE_INACTIVO", "El antecedente ya estaba inactivo.");
        }
        activo = false;
        motivoInactivacion = motivo;
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

    public String getDescripcion() {
        return descripcion;
    }

    public String getDetalle() {
        return detalle;
    }

    public Severidad getSeveridad() {
        return severidad;
    }

    public boolean isActivo() {
        return activo;
    }

    public String getMotivoInactivacion() {
        return motivoInactivacion;
    }
}
