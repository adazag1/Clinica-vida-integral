package co.edu.ucentral.clinica.laboratorio;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Orden de laboratorio, desde que el médico la genera hasta que el resultado queda validado
 * (fases pre-analítica, analítica y post-analítica de la entrevista al laboratorio).
 */
@Entity
@Table(name = "orden_lab")
public class OrdenLab extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numero;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "consulta_id")
    private Long consultaId;

    @Column(nullable = false)
    private String medico;

    @Column(name = "medico_nombre", nullable = false)
    private String medicoNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrioridadLab prioridad;

    private String indicaciones;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoOrden estado;

    @Column(name = "ordenada_en", nullable = false)
    private Instant ordenadaEn;

    @Column(name = "muestra_tomada_en")
    private Instant muestraTomadaEn;

    @Column(name = "muestra_tomada_por")
    private String muestraTomadaPor;

    @Column(name = "muestra_recibida_en")
    private Instant muestraRecibidaEn;

    @Column(name = "proceso_en")
    private Instant procesoEn;

    @Column(name = "validada_en")
    private Instant validadaEn;

    @Column(name = "validada_por")
    private String validadaPor;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_rechazo")
    private MotivoLab.Rechazo motivoRechazo;

    @Column(name = "detalle_rechazo")
    private String detalleRechazo;

    @Column(name = "critico_contacto")
    private String criticoContacto;

    @Column(name = "critico_medio")
    private String criticoMedio;

    @Column(name = "critico_notificado_en")
    private Instant criticoNotificadoEn;

    @Column(name = "tat_alertado", nullable = false)
    private boolean tatAlertado;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrdenExamen> examenes = new ArrayList<>();

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ResultadoLab> resultados = new ArrayList<>();

    protected OrdenLab() {
    }

    public OrdenLab(String numero, Long pacienteId, Long consultaId, String medico, String medicoNombre,
                    PrioridadLab prioridad, String indicaciones, List<ExamenLab> examenes) {
        this.numero = numero;
        this.pacienteId = pacienteId;
        this.consultaId = consultaId;
        this.medico = medico;
        this.medicoNombre = medicoNombre;
        this.prioridad = prioridad;
        this.indicaciones = indicaciones;
        this.estado = EstadoOrden.ORDENADA;
        this.ordenadaEn = Instant.now();
        examenes.forEach(e -> this.examenes.add(new OrdenExamen(this, e)));
    }

    /** Pre-analítica: la muestra llega al laboratorio identificada. */
    public void recibirMuestra(Instant tomadaEn, String tomadaPor) {
        exigir(Set.of(EstadoOrden.ORDENADA), "recibir la muestra");
        this.muestraTomadaEn = tomadaEn;
        this.muestraTomadaPor = tomadaPor;
        this.muestraRecibidaEn = Instant.now();
        this.estado = EstadoOrden.MUESTRA_RECIBIDA;
    }

    public void rechazarMuestra(MotivoLab.Rechazo motivo, String detalle) {
        exigir(Set.of(EstadoOrden.MUESTRA_RECIBIDA, EstadoOrden.EN_PROCESO), "rechazar la muestra");
        this.estado = EstadoOrden.RECHAZADA;
        this.motivoRechazo = motivo;
        this.detalleRechazo = detalle;
        this.resultados.clear();
    }

    /** Tras un rechazo se pide una muestra nueva: la orden vuelve a pendiente, con el rechazo en el historial. */
    public void solicitarNuevaMuestra() {
        exigir(Set.of(EstadoOrden.RECHAZADA), "solicitar una nueva muestra");
        this.estado = EstadoOrden.ORDENADA;
        this.muestraTomadaEn = null;
        this.muestraTomadaPor = null;
        this.muestraRecibidaEn = null;
        this.procesoEn = null;
        this.motivoRechazo = null;
        this.detalleRechazo = null;
    }

    /** Analítica: al cargar resultados la orden queda en proceso. */
    public void marcarEnProceso() {
        exigir(Set.of(EstadoOrden.MUESTRA_RECIBIDA, EstadoOrden.EN_PROCESO), "registrar resultados");
        if (estado == EstadoOrden.MUESTRA_RECIBIDA) {
            this.estado = EstadoOrden.EN_PROCESO;
            this.procesoEn = Instant.now();
        }
    }

    public void validar(String usuario, String contactoCritico, String medioCritico) {
        exigir(Set.of(EstadoOrden.EN_PROCESO), "validar");
        this.estado = EstadoOrden.VALIDADA;
        this.validadaEn = Instant.now();
        this.validadaPor = usuario;
        if (contactoCritico != null) {
            this.criticoContacto = contactoCritico;
            this.criticoMedio = medioCritico;
            this.criticoNotificadoEn = validadaEn;
        }
    }

    /** RFLC-012: la orden abierta superó el tiempo esperado de su examen más lento. */
    public boolean tatVencido(Instant ahora) {
        return estado.isAbierta() && ahora.isAfter(limiteTat());
    }

    public Instant limiteTat() {
        Duration mayor = examenes.stream().map(e -> e.getExamen().tat(prioridad)).max(Duration::compareTo)
                .orElse(Duration.ZERO);
        return ordenadaEn.plus(mayor);
    }

    public void marcarTatAlertado() {
        this.tatAlertado = true;
    }

    private void exigir(Set<EstadoOrden> permitidos, String accion) {
        if (!permitidos.contains(estado)) {
            throw new ReglaNegocioException("ORDEN_ESTADO_INVALIDO",
                    "No se puede " + accion + ": la orden está " + estado.getDescripcion().toLowerCase() + ".");
        }
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public Long getConsultaId() {
        return consultaId;
    }

    public String getMedico() {
        return medico;
    }

    public String getMedicoNombre() {
        return medicoNombre;
    }

    public PrioridadLab getPrioridad() {
        return prioridad;
    }

    public String getIndicaciones() {
        return indicaciones;
    }

    public EstadoOrden getEstado() {
        return estado;
    }

    public Instant getOrdenadaEn() {
        return ordenadaEn;
    }

    public Instant getMuestraTomadaEn() {
        return muestraTomadaEn;
    }

    public String getMuestraTomadaPor() {
        return muestraTomadaPor;
    }

    public Instant getMuestraRecibidaEn() {
        return muestraRecibidaEn;
    }

    public Instant getProcesoEn() {
        return procesoEn;
    }

    public Instant getValidadaEn() {
        return validadaEn;
    }

    public String getValidadaPor() {
        return validadaPor;
    }

    public MotivoLab.Rechazo getMotivoRechazo() {
        return motivoRechazo;
    }

    public String getDetalleRechazo() {
        return detalleRechazo;
    }

    public String getCriticoContacto() {
        return criticoContacto;
    }

    public String getCriticoMedio() {
        return criticoMedio;
    }

    public Instant getCriticoNotificadoEn() {
        return criticoNotificadoEn;
    }

    public boolean isTatAlertado() {
        return tatAlertado;
    }

    public List<OrdenExamen> getExamenes() {
        return examenes;
    }

    public List<ResultadoLab> getResultados() {
        return resultados;
    }
}
