package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
import co.edu.ucentral.clinica.pacientes.Paciente;
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

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

@Entity
@Table(name = "cita")
public class Cita extends EntidadAuditable {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profesional_id")
    private Profesional profesional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_cita_id")
    private TipoCita tipo;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCita estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrioridadCita prioridad;

    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_cancelacion")
    private MotivoCancelacion motivoCancelacion;

    @Column(name = "detalle_cancelacion")
    private String detalleCancelacion;

    @Column(name = "reprogramada_desde_id")
    private Long reprogramadaDesdeId;

    /** RFAGE-010: identifica la cita en el enlace que recibe el paciente. No es adivinable. */
    @Column(name = "token_publico", nullable = false, unique = true)
    private String tokenPublico;

    @Column(name = "confirmada_en")
    private Instant confirmadaEn;

    @Column(name = "recordatorio_enviado_en")
    private Instant recordatorioEnviadoEn;

    protected Cita() {
    }

    /** RFAGE-004: el fin lo fija la duración del tipo de cita. */
    public Cita(Paciente paciente, Profesional profesional, TipoCita tipo, Instant inicio, PrioridadCita prioridad,
                String motivo, Long reprogramadaDesdeId) {
        this.paciente = paciente;
        this.profesional = profesional;
        this.tipo = tipo;
        this.inicio = inicio;
        this.fin = inicio.plus(tipo.getDuracion());
        this.prioridad = prioridad;
        this.motivo = motivo;
        this.reprogramadaDesdeId = reprogramadaDesdeId;
        this.estado = EstadoCita.PROGRAMADA;
        byte[] bytes = new byte[24];
        ALEATORIO.nextBytes(bytes);
        this.tokenPublico = HexFormat.of().formatHex(bytes);
    }

    public void confirmar() {
        exigirPendiente("confirmar");
        estado = EstadoCita.CONFIRMADA;
        confirmadaEn = Instant.now();
    }

    public void cancelar(MotivoCancelacion motivo, String detalle) {
        exigirPendiente("cancelar");
        estado = EstadoCita.CANCELADA;
        motivoCancelacion = motivo;
        detalleCancelacion = detalle;
    }

    public void marcarReprogramada() {
        exigirPendiente("reprogramar");
        estado = EstadoCita.REPROGRAMADA;
    }

    /** Asistencia: solo desde la hora de inicio de la cita. */
    public void registrarAsistencia(boolean asistio) {
        exigirPendiente("registrar la asistencia de");
        if (Instant.now().isBefore(inicio)) {
            throw new ReglaNegocioException("CITA_FUTURA", "La asistencia se registra a partir de la hora de la cita.");
        }
        estado = asistio ? EstadoCita.ATENDIDA : EstadoCita.NO_ASISTIO;
    }

    /** Al firmarse la consulta que atendió esta cita (el paciente pudo llegar antes de la hora). */
    public void marcarAtendida() {
        if (estado.isPendiente()) {
            estado = EstadoCita.ATENDIDA;
        }
    }

    public void registrarRecordatorio() {
        recordatorioEnviadoEn = Instant.now();
    }

    private void exigirPendiente(String accion) {
        if (!estado.isPendiente()) {
            throw new ReglaNegocioException("CITA_NO_MODIFICABLE",
                    "No se puede " + accion + " una cita " + estado.getDescripcion().toLowerCase() + ".");
        }
    }

    public Long getId() {
        return id;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public Profesional getProfesional() {
        return profesional;
    }

    public TipoCita getTipo() {
        return tipo;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public EstadoCita getEstado() {
        return estado;
    }

    public PrioridadCita getPrioridad() {
        return prioridad;
    }

    public String getMotivo() {
        return motivo;
    }

    public MotivoCancelacion getMotivoCancelacion() {
        return motivoCancelacion;
    }

    public String getDetalleCancelacion() {
        return detalleCancelacion;
    }

    public Long getReprogramadaDesdeId() {
        return reprogramadaDesdeId;
    }

    public String getTokenPublico() {
        return tokenPublico;
    }

    public Instant getConfirmadaEn() {
        return confirmadaEn;
    }

    public Instant getRecordatorioEnviadoEn() {
        return recordatorioEnviadoEn;
    }
}
