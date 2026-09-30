package co.edu.ucentral.clinica.facturacion;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Cuenta de una atención: acumula los cargos y recorre auditoría, factura, glosas y pago (RFRC-007). */
@Entity
@Table(name = "cuenta")
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numero;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "episodio_id")
    private Long episodioId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    @Column(nullable = false)
    private String aseguradora;

    @Column(name = "numero_autorizacion")
    private String numeroAutorizacion;

    /** Atención de urgencias: no exige autorización previa (se informa a la aseguradora). */
    @Column(nullable = false)
    private boolean urgencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCuenta estado;

    @Column(name = "abierta_en", nullable = false)
    private Instant abiertaEn;

    @Column(name = "enviada_auditoria_en")
    private Instant enviadaAuditoriaEn;

    @Column(name = "auditada_en")
    private Instant auditadaEn;

    @Column(name = "auditada_por")
    private String auditadaPor;

    @Column(name = "facturada_en")
    private Instant facturadaEn;

    @Column(name = "cerrada_en")
    private Instant cerradaEn;

    @OneToMany(mappedBy = "cuenta", cascade = CascadeType.ALL)
    @OrderBy("fechaServicio, id")
    private List<Cargo> cargos = new ArrayList<>();

    protected Cuenta() {
    }

    Cuenta(String numero, Long pacienteId, Long episodioId, Contrato contrato, String aseguradora, String numeroAutorizacion,
           boolean urgencia) {
        this.numero = numero;
        this.pacienteId = pacienteId;
        this.episodioId = episodioId;
        this.contrato = contrato;
        this.aseguradora = aseguradora;
        this.numeroAutorizacion = numeroAutorizacion;
        this.urgencia = urgencia;
        this.estado = EstadoCuenta.ABIERTA;
        this.abiertaEn = Instant.now();
    }

    public List<Cargo> activos() {
        return cargos.stream().filter(Cargo::isActivo).toList();
    }

    void exigirEditable() {
        if (!EstadoCuenta.EDITABLES.contains(estado)) {
            throw new ReglaNegocioException("CUENTA_NO_EDITABLE", "La cuenta está «" + estado.getDescripcion().toLowerCase()
                    + "»: ya no se modifican sus cargos.");
        }
    }

    void registrarAutorizacion(String numero) {
        if (estado.ordinal() >= EstadoCuenta.FACTURADA.ordinal()) {
            throw new ReglaNegocioException("CUENTA_FACTURADA", "La cuenta ya fue facturada.");
        }
        this.numeroAutorizacion = numero;
    }

    void enviarAAuditoria() {
        exigir(Set.of(EstadoCuenta.ABIERTA, EstadoCuenta.DEVUELTA), "Solo se envía a auditoría una cuenta abierta o devuelta.");
        if (activos().isEmpty()) {
            throw new ReglaNegocioException("CUENTA_VACIA", "La cuenta no tiene cargos.");
        }
        estado = EstadoCuenta.EN_AUDITORIA;
        enviadaAuditoriaEn = Instant.now();
    }

    public void devolver() {
        exigir(Set.of(EstadoCuenta.EN_AUDITORIA), "Solo se devuelve una cuenta en auditoría.");
        estado = EstadoCuenta.DEVUELTA;
    }

    public void aprobarAuditoria(String usuario) {
        exigir(Set.of(EstadoCuenta.EN_AUDITORIA), "Solo se aprueba una cuenta en auditoría.");
        estado = EstadoCuenta.AUDITADA;
        auditadaEn = Instant.now();
        auditadaPor = usuario;
    }

    void facturar() {
        exigir(Set.of(EstadoCuenta.AUDITADA), "Solo se factura una cuenta aprobada por auditoría.");
        estado = EstadoCuenta.FACTURADA;
        facturadaEn = Instant.now();
    }

    void glosar() {
        exigir(Set.of(EstadoCuenta.FACTURADA, EstadoCuenta.GLOSADA), "Solo se glosa una cuenta facturada.");
        estado = EstadoCuenta.GLOSADA;
    }

    void marcarPagada() {
        estado = EstadoCuenta.PAGADA;
    }

    void cerrar() {
        exigir(Set.of(EstadoCuenta.PAGADA), "Solo se cierra una cuenta pagada.");
        estado = EstadoCuenta.CERRADA;
        cerradaEn = Instant.now();
    }

    private void exigir(Set<EstadoCuenta> permitidos, String mensaje) {
        if (!permitidos.contains(estado)) throw new ReglaNegocioException("CUENTA_ESTADO_INVALIDO", mensaje);
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

    public Long getEpisodioId() {
        return episodioId;
    }

    public Contrato getContrato() {
        return contrato;
    }

    public String getAseguradora() {
        return aseguradora;
    }

    public String getNumeroAutorizacion() {
        return numeroAutorizacion;
    }

    public boolean isUrgencia() {
        return urgencia;
    }

    public EstadoCuenta getEstado() {
        return estado;
    }

    public Instant getAbiertaEn() {
        return abiertaEn;
    }

    public Instant getEnviadaAuditoriaEn() {
        return enviadaAuditoriaEn;
    }

    public Instant getAuditadaEn() {
        return auditadaEn;
    }

    public String getAuditadaPor() {
        return auditadaPor;
    }

    public Instant getFacturadaEn() {
        return facturadaEn;
    }

    public Instant getCerradaEn() {
        return cerradaEn;
    }

    public List<Cargo> getCargos() {
        return cargos;
    }
}
