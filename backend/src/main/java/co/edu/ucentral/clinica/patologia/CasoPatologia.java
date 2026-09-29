package co.edu.ucentral.clinica.patologia;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Caso de patología: solicitud → recepción con verificación de identidad → macroscopía → microscopía → informe. */
@Entity
@Table(name = "caso_patologia")
public class CasoPatologia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numero;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "consulta_id")
    private Long consultaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCaso tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrioridadCaso prioridad;

    @Column(name = "sitio_anatomico", nullable = false)
    private String sitioAnatomico;

    @Column(name = "datos_clinicos")
    private String datosClinicos;

    @Column(name = "numero_muestras", nullable = false)
    private int numeroMuestras;

    @Column(nullable = false)
    private String medico;

    @Column(name = "medico_nombre", nullable = false)
    private String medicoNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCaso estado;

    @Column(name = "solicitado_en", nullable = false)
    private Instant solicitadoEn;

    @Column(name = "recibido_en")
    private Instant recibidoEn;

    @Column(name = "recibido_por")
    private String recibidoPor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plantilla_id")
    private PlantillaPatologia plantilla;

    @Convert(converter = ValoresJson.class)
    private Map<String, String> macroscopia;

    @Convert(converter = ValoresJson.class)
    private Map<String, String> microscopia;

    private String diagnostico;

    @Column(name = "macroscopia_por")
    private String macroscopiaPor;

    @Column(name = "macroscopia_en")
    private Instant macroscopiaEn;

    @Column(name = "validado_en")
    private Instant validadoEn;

    @Column(name = "validado_por")
    private String validadoPor;

    @Column(name = "retencion_hasta")
    private LocalDate retencionHasta;

    protected CasoPatologia() {
    }

    public CasoPatologia(String numero, Long pacienteId, Long consultaId, TipoCaso tipo, PrioridadCaso prioridad,
                         String sitioAnatomico, String datosClinicos, int numeroMuestras, String medico, String medicoNombre) {
        this.numero = numero;
        this.pacienteId = pacienteId;
        this.consultaId = consultaId;
        this.tipo = tipo;
        this.prioridad = prioridad;
        this.sitioAnatomico = sitioAnatomico;
        this.datosClinicos = datosClinicos;
        this.numeroMuestras = numeroMuestras;
        this.medico = medico;
        this.medicoNombre = medicoNombre;
        this.estado = EstadoCaso.SOLICITADO;
        this.solicitadoEn = Instant.now();
    }

    void recibir(String usuario, PlantillaPatologia plantilla) {
        exigir(EstadoCaso.SOLICITADO, "El caso ya fue recibido.");
        this.estado = EstadoCaso.RECIBIDO;
        this.recibidoEn = Instant.now();
        this.recibidoPor = usuario;
        this.plantilla = plantilla;
    }

    void registrarMacroscopia(Map<String, String> valores, String usuario) {
        if (estado != EstadoCaso.RECIBIDO && estado != EstadoCaso.MACROSCOPIA) {
            throw new ReglaNegocioException("CASO_ESTADO_INVALIDO", estado == EstadoCaso.SOLICITADO
                    ? "Primero reciba la muestra y verifique la identificación."
                    : "La macroscopía de un caso informado no se modifica: corrija el informe y valide una nueva versión.");
        }
        this.macroscopia = new LinkedHashMap<>(valores);
        this.macroscopiaPor = usuario;
        this.macroscopiaEn = Instant.now();
        this.estado = EstadoCaso.MACROSCOPIA;
    }

    /** Borrador de microscopía y diagnóstico; en un caso informado prepara la corrección (nueva versión). */
    void registrarMicroscopia(Map<String, String> valores, String diagnostico) {
        if (estado != EstadoCaso.MACROSCOPIA && estado != EstadoCaso.INFORMADO) {
            throw new ReglaNegocioException("CASO_ESTADO_INVALIDO", "Registre primero la macroscopía.");
        }
        this.microscopia = new LinkedHashMap<>(valores);
        this.diagnostico = diagnostico;
    }

    void validar(String usuario, LocalDate retencionHasta) {
        this.estado = EstadoCaso.INFORMADO;
        this.validadoEn = Instant.now();
        this.validadoPor = usuario;
        this.retencionHasta = retencionHasta;
    }

    public Instant limiteRespuesta() {
        return (recibidoEn != null ? recibidoEn : solicitadoEn).plus(prioridad.getObjetivo());
    }

    public boolean vencido(Instant ahora) {
        return estado != EstadoCaso.INFORMADO && ahora.isAfter(limiteRespuesta());
    }

    private void exigir(EstadoCaso esperado, String mensaje) {
        if (estado != esperado) throw new ReglaNegocioException("CASO_ESTADO_INVALIDO", mensaje);
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

    public TipoCaso getTipo() {
        return tipo;
    }

    public PrioridadCaso getPrioridad() {
        return prioridad;
    }

    public String getSitioAnatomico() {
        return sitioAnatomico;
    }

    public String getDatosClinicos() {
        return datosClinicos;
    }

    public int getNumeroMuestras() {
        return numeroMuestras;
    }

    public String getMedico() {
        return medico;
    }

    public String getMedicoNombre() {
        return medicoNombre;
    }

    public EstadoCaso getEstado() {
        return estado;
    }

    public Instant getSolicitadoEn() {
        return solicitadoEn;
    }

    public Instant getRecibidoEn() {
        return recibidoEn;
    }

    public String getRecibidoPor() {
        return recibidoPor;
    }

    public PlantillaPatologia getPlantilla() {
        return plantilla;
    }

    public Map<String, String> getMacroscopia() {
        return macroscopia == null ? Map.of() : macroscopia;
    }

    public Map<String, String> getMicroscopia() {
        return microscopia == null ? Map.of() : microscopia;
    }

    public String getDiagnostico() {
        return diagnostico;
    }

    public String getMacroscopiaPor() {
        return macroscopiaPor;
    }

    public Instant getMacroscopiaEn() {
        return macroscopiaEn;
    }

    public Instant getValidadoEn() {
        return validadoEn;
    }

    public String getValidadoPor() {
        return validadoPor;
    }

    public LocalDate getRetencionHasta() {
        return retencionHasta;
    }
}
