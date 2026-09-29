package co.edu.ucentral.clinica.agenda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** RFAGE-009: constancia de cada recordatorio, se haya podido entregar o no. */
@Entity
@Table(name = "recordatorio")
public class Recordatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cita_id", nullable = false)
    private Long citaId;

    @Column(nullable = false)
    private String canal;

    private String destino;

    /** ENVIADO, SIMULADO (sin proveedor configurado), SIN_DATOS o ERROR. */
    @Column(nullable = false)
    private String resultado;

    private String detalle;

    @Column(nullable = false)
    private Instant fecha;

    protected Recordatorio() {
    }

    public Recordatorio(Long citaId, String canal, String destino, String resultado, String detalle) {
        this.citaId = citaId;
        this.canal = canal;
        this.destino = destino;
        this.resultado = resultado;
        this.detalle = detalle;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCitaId() {
        return citaId;
    }

    public String getCanal() {
        return canal;
    }

    public String getDestino() {
        return destino;
    }

    public String getResultado() {
        return resultado;
    }

    public String getDetalle() {
        return detalle;
    }

    public Instant getFecha() {
        return fecha;
    }
}
