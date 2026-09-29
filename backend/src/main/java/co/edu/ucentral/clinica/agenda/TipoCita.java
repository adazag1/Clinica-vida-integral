package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.seguridad.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;

/** RFAGE-004: la duración de la cita la define el tipo, no quien agenda. */
@Entity
@Table(name = "tipo_cita")
public class TipoCita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "duracion_minutos", nullable = false)
    private int duracionMinutos;

    /** RFAGE-013: al agendarlo se avisa al área que prepara al paciente. */
    @Column(name = "requiere_preparacion", nullable = false)
    private boolean requierePreparacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_area_preparacion")
    private Rol rolAreaPreparacion;

    @Column(name = "instrucciones_preparacion")
    private String instruccionesPreparacion;

    protected TipoCita() {
    }

    public Duration getDuracion() {
        return Duration.ofMinutes(duracionMinutos);
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public int getDuracionMinutos() {
        return duracionMinutos;
    }

    public boolean isRequierePreparacion() {
        return requierePreparacion;
    }

    public Rol getRolAreaPreparacion() {
        return rolAreaPreparacion;
    }

    public String getInstruccionesPreparacion() {
        return instruccionesPreparacion;
    }
}
