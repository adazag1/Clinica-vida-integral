package co.edu.ucentral.clinica.admisiones;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.Regimen;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Episodio de atención: el "número de atención" que admisiones genera al recibir al paciente (RFADM-004). */
@Entity
@Table(name = "episodio")
public class Episodio extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_atencion", nullable = false)
    private TipoAtencion tipoAtencion;

    @Column(name = "motivo_consulta", nullable = false)
    private String motivoConsulta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoEpisodio estado;

    @Column(name = "fecha_ingreso", nullable = false)
    private Instant fechaIngreso;

    @Column(name = "fecha_listo")
    private Instant fechaListo;

    @Column(name = "fecha_cierre")
    private Instant fechaCierre;

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_cobertura", nullable = false)
    private EstadoCobertura estadoCobertura;

    private String aseguradora;

    @Column(name = "plan_cobertura")
    private String planCobertura;

    @Column(name = "numero_autorizacion")
    private String numeroAutorizacion;

    @Column(name = "observacion_cobertura")
    private String observacionCobertura;

    @Column(name = "cobertura_verificada_por")
    private String coberturaVerificadaPor;

    @Column(name = "cobertura_verificada_en")
    private Instant coberturaVerificadaEn;

    protected Episodio() {
    }

    public Episodio(String numero, Paciente paciente, TipoAtencion tipoAtencion, String motivoConsulta) {
        this.numero = numero;
        this.paciente = paciente;
        this.tipoAtencion = tipoAtencion;
        this.motivoConsulta = motivoConsulta;
        this.estado = EstadoEpisodio.EN_ADMISION;
        this.fechaIngreso = Instant.now();
        this.aseguradora = paciente.getAseguradora();
        this.estadoCobertura = paciente.getRegimen() == Regimen.PARTICULAR
                ? EstadoCobertura.PARTICULAR
                : EstadoCobertura.PENDIENTE;
    }

    /** RFADM-008: resultado de la verificación con la aseguradora. */
    public void registrarCobertura(EstadoCobertura resultado, String aseguradora, String plan, String autorizacion,
                                   String observacion, String usuario) {
        exigirEnAdmision();
        if (resultado == EstadoCobertura.PENDIENTE || resultado == EstadoCobertura.EXCEPCION_URGENCIA) {
            throw new ReglaNegocioException("COBERTURA_INVALIDA",
                    "Registre el resultado de la verificación: verificada, sin cobertura o pago particular.");
        }
        this.estadoCobertura = resultado;
        this.aseguradora = aseguradora;
        this.planCobertura = plan;
        this.numeroAutorizacion = autorizacion;
        this.observacionCobertura = observacion;
        this.coberturaVerificadaPor = usuario;
        this.coberturaVerificadaEn = Instant.now();
    }

    /**
     * RFADM-009: en urgencias vitales la atención no se retrasa por falta de autorización.
     * Queda registrada la justificación y quién la autorizó.
     */
    public void registrarExcepcionUrgencia(String justificacion, String usuario) {
        exigirEnAdmision();
        if (tipoAtencion != TipoAtencion.URGENCIAS) {
            throw new ReglaNegocioException("EXCEPCION_SOLO_URGENCIAS",
                    "La atención sin autorización solo aplica a urgencias.");
        }
        this.estadoCobertura = EstadoCobertura.EXCEPCION_URGENCIA;
        this.observacionCobertura = justificacion;
        this.coberturaVerificadaPor = usuario;
        this.coberturaVerificadaEn = Instant.now();
    }

    public void marcarListoParaConsulta() {
        exigirEnAdmision();
        this.estado = EstadoEpisodio.EN_ESPERA_CONSULTA;
        this.fechaListo = Instant.now();
    }

    /** El médico abre la consulta del paciente que estaba en espera (Fase 4). */
    public void iniciarAtencion() {
        if (estado != EstadoEpisodio.EN_ESPERA_CONSULTA && estado != EstadoEpisodio.EN_ATENCION) {
            throw new ReglaNegocioException("EPISODIO_NO_LISTO",
                    "El paciente aún no está listo para consulta (" + estado.getDescripcion().toLowerCase() + ").");
        }
        this.estado = EstadoEpisodio.EN_ATENCION;
    }

    /** Se cierra al firmar la consulta que lo atendió. */
    public void cerrar() {
        if (estado == EstadoEpisodio.EN_ATENCION) {
            this.estado = EstadoEpisodio.CERRADO;
            this.fechaCierre = Instant.now();
        }
    }

    public void anular(String motivo) {
        if (estado != EstadoEpisodio.EN_ADMISION && estado != EstadoEpisodio.EN_ESPERA_CONSULTA) {
            throw new ReglaNegocioException("EPISODIO_NO_ANULABLE",
                    "Solo se puede anular un episodio que aún no ha sido atendido.");
        }
        this.estado = EstadoEpisodio.ANULADO;
        this.motivoAnulacion = motivo;
        this.fechaCierre = Instant.now();
    }

    /** Cobertura, consentimientos y triage solo se registran mientras el paciente está en admisión. */
    void exigirEnAdmision() {
        if (estado != EstadoEpisodio.EN_ADMISION) {
            throw new ReglaNegocioException("EPISODIO_NO_EDITABLE",
                    "El episodio está " + estado.getDescripcion().toLowerCase() + " y ya no admite cambios de admisión.");
        }
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public TipoAtencion getTipoAtencion() {
        return tipoAtencion;
    }

    public String getMotivoConsulta() {
        return motivoConsulta;
    }

    public EstadoEpisodio getEstado() {
        return estado;
    }

    public Instant getFechaIngreso() {
        return fechaIngreso;
    }

    public Instant getFechaListo() {
        return fechaListo;
    }

    public Instant getFechaCierre() {
        return fechaCierre;
    }

    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    public EstadoCobertura getEstadoCobertura() {
        return estadoCobertura;
    }

    public String getAseguradora() {
        return aseguradora;
    }

    public String getPlanCobertura() {
        return planCobertura;
    }

    public String getNumeroAutorizacion() {
        return numeroAutorizacion;
    }

    public String getObservacionCobertura() {
        return observacionCobertura;
    }

    public String getCoberturaVerificadaPor() {
        return coberturaVerificadaPor;
    }

    public Instant getCoberturaVerificadaEn() {
        return coberturaVerificadaEn;
    }
}
