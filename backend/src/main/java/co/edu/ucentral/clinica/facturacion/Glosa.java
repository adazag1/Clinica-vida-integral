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
import java.time.LocalDate;

/** RFRC-008: objeción de la aseguradora a una factura, clasificada según el Manual Único de Glosas. */
@Entity
@Table(name = "glosa")
public class Glosa {

    /** Conceptos generales del Manual Único de Glosas (Res. 3047 de 2008, anexo técnico 6). */
    public enum Concepto {
        FACTURACION(1, "Facturación", false),
        TARIFAS(2, "Tarifas", false),
        SOPORTES(3, "Soportes", true),
        AUTORIZACION(4, "Autorización", false),
        COBERTURA(5, "Cobertura", false),
        PERTINENCIA(6, "Pertinencia", true);

        private final int codigo;
        private final String descripcion;
        /** Glosa por causa clínica: cuenta para el tablero de auditoría (RFAU-006). */
        private final boolean clinica;

        Concepto(int codigo, String descripcion, boolean clinica) {
            this.codigo = codigo;
            this.descripcion = descripcion;
            this.clinica = clinica;
        }

        public int getCodigo() {
            return codigo;
        }

        public String getDescripcion() {
            return descripcion;
        }

        public boolean isClinica() {
            return clinica;
        }
    }

    public enum Estado {
        RADICADA("Pendiente de respuesta"),
        ACEPTADA("Aceptada"),
        ACEPTADA_PARCIAL("Aceptada parcialmente"),
        LEVANTADA("No aceptada (respuesta sustentada)");

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id")
    private Factura factura;

    @Column(name = "cargo_id")
    private Long cargoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Concepto concepto;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(name = "valor_aceptado")
    private BigDecimal valorAceptado;

    private String respuesta;

    @Column(name = "recibida_en", nullable = false)
    private LocalDate recibidaEn;

    @Column(name = "limite_respuesta", nullable = false)
    private LocalDate limiteRespuesta;

    @Column(name = "respondida_en")
    private Instant respondidaEn;

    @Column(name = "respondida_por")
    private String respondidaPor;

    @Column(name = "registrada_por", nullable = false)
    private String registradaPor;

    protected Glosa() {
    }

    Glosa(Factura factura, Long cargoId, Concepto concepto, String codigo, String descripcion, BigDecimal valor,
          LocalDate recibidaEn, LocalDate limiteRespuesta, String usuario) {
        this.factura = factura;
        this.cargoId = cargoId;
        this.concepto = concepto;
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.valor = valor;
        this.recibidaEn = recibidaEn;
        this.limiteRespuesta = limiteRespuesta;
        this.estado = Estado.RADICADA;
        this.registradaPor = usuario;
    }

    /** Devuelve el valor que se acepta (lo que se descuenta del saldo). */
    BigDecimal responder(BigDecimal aceptado, String respuesta, String usuario) {
        if (estado != Estado.RADICADA) {
            throw new ReglaNegocioException("GLOSA_RESPONDIDA", "La glosa ya fue respondida.");
        }
        if (aceptado.signum() < 0 || aceptado.compareTo(valor) > 0) {
            throw new ReglaNegocioException("VALOR_INVALIDO", "El valor aceptado debe estar entre 0 y el valor glosado.");
        }
        this.valorAceptado = aceptado;
        this.estado = aceptado.signum() == 0 ? Estado.LEVANTADA : aceptado.compareTo(valor) == 0 ? Estado.ACEPTADA : Estado.ACEPTADA_PARCIAL;
        this.respuesta = respuesta;
        this.respondidaEn = Instant.now();
        this.respondidaPor = usuario;
        return aceptado;
    }

    public Long getId() {
        return id;
    }

    public Factura getFactura() {
        return factura;
    }

    public Long getCargoId() {
        return cargoId;
    }

    public Concepto getConcepto() {
        return concepto;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Estado getEstado() {
        return estado;
    }

    public BigDecimal getValorAceptado() {
        return valorAceptado;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public LocalDate getRecibidaEn() {
        return recibidaEn;
    }

    public LocalDate getLimiteRespuesta() {
        return limiteRespuesta;
    }

    public Instant getRespondidaEn() {
        return respondidaEn;
    }

    public String getRespondidaPor() {
        return respondidaPor;
    }

    public String getRegistradaPor() {
        return registradaPor;
    }
}
