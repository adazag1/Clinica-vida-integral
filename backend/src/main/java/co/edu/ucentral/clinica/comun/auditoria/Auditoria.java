package co.edu.ucentral.clinica.comun.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Registro inmutable de accesos y acciones sobre el sistema (RNF Registro de Auditoría). */
@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant fecha;

    private String usuario;

    @Column(nullable = false)
    private String accion;

    @Column(nullable = false)
    private String recurso;

    @Column(name = "metodo_http")
    private String metodoHttp;

    @Column(name = "estado_http")
    private Integer estadoHttp;

    private String ip;

    private String detalle;

    protected Auditoria() {
    }

    public Auditoria(String usuario, String accion, String recurso, String metodoHttp,
                     Integer estadoHttp, String ip, String detalle) {
        this.fecha = Instant.now();
        this.usuario = usuario;
        this.accion = accion;
        this.recurso = recurso;
        this.metodoHttp = metodoHttp;
        this.estadoHttp = estadoHttp;
        this.ip = ip;
        this.detalle = detalle;
    }

    public Long getId() {
        return id;
    }

    public Instant getFecha() {
        return fecha;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getAccion() {
        return accion;
    }

    public String getRecurso() {
        return recurso;
    }

    public String getMetodoHttp() {
        return metodoHttp;
    }

    public Integer getEstadoHttp() {
        return estadoHttp;
    }

    public String getIp() {
        return ip;
    }

    public String getDetalle() {
        return detalle;
    }
}
