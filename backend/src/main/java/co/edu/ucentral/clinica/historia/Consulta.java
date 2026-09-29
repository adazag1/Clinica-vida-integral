package co.edu.ucentral.clinica.historia;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Atención médica (evolución). Mientras está EN_CURSO solo la edita el médico que la abrió;
 * al firmarse queda inmutable y su contenido protegido por una huella SHA-256 (RFMT-008 / RFMT-011).
 */
@Entity
@Table(name = "consulta")
public class Consulta extends EntidadAuditable {

    public enum Estado {
        EN_CURSO("En consulta"),
        FIRMADA("Firmada");

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

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "episodio_id")
    private Long episodioId;

    @Column(name = "cita_id")
    private Long citaId;

    /** Usuario del médico tratante. */
    @Column(nullable = false)
    private String medico;

    @Column(name = "medico_nombre", nullable = false)
    private String medicoNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(name = "motivo_consulta")
    private String motivoConsulta;

    @Column(name = "enfermedad_actual")
    private String enfermedadActual;

    @Column(name = "examen_fisico")
    private String examenFisico;

    private String plan;

    @Column(nullable = false)
    private Instant inicio;

    @Column(name = "firmada_en")
    private Instant firmadaEn;

    @Column(name = "firma_hash")
    private String firmaHash;

    @OneToMany(mappedBy = "consulta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("principal desc, id")
    private List<ConsultaDiagnostico> diagnosticos = new ArrayList<>();

    @OneToMany(mappedBy = "consulta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Prescripcion> prescripciones = new ArrayList<>();

    protected Consulta() {
    }

    public Consulta(Long pacienteId, Long episodioId, Long citaId, String medico, String medicoNombre,
                    String motivoConsulta) {
        this.pacienteId = pacienteId;
        this.episodioId = episodioId;
        this.citaId = citaId;
        this.medico = medico;
        this.medicoNombre = medicoNombre;
        this.motivoConsulta = motivoConsulta;
        this.estado = Estado.EN_CURSO;
        this.inicio = Instant.now();
    }

    /** Solo el médico que abrió la consulta la modifica, y nunca después de firmada. */
    public void exigirEditable(String usuario) {
        if (estado == Estado.FIRMADA) {
            throw new ReglaNegocioException("CONSULTA_FIRMADA",
                    "La consulta ya fue firmada y no se puede modificar. Registre una nueva evolución.");
        }
        if (!medico.equalsIgnoreCase(usuario)) {
            throw new ReglaNegocioException("CONSULTA_DE_OTRO_MEDICO",
                    "Solo " + medicoNombre + ", quien abrió la consulta, puede modificarla.");
        }
    }

    public void actualizarNotas(String motivo, String enfermedadActual, String examenFisico, String plan) {
        this.motivoConsulta = motivo;
        this.enfermedadActual = enfermedadActual;
        this.examenFisico = examenFisico;
        this.plan = plan;
    }

    public void reemplazarDiagnosticos(List<ConsultaDiagnostico> nuevos) {
        diagnosticos.clear();
        nuevos.forEach(d -> {
            d.asignar(this);
            diagnosticos.add(d);
        });
    }

    public void agregarPrescripcion(Prescripcion p) {
        p.asignar(this);
        prescripciones.add(p);
    }

    public void quitarPrescripcion(Long prescripcionId) {
        if (!prescripciones.removeIf(p -> p.getId().equals(prescripcionId))) {
            throw new ReglaNegocioException("PRESCRIPCION_NO_EXISTE", "El medicamento no está en la fórmula.");
        }
    }

    /**
     * Fija la hora de firma (en milisegundos, para que la huella se pueda recalcular igual al
     * leerla de la base de datos) y cierra la consulta. La huella se calcula después con esta hora.
     */
    public void firmar(Function<Consulta, String> huella) {
        this.estado = Estado.FIRMADA;
        this.firmadaEn = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        this.firmaHash = huella.apply(this);
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public Long getEpisodioId() {
        return episodioId;
    }

    public Long getCitaId() {
        return citaId;
    }

    public String getMedico() {
        return medico;
    }

    public String getMedicoNombre() {
        return medicoNombre;
    }

    public Estado getEstado() {
        return estado;
    }

    public String getMotivoConsulta() {
        return motivoConsulta;
    }

    public String getEnfermedadActual() {
        return enfermedadActual;
    }

    public String getExamenFisico() {
        return examenFisico;
    }

    public String getPlan() {
        return plan;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFirmadaEn() {
        return firmadaEn;
    }

    public String getFirmaHash() {
        return firmaHash;
    }

    public List<ConsultaDiagnostico> getDiagnosticos() {
        return diagnosticos;
    }

    public List<Prescripcion> getPrescripciones() {
        return prescripciones;
    }
}
