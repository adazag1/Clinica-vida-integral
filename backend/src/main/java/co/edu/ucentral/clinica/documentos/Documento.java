package co.edu.ucentral.clinica.documentos;

import co.edu.ucentral.clinica.archivos.Archivo;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
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

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Documento clínico con metadatos, versión vigente, ubicación física y ciclo de vida (RFGAC-002 a 010). */
@Entity
@Table(name = "documento")
public class Documento extends EntidadAuditable {

    public enum Estado {
        ACTIVO("Activo"),
        ARCHIVADO("Archivado"),
        EN_CUSTODIA("En custodia"),
        ELIMINADO("Eliminado según normativa");

        private final String descripcion;

        Estado(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    public enum Soporte {
        DIGITAL, FISICO
    }

    public enum Origen {
        CARGADO, GENERADO
    }

    /** RFGAC-006: transiciones permitidas. Eliminado es final. */
    private static final Map<Estado, Set<Estado>> TRANSICIONES = Map.of(
            Estado.ACTIVO, EnumSet.of(Estado.ARCHIVADO, Estado.EN_CUSTODIA, Estado.ELIMINADO),
            Estado.ARCHIVADO, EnumSet.of(Estado.ACTIVO, Estado.EN_CUSTODIA, Estado.ELIMINADO),
            Estado.EN_CUSTODIA, EnumSet.of(Estado.ACTIVO, Estado.ARCHIVADO, Estado.ELIMINADO),
            Estado.ELIMINADO, EnumSet.noneOf(Estado.class));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "episodio_id")
    private Long episodioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDocumento tipo;

    @Column(nullable = false)
    private String titulo;

    private String descripcion;

    @Column(name = "fecha_documento", nullable = false)
    private LocalDate fechaDocumento;

    @Column(nullable = false)
    private String servicio;

    private String profesional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Soporte soporte;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origen origen;

    @Column(name = "modulo_origen")
    private String moduloOrigen;

    @Column(name = "referencia_origen")
    private String referenciaOrigen;

    private String enlace;

    @Column(name = "version_actual", nullable = false)
    private int versionActual;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "archivo_id")
    private Archivo archivo;

    private String sala;
    private String estanteria;
    private String seccion;
    private String caja;

    @Column(name = "retencion_hasta", nullable = false)
    private LocalDate retencionHasta;

    @Column(name = "motivo_estado")
    private String motivoEstado;

    protected Documento() {
    }

    Documento(Long pacienteId, Long episodioId, TipoDocumento tipo, String titulo, String descripcion,
              LocalDate fechaDocumento, String servicio, String profesional, Soporte soporte, Origen origen,
              LocalDate retencionHasta) {
        this.pacienteId = pacienteId;
        this.episodioId = episodioId;
        this.tipo = tipo;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.fechaDocumento = fechaDocumento;
        this.servicio = servicio;
        this.profesional = profesional;
        this.soporte = soporte;
        this.origen = origen;
        this.retencionHasta = retencionHasta;
        this.estado = Estado.ACTIVO;
    }

    void vincularOrigen(String modulo, String referencia, String enlace) {
        this.moduloOrigen = modulo;
        this.referenciaOrigen = referencia;
        this.enlace = enlace;
    }

    /** Nueva versión vigente; las anteriores quedan en documento_version. */
    int nuevaVersion(Archivo nuevo) {
        exigirNoEliminado();
        this.archivo = nuevo;
        return ++versionActual;
    }

    /** Documento generado por otro módulo que cambió (p. ej. informe enmendado). */
    int registrarCambioOrigen(String titulo) {
        exigirNoEliminado();
        this.titulo = titulo;
        return ++versionActual;
    }

    void ubicar(String sala, String estanteria, String seccion, String caja) {
        exigirNoEliminado();
        this.sala = sala;
        this.estanteria = estanteria;
        this.seccion = seccion;
        this.caja = caja;
    }

    boolean tieneUbicacion() {
        return sala != null && estanteria != null;
    }

    void cambiarEstado(Estado nuevo, String motivo, LocalDate hoy) {
        if (!TRANSICIONES.get(estado).contains(nuevo)) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA", "No se puede pasar de «" + estado.getDescripcion()
                    + "» a «" + nuevo.getDescripcion() + "».");
        }
        if (nuevo == Estado.ELIMINADO && !hoy.isAfter(retencionHasta)) {
            throw new ReglaNegocioException("RETENCION_VIGENTE", "El documento debe conservarse hasta el "
                    + retencionHasta.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " según la política de retención.");
        }
        if (nuevo == Estado.EN_CUSTODIA && !tieneUbicacion()) {
            throw new ReglaNegocioException("SIN_UBICACION", "Registre la ubicación física (sala y estantería) antes de ponerlo en custodia.");
        }
        this.estado = nuevo;
        this.motivoEstado = motivo;
    }

    private void exigirNoEliminado() {
        if (estado == Estado.ELIMINADO) {
            throw new ReglaNegocioException("DOCUMENTO_ELIMINADO", "El documento fue eliminado según la normativa.");
        }
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

    public TipoDocumento getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public LocalDate getFechaDocumento() {
        return fechaDocumento;
    }

    public String getServicio() {
        return servicio;
    }

    public String getProfesional() {
        return profesional;
    }

    public Soporte getSoporte() {
        return soporte;
    }

    public Estado getEstado() {
        return estado;
    }

    public Origen getOrigen() {
        return origen;
    }

    public String getModuloOrigen() {
        return moduloOrigen;
    }

    public String getReferenciaOrigen() {
        return referenciaOrigen;
    }

    public String getEnlace() {
        return enlace;
    }

    public int getVersionActual() {
        return versionActual;
    }

    public Archivo getArchivo() {
        return archivo;
    }

    public String getSala() {
        return sala;
    }

    public String getEstanteria() {
        return estanteria;
    }

    public String getSeccion() {
        return seccion;
    }

    public String getCaja() {
        return caja;
    }

    public LocalDate getRetencionHasta() {
        return retencionHasta;
    }

    public String getMotivoEstado() {
        return motivoEstado;
    }
}
