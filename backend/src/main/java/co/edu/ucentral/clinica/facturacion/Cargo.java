package co.edu.ucentral.clinica.facturacion;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cargo de la cuenta con la tarifa aplicada al registrarlo. Nunca se borra: se anula con motivo y
 * queda en el historial (RFAU-008).
 */
@Entity
@Table(name = "cargo")
public class Cargo {

    public enum Estado {
        ACTIVO, ANULADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id")
    private Cuenta cuenta;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "servicio_id")
    private ServicioFacturable servicio;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "valor_unitario", nullable = false)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal;

    @Column(name = "a_cargo_paciente", nullable = false)
    private boolean aCargoPaciente;

    @Column(name = "cubierto_capitacion", nullable = false)
    private boolean cubiertoCapitacion;

    @Column(name = "requiere_autorizacion", nullable = false)
    private boolean requiereAutorizacion;

    @Column(name = "tiene_soporte", nullable = false)
    private boolean tieneSoporte;

    private String soporte;

    @Column(name = "soporte_enlace")
    private String soporteEnlace;

    @Column(name = "origen_modulo")
    private String origenModulo;

    @Column(name = "referencia_origen")
    private String referenciaOrigen;

    @Column(name = "fecha_servicio", nullable = false)
    private Instant fechaServicio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(name = "factura_id")
    private Long facturaId;

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;

    @Column(name = "anulado_por")
    private String anuladoPor;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    @Column(name = "registrado_por", nullable = false)
    private String registradoPor;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    protected Cargo() {
    }

    Cargo(Cuenta cuenta, ServicioFacturable servicio, int cantidad, Tarifa tarifa, String soporte, String soporteEnlace,
          String origenModulo, String referenciaOrigen, Instant fechaServicio, String usuario) {
        this.cuenta = cuenta;
        this.servicio = servicio;
        this.cantidad = cantidad;
        this.valorUnitario = tarifa.valorUnitario();
        this.valorTotal = tarifa.valorUnitario().multiply(BigDecimal.valueOf(cantidad));
        this.aCargoPaciente = tarifa.aCargoPaciente();
        this.cubiertoCapitacion = tarifa.cubiertoCapitacion();
        this.requiereAutorizacion = tarifa.requiereAutorizacion();
        this.soporte = soporte;
        this.soporteEnlace = soporteEnlace;
        this.tieneSoporte = soporte != null && !soporte.isBlank();
        this.origenModulo = origenModulo;
        this.referenciaOrigen = referenciaOrigen;
        this.fechaServicio = fechaServicio;
        this.estado = Estado.ACTIVO;
        this.registradoPor = usuario;
        this.registradoEn = Instant.now();
    }

    /** Resultado de aplicar el contrato a un servicio (RFRC-005). */
    record Tarifa(BigDecimal valorUnitario, boolean aCargoPaciente, boolean cubiertoCapitacion, boolean requiereAutorizacion) {
    }

    void registrarSoporte(String soporte, String enlace) {
        this.soporte = soporte;
        this.soporteEnlace = enlace;
        this.tieneSoporte = true;
    }

    void anular(String motivo, String usuario) {
        if (estado == Estado.ANULADO) {
            throw new ReglaNegocioException("CARGO_ANULADO", "El cargo ya estaba anulado.");
        }
        if (facturaId != null) {
            throw new ReglaNegocioException("CARGO_FACTURADO", "El cargo ya fue facturado: se corrige con una nota crédito.");
        }
        estado = Estado.ANULADO;
        motivoAnulacion = motivo;
        anuladoPor = usuario;
        anuladoEn = Instant.now();
    }

    void facturar(Long facturaId) {
        if (this.facturaId != null) {
            // RFRC-011: un cargo no se factura dos veces
            throw new ReglaNegocioException("CARGO_YA_FACTURADO", "El cargo «" + servicio.getNombre() + "» ya fue facturado.");
        }
        this.facturaId = facturaId;
    }

    public boolean isActivo() {
        return estado == Estado.ACTIVO;
    }

    public Long getId() {
        return id;
    }

    public Cuenta getCuenta() {
        return cuenta;
    }

    public ServicioFacturable getServicio() {
        return servicio;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public boolean isACargoPaciente() {
        return aCargoPaciente;
    }

    public boolean isCubiertoCapitacion() {
        return cubiertoCapitacion;
    }

    public boolean isRequiereAutorizacion() {
        return requiereAutorizacion;
    }

    public boolean isTieneSoporte() {
        return tieneSoporte;
    }

    public String getSoporte() {
        return soporte;
    }

    public String getSoporteEnlace() {
        return soporteEnlace;
    }

    public String getOrigenModulo() {
        return origenModulo;
    }

    public String getReferenciaOrigen() {
        return referenciaOrigen;
    }

    public Instant getFechaServicio() {
        return fechaServicio;
    }

    public Estado getEstado() {
        return estado;
    }

    public Long getFacturaId() {
        return facturaId;
    }

    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    public String getAnuladoPor() {
        return anuladoPor;
    }

    public Instant getAnuladoEn() {
        return anuladoEn;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }
}
