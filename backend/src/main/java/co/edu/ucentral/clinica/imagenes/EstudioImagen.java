package co.edu.ucentral.clinica.imagenes;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Estudio de imágenes: orden del médico → adquisición → lectura → informe firmado. */
@Entity
@Table(name = "estudio_imagen")
public class EstudioImagen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numero;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "consulta_id")
    private Long consultaId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_estudio_id")
    private TipoEstudio tipoEstudio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrioridadEstudio prioridad;

    @Column(nullable = false)
    private String motivo;

    @Column(name = "diagnostico_presuntivo")
    private String diagnosticoPresuntivo;

    @Column(nullable = false)
    private String medico;

    @Column(name = "medico_nombre", nullable = false)
    private String medicoNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoEstudio estado;

    @Column(name = "ordenado_en", nullable = false)
    private Instant ordenadoEn;

    @Column(name = "adquirido_en")
    private Instant adquiridoEn;

    @Column(name = "adquirido_por")
    private String adquiridoPor;

    @Column(name = "asignado_a")
    private String asignadoA;

    @Column(name = "study_instance_uid")
    private String studyInstanceUid;

    @Column(name = "bloqueo_motivo")
    private String bloqueoMotivo;

    @Column(name = "bloqueado_por")
    private String bloqueadoPor;

    @Column(name = "bloqueado_en")
    private Instant bloqueadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_antes_bloqueo")
    private EstadoEstudio estadoAntesBloqueo;

    @Column(name = "informado_en")
    private Instant informadoEn;

    @Column(name = "informado_por")
    private String informadoPor;

    @Column(nullable = false)
    private boolean critico;

    @Column(name = "critico_receptor")
    private String criticoReceptor;

    @Column(name = "critico_medio")
    private String criticoMedio;

    @Column(name = "critico_notificado_en")
    private Instant criticoNotificadoEn;

    @Column(name = "tat_alertado", nullable = false)
    private boolean tatAlertado;

    @OneToMany(mappedBy = "estudio", cascade = CascadeType.ALL)
    @OrderBy("orden")
    private List<ImagenEstudio> imagenes = new ArrayList<>();

    @OneToMany(mappedBy = "estudio", cascade = CascadeType.ALL)
    @OrderBy("version")
    private List<InformeImagen> informes = new ArrayList<>();

    protected EstudioImagen() {
    }

    public EstudioImagen(String numero, Long pacienteId, Long consultaId, TipoEstudio tipoEstudio, PrioridadEstudio prioridad,
                         String motivo, String diagnosticoPresuntivo, String medico, String medicoNombre) {
        this.numero = numero;
        this.pacienteId = pacienteId;
        this.consultaId = consultaId;
        this.tipoEstudio = tipoEstudio;
        this.prioridad = prioridad;
        this.motivo = motivo;
        this.diagnosticoPresuntivo = diagnosticoPresuntivo;
        this.medico = medico;
        this.medicoNombre = medicoNombre;
        this.estado = EstadoEstudio.ORDENADO;
        this.ordenadoEn = Instant.now();
    }

    /** Llegan las imágenes: el estudio pasa a la lista de lectura. */
    void adquirir(String usuario, String uid) {
        exigirNoBloqueado();
        if (estado == EstadoEstudio.INFORMADO) {
            throw new ReglaNegocioException("ESTUDIO_INFORMADO", "El estudio ya está informado; para corregirlo use «Enmendar».");
        }
        if (estado == EstadoEstudio.ORDENADO) {
            estado = EstadoEstudio.ADQUIRIDO;
            adquiridoEn = Instant.now();
            adquiridoPor = usuario;
        }
        if (uid != null) studyInstanceUid = uid;
    }

    void iniciarLectura(String usuario) {
        exigirNoBloqueado();
        if (estado == EstadoEstudio.ORDENADO) {
            throw new ReglaNegocioException("SIN_IMAGENES", "Aún no hay imágenes: el estudio no se ha adquirido.");
        }
        estado = EstadoEstudio.EN_LECTURA;
        asignadoA = usuario;
    }

    void informar(String usuario) {
        exigirNoBloqueado();
        estado = EstadoEstudio.INFORMADO;
        if (informadoEn == null) {
            // RFRAD-012: el tiempo de respuesta se mide hasta el primer informe firmado
            informadoEn = Instant.now();
            informadoPor = usuario;
        }
    }

    /** RFRAD-008: fecha, hora y receptor de la notificación del hallazgo crítico. */
    void registrarCritico(String receptor, String medio) {
        critico = true;
        criticoReceptor = receptor;
        criticoMedio = medio;
        criticoNotificadoEn = Instant.now();
    }

    void asignar(String usuario) {
        asignadoA = usuario;
    }

    /** RFRAD-011: nadie lee ni firma un estudio con la identificación en duda. */
    void bloquear(String motivo, String usuario) {
        exigirNoBloqueado();
        estadoAntesBloqueo = estado;
        estado = EstadoEstudio.BLOQUEADO;
        bloqueoMotivo = motivo;
        bloqueadoPor = usuario;
        bloqueadoEn = Instant.now();
    }

    void desbloquear() {
        if (estado != EstadoEstudio.BLOQUEADO) {
            throw new ReglaNegocioException("ESTUDIO_NO_BLOQUEADO", "El estudio no está bloqueado.");
        }
        estado = estadoAntesBloqueo;
        estadoAntesBloqueo = null;
        bloqueoMotivo = null;
        bloqueadoPor = null;
        bloqueadoEn = null;
    }

    void exigirNoBloqueado() {
        if (estado == EstadoEstudio.BLOQUEADO) {
            throw new ReglaNegocioException("ESTUDIO_BLOQUEADO", "Estudio bloqueado por identificación: " + bloqueoMotivo
                    + ". Debe resolverse antes de continuar.");
        }
    }

    public Instant limiteTat() {
        return ordenadoEn.plus(tipoEstudio.tat(prioridad));
    }

    public boolean tatVencido(Instant ahora) {
        return informadoEn == null && ahora.isAfter(limiteTat());
    }

    void marcarTatAlertado() {
        tatAlertado = true;
    }

    public Optional<InformeImagen> borrador() {
        return informes.stream().filter(i -> i.getEstado() == InformeImagen.Estado.BORRADOR).findFirst();
    }

    /** La última versión firmada: es la que ve el médico. */
    public Optional<InformeImagen> vigente() {
        return informes.stream().filter(i -> i.getEstado() == InformeImagen.Estado.FIRMADO).reduce((a, b) -> b);
    }

    public int siguienteVersion() {
        return informes.stream().mapToInt(InformeImagen::getVersion).max().orElse(0) + 1;
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public Long getConsultaId() {
        return consultaId;
    }

    public TipoEstudio getTipoEstudio() {
        return tipoEstudio;
    }

    public PrioridadEstudio getPrioridad() {
        return prioridad;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getDiagnosticoPresuntivo() {
        return diagnosticoPresuntivo;
    }

    public String getMedico() {
        return medico;
    }

    public String getMedicoNombre() {
        return medicoNombre;
    }

    public EstadoEstudio getEstado() {
        return estado;
    }

    public Instant getOrdenadoEn() {
        return ordenadoEn;
    }

    public Instant getAdquiridoEn() {
        return adquiridoEn;
    }

    public String getAdquiridoPor() {
        return adquiridoPor;
    }

    public String getAsignadoA() {
        return asignadoA;
    }

    public String getStudyInstanceUid() {
        return studyInstanceUid;
    }

    public String getBloqueoMotivo() {
        return bloqueoMotivo;
    }

    public String getBloqueadoPor() {
        return bloqueadoPor;
    }

    public Instant getBloqueadoEn() {
        return bloqueadoEn;
    }

    public Instant getInformadoEn() {
        return informadoEn;
    }

    public String getInformadoPor() {
        return informadoPor;
    }

    public boolean isCritico() {
        return critico;
    }

    public String getCriticoReceptor() {
        return criticoReceptor;
    }

    public String getCriticoMedio() {
        return criticoMedio;
    }

    public Instant getCriticoNotificadoEn() {
        return criticoNotificadoEn;
    }

    public boolean isTatAlertado() {
        return tatAlertado;
    }

    public List<ImagenEstudio> getImagenes() {
        return imagenes;
    }

    public List<InformeImagen> getInformes() {
        return informes;
    }
}
