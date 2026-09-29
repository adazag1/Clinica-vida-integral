package co.edu.ucentral.clinica.pacientes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Registro inmutable de cada cambio en los datos de un paciente (RFADM-011). */
@Entity
@Table(name = "cambio_demografico")
public class CambioDemografico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(nullable = false)
    private String campo;

    @Column(name = "valor_anterior")
    private String valorAnterior;

    @Column(name = "valor_nuevo")
    private String valorNuevo;

    @Column(nullable = false)
    private String motivo;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha;

    protected CambioDemografico() {
    }

    public CambioDemografico(Long pacienteId, Paciente.Cambio cambio, String motivo, String usuario) {
        this.pacienteId = pacienteId;
        this.campo = cambio.campo();
        this.valorAnterior = cambio.valorAnterior();
        this.valorNuevo = cambio.valorNuevo();
        this.motivo = motivo;
        this.usuario = usuario;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getFecha() {
        return fecha;
    }
}
