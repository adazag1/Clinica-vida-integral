package co.edu.ucentral.clinica.facturacion;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RFRC-005 / RFRC-006: contrato con una aseguradora. La tarifa de cada servicio es la base por el
 * factor del contrato, salvo que haya una tarifa pactada; las reglas definen exclusiones, topes y
 * servicios que exigen autorización, y los paquetes agrupan servicios con un precio único.
 */
@Entity
@Table(name = "contrato")
public class Contrato {

    public enum Modalidad {
        EVENTO("Por evento"),
        CAPITACION("Capitación");

        private final String descripcion;

        Modalidad(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String aseguradora;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Modalidad modalidad;

    @Column(name = "factor_tarifa", nullable = false)
    private BigDecimal factorTarifa;

    @Column(name = "dias_pago", nullable = false)
    private int diasPago;

    @Column(name = "tope_cuenta")
    private BigDecimal topeCuenta;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "modificado_en")
    private Instant modificadoEn;

    @Column(name = "modificado_por")
    private String modificadoPor;

    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ContratoRegla> reglas = new ArrayList<>();

    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Paquete> paquetes = new ArrayList<>();

    protected Contrato() {
    }

    Contrato(String aseguradora, String nombre, Modalidad modalidad, BigDecimal factorTarifa, int diasPago,
             BigDecimal topeCuenta, LocalDate vigenteDesde, LocalDate vigenteHasta, String usuario) {
        this.aseguradora = aseguradora;
        configurar(nombre, modalidad, factorTarifa, diasPago, topeCuenta, vigenteDesde, vigenteHasta, true, usuario);
    }

    void configurar(String nombre, Modalidad modalidad, BigDecimal factorTarifa, int diasPago, BigDecimal topeCuenta,
                    LocalDate vigenteDesde, LocalDate vigenteHasta, boolean activo, String usuario) {
        this.nombre = nombre;
        this.modalidad = modalidad;
        this.factorTarifa = factorTarifa;
        this.diasPago = diasPago;
        this.topeCuenta = topeCuenta;
        this.vigenteDesde = vigenteDesde;
        this.vigenteHasta = vigenteHasta;
        this.activo = activo;
        this.modificadoPor = usuario;
        this.modificadoEn = Instant.now();
    }

    public boolean vigente(LocalDate fecha) {
        return activo && !fecha.isBefore(vigenteDesde) && (vigenteHasta == null || !fecha.isAfter(vigenteHasta));
    }

    public Optional<ContratoRegla> regla(ServicioFacturable s, ContratoRegla.Tipo tipo) {
        return reglas.stream().filter(r -> r.getServicio().getCodigo().equals(s.getCodigo()) && r.getTipo() == tipo).findFirst();
    }

    public Long getId() {
        return id;
    }

    public String getAseguradora() {
        return aseguradora;
    }

    public String getNombre() {
        return nombre;
    }

    public Modalidad getModalidad() {
        return modalidad;
    }

    public BigDecimal getFactorTarifa() {
        return factorTarifa;
    }

    public int getDiasPago() {
        return diasPago;
    }

    public BigDecimal getTopeCuenta() {
        return topeCuenta;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }

    public LocalDate getVigenteHasta() {
        return vigenteHasta;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getModificadoEn() {
        return modificadoEn;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }

    public List<ContratoRegla> getReglas() {
        return reglas;
    }

    public List<Paquete> getPaquetes() {
        return paquetes;
    }
}
