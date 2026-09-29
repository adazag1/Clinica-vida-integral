package co.edu.ucentral.clinica.documentos;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** RFGAC-008: pedido de un documento (o del expediente completo) al archivo clínico. */
@Entity
@Table(name = "solicitud_archivo")
public class SolicitudArchivo {

    public enum Estado {
        PENDIENTE("Pendiente"),
        ENTREGADA("Entregada"),
        DEVUELTA("Devuelta al archivo"),
        RECHAZADA("Rechazada");

        private final String descripcion;

        Estado(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "documento_id")
    private Long documentoId;

    @Column(nullable = false)
    private String solicitante;

    @Column(name = "solicitante_nombre", nullable = false)
    private String solicitanteNombre;

    @Column(nullable = false)
    private String motivo;

    @Column(nullable = false)
    private boolean urgente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(name = "solicitada_en", nullable = false)
    private Instant solicitadaEn;

    @Column(name = "atendida_por")
    private String atendidaPor;

    @Column(name = "entregada_en")
    private Instant entregadaEn;

    @Column(name = "devuelta_en")
    private Instant devueltaEn;

    private String respuesta;

    protected SolicitudArchivo() {
    }

    SolicitudArchivo(Long pacienteId, Long documentoId, String solicitante, String solicitanteNombre, String motivo,
                     boolean urgente) {
        this.pacienteId = pacienteId;
        this.documentoId = documentoId;
        this.solicitante = solicitante;
        this.solicitanteNombre = solicitanteNombre;
        this.motivo = motivo;
        this.urgente = urgente;
        this.estado = Estado.PENDIENTE;
        this.solicitadaEn = Instant.now();
    }

    void entregar(String usuario, String respuesta) {
        exigir(Estado.PENDIENTE);
        estado = Estado.ENTREGADA;
        atendidaPor = usuario;
        entregadaEn = Instant.now();
        this.respuesta = respuesta;
    }

    void rechazar(String usuario, String respuesta) {
        exigir(Estado.PENDIENTE);
        estado = Estado.RECHAZADA;
        atendidaPor = usuario;
        entregadaEn = Instant.now();
        this.respuesta = respuesta;
    }

    void registrarDevolucion() {
        exigir(Estado.ENTREGADA);
        estado = Estado.DEVUELTA;
        devueltaEn = Instant.now();
    }

    private void exigir(Estado esperado) {
        if (estado != esperado) {
            throw new ReglaNegocioException("SOLICITUD_ESTADO_INVALIDO", "La solicitud está " + estado.getDescripcion().toLowerCase() + ".");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public Long getDocumentoId() {
        return documentoId;
    }

    public String getSolicitante() {
        return solicitante;
    }

    public String getSolicitanteNombre() {
        return solicitanteNombre;
    }

    public String getMotivo() {
        return motivo;
    }

    public boolean isUrgente() {
        return urgente;
    }

    public Estado getEstado() {
        return estado;
    }

    public Instant getSolicitadaEn() {
        return solicitadaEn;
    }

    public String getAtendidaPor() {
        return atendidaPor;
    }

    public Instant getEntregadaEn() {
        return entregadaEn;
    }

    public Instant getDevueltaEn() {
        return devueltaEn;
    }

    public String getRespuesta() {
        return respuesta;
    }
}
