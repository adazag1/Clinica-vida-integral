package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
import co.edu.ucentral.clinica.pacientes.Paciente;
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

/** RFAGE-007: paciente que espera un cupo con cierta especialidad (y opcionalmente cierto médico). */
@Entity
@Table(name = "lista_espera")
public class EntradaListaEspera extends EntidadAuditable {

    public enum Estado {
        ACTIVA,
        ASIGNADA,
        RETIRADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "especialidad_id")
    private Especialidad especialidad;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "profesional_id")
    private Profesional profesional;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_cita_id")
    private TipoCita tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrioridadCita prioridad;

    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(name = "cita_id")
    private Long citaId;

    protected EntradaListaEspera() {
    }

    public EntradaListaEspera(Paciente paciente, Especialidad especialidad, Profesional profesional, TipoCita tipo,
                              PrioridadCita prioridad, String motivo) {
        this.paciente = paciente;
        this.especialidad = especialidad;
        this.profesional = profesional;
        this.tipo = tipo;
        this.prioridad = prioridad;
        this.motivo = motivo;
        this.estado = Estado.ACTIVA;
    }

    public void asignar(Long citaId) {
        this.estado = Estado.ASIGNADA;
        this.citaId = citaId;
    }

    public void retirar() {
        if (estado != Estado.ACTIVA) {
            throw new ReglaNegocioException("LISTA_ESPERA_NO_ACTIVA", "El paciente ya no está en la lista de espera.");
        }
        this.estado = Estado.RETIRADA;
    }

    public Long getId() {
        return id;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public Especialidad getEspecialidad() {
        return especialidad;
    }

    public Profesional getProfesional() {
        return profesional;
    }

    public TipoCita getTipo() {
        return tipo;
    }

    public PrioridadCita getPrioridad() {
        return prioridad;
    }

    public String getMotivo() {
        return motivo;
    }

    public Estado getEstado() {
        return estado;
    }

    public Long getCitaId() {
        return citaId;
    }
}
