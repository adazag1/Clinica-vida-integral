package co.edu.ucentral.clinica.laboratorio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** RFLC-009: repetición de una prueba con el valor anterior y el motivo. */
@Entity
@Table(name = "reproceso_lab")
public class ReprocesoLab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resultado_id", nullable = false)
    private Long resultadoId;

    @Column(name = "valor_anterior", nullable = false, precision = 10, scale = 3)
    private BigDecimal valorAnterior;

    @Column(name = "valor_nuevo", nullable = false, precision = 10, scale = 3)
    private BigDecimal valorNuevo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoLab.Reproceso motivo;

    private String detalle;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha;

    protected ReprocesoLab() {
    }

    public ReprocesoLab(Long resultadoId, BigDecimal valorAnterior, BigDecimal valorNuevo, MotivoLab.Reproceso motivo,
                        String detalle, String usuario) {
        this.resultadoId = resultadoId;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
        this.motivo = motivo;
        this.detalle = detalle;
        this.usuario = usuario;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getResultadoId() {
        return resultadoId;
    }

    public BigDecimal getValorAnterior() {
        return valorAnterior;
    }

    public BigDecimal getValorNuevo() {
        return valorNuevo;
    }

    public MotivoLab.Reproceso getMotivo() {
        return motivo;
    }

    public String getDetalle() {
        return detalle;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getFecha() {
        return fecha;
    }
}
