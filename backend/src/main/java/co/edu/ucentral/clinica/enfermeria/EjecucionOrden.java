package co.edu.ucentral.clinica.enfermeria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Lo que pasó con una dosis o cuidado programado. {@code realizadaEn} la pone el servidor en el
 * momento del registro: es la hora real, no la que se digita (RFENF-004).
 */
@Entity
@Table(name = "ejecucion_orden")
public class EjecucionOrden {

    public enum Resultado {
        ADMINISTRADA("Administrada"),
        REALIZADO("Realizado"),
        OMITIDA("Omitida"),
        RECHAZADA_POR_PACIENTE("Rechazada por el paciente");

        private final String descripcion;

        Resultado(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "orden_id", nullable = false)
    private Long ordenId;

    @Column(name = "programada_para", nullable = false)
    private Instant programadaPara;

    @Column(name = "realizada_en", nullable = false)
    private Instant realizadaEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Resultado resultado;

    private String motivo;

    @Column(name = "justificacion_retraso")
    private String justificacionRetraso;

    /** Correctos verificados, p. ej. "PACIENTE,MEDICAMENTO,DOSIS,VIA,HORA". */
    private String verificaciones;

    @Column(name = "doble_validacion_por")
    private String dobleValidacionPor;

    @Column(nullable = false)
    private String usuario;

    protected EjecucionOrden() {
    }

    public EjecucionOrden(Long ordenId, Instant programadaPara, Resultado resultado, String motivo,
                          String justificacionRetraso, String verificaciones, String dobleValidacionPor, String usuario) {
        this.ordenId = ordenId;
        this.programadaPara = programadaPara;
        this.realizadaEn = Instant.now();
        this.resultado = resultado;
        this.motivo = motivo;
        this.justificacionRetraso = justificacionRetraso;
        this.verificaciones = verificaciones;
        this.dobleValidacionPor = dobleValidacionPor;
        this.usuario = usuario;
    }

    public Long getId() {
        return id;
    }

    public Long getOrdenId() {
        return ordenId;
    }

    public Instant getProgramadaPara() {
        return programadaPara;
    }

    public Instant getRealizadaEn() {
        return realizadaEn;
    }

    public Resultado getResultado() {
        return resultado;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getJustificacionRetraso() {
        return justificacionRetraso;
    }

    public String getVerificaciones() {
        return verificaciones;
    }

    public String getDobleValidacionPor() {
        return dobleValidacionPor;
    }

    public String getUsuario() {
        return usuario;
    }
}
