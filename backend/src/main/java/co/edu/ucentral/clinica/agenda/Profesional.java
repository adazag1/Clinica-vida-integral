package co.edu.ucentral.clinica.agenda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Médico con agenda. {@code usuarioId} enlaza su cuenta para que vea "Mi agenda". */
@Entity
@Table(name = "profesional")
public class Profesional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "especialidad_id")
    private Especialidad especialidad;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sede_id")
    private Sede sede;

    private String consultorio;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false)
    private boolean activo = true;

    protected Profesional() {
    }

    public Profesional(String nombre, Especialidad especialidad, Sede sede, String consultorio, Long usuarioId) {
        this.nombre = nombre;
        this.especialidad = especialidad;
        this.sede = sede;
        this.consultorio = consultorio;
        this.usuarioId = usuarioId;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public Especialidad getEspecialidad() {
        return especialidad;
    }

    public Sede getSede() {
        return sede;
    }

    public String getConsultorio() {
        return consultorio;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public boolean isActivo() {
        return activo;
    }
}
