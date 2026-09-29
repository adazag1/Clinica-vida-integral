package co.edu.ucentral.clinica.enfermeria;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
import co.edu.ucentral.clinica.historia.Medicamento;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orden médica que ejecuta enfermería (RFENF-002): un medicamento con horario, una dieta o un
 * cuidado. Vencida o suspendida ya no se puede ejecutar (RFENF-009).
 */
@Entity
@Table(name = "orden_clinica")
public class OrdenClinica extends EntidadAuditable {

    public enum Tipo {
        MEDICAMENTO("Medicamento"),
        DIETA("Dieta"),
        CUIDADO("Cuidado de enfermería");

        private final String descripcion;

        Tipo(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    public enum Estado {
        ACTIVA, SUSPENDIDA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "consulta_id")
    private Long consultaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Column(nullable = false)
    private String descripcion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medicamento_id")
    private Medicamento medicamento;

    private String dosis;

    private String via;

    @Column(name = "frecuencia_horas")
    private Integer frecuenciaHoras;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    @Column(name = "alto_riesgo", nullable = false)
    private boolean altoRiesgo;

    private String indicaciones;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(name = "motivo_suspension")
    private String motivoSuspension;

    @Column(name = "suspendida_por")
    private String suspendidaPor;

    @Column(name = "suspendida_en")
    private Instant suspendidaEn;

    @Column(nullable = false)
    private String medico;

    @Column(name = "medico_nombre", nullable = false)
    private String medicoNombre;

    protected OrdenClinica() {
    }

    public OrdenClinica(Long pacienteId, Long consultaId, Tipo tipo, String descripcion, Medicamento medicamento,
                        String dosis, String via, Integer frecuenciaHoras, Instant inicio, Instant fin, boolean altoRiesgo,
                        String indicaciones, String medico, String medicoNombre) {
        this.pacienteId = pacienteId;
        this.consultaId = consultaId;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.medicamento = medicamento;
        this.dosis = dosis;
        this.via = via;
        this.frecuenciaHoras = frecuenciaHoras;
        this.inicio = inicio;
        this.fin = fin;
        this.altoRiesgo = altoRiesgo;
        this.indicaciones = indicaciones;
        this.medico = medico;
        this.medicoNombre = medicoNombre;
        this.estado = Estado.ACTIVA;
    }

    public void suspender(String motivo, String usuario) {
        if (estado == Estado.SUSPENDIDA) {
            throw new ReglaNegocioException("ORDEN_SUSPENDIDA", "La orden ya estaba suspendida.");
        }
        estado = Estado.SUSPENDIDA;
        motivoSuspension = motivo;
        suspendidaPor = usuario;
        suspendidaEn = Instant.now();
    }

    /** Activa y dentro de su vigencia: se puede ejecutar. */
    public boolean isVigente(Instant ahora) {
        return estado == Estado.ACTIVA && !ahora.isAfter(fin);
    }

    /** Qué muestra enfermería: activa, vencida (pasó su fin) o suspendida. */
    public String situacion(Instant ahora) {
        if (estado == Estado.SUSPENDIDA) return "SUSPENDIDA";
        return ahora.isAfter(fin) ? "VENCIDA" : "ACTIVA";
    }

    /**
     * Horas programadas dentro de [desde, hasta]: cada {@code frecuenciaHoras} desde el inicio y
     * antes del fin. Una orden sin frecuencia es de una sola vez, al inicio. Una dieta no se programa.
     */
    public List<Instant> horarios(Instant desde, Instant hasta) {
        List<Instant> horas = new ArrayList<>();
        if (tipo == Tipo.DIETA) {
            return horas;
        }
        if (frecuenciaHoras == null) {
            if (!inicio.isBefore(desde) && !inicio.isAfter(hasta)) horas.add(inicio);
            return horas;
        }
        Duration paso = Duration.ofHours(frecuenciaHoras);
        Instant t = inicio;
        if (t.isBefore(desde)) {
            long saltos = Duration.between(inicio, desde).toMinutes() / paso.toMinutes();
            t = inicio.plus(paso.multipliedBy(saltos));
        }
        for (; !t.isAfter(hasta) && t.isBefore(fin); t = t.plus(paso)) {
            if (!t.isBefore(desde)) horas.add(t);
        }
        return horas;
    }

    /** true si {@code hora} es exactamente una de las horas programadas de la orden. */
    public boolean esHoraProgramada(Instant hora) {
        return horarios(hora, hora).contains(hora);
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

    public Tipo getTipo() {
        return tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Medicamento getMedicamento() {
        return medicamento;
    }

    public String getDosis() {
        return dosis;
    }

    public String getVia() {
        return via;
    }

    public Integer getFrecuenciaHoras() {
        return frecuenciaHoras;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public boolean isAltoRiesgo() {
        return altoRiesgo;
    }

    public String getIndicaciones() {
        return indicaciones;
    }

    public Estado getEstado() {
        return estado;
    }

    public String getMotivoSuspension() {
        return motivoSuspension;
    }

    public String getSuspendidaPor() {
        return suspendidaPor;
    }

    public Instant getSuspendidaEn() {
        return suspendidaEn;
    }

    public String getMedico() {
        return medico;
    }

    public String getMedicoNombre() {
        return medicoNombre;
    }
}
