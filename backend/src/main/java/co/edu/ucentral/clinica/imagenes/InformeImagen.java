package co.edu.ucentral.clinica.imagenes;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.comun.seguridad.Huella;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Una versión del informe radiológico. Mientras es borrador la edita su autor; firmada queda
 * inmutable con su huella (RNFRAD-002). Una corrección es una versión nueva con motivo.
 */
@Entity
@Table(name = "informe_imagen")
public class InformeImagen {

    public enum Estado {
        BORRADOR, FIRMADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estudio_id")
    private EstudioImagen estudio;

    @Column(nullable = false)
    private int version;

    private String hallazgos;

    private String impresion;

    private String recomendaciones;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(name = "motivo_cambio")
    private String motivoCambio;

    @Column(nullable = false)
    private String autor;

    @Column(name = "autor_nombre", nullable = false)
    private String autorNombre;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "modificado_en", nullable = false)
    private Instant modificadoEn;

    @Column(name = "firmado_en")
    private Instant firmadoEn;

    @Column(name = "firma_hash")
    private String firmaHash;

    protected InformeImagen() {
    }

    InformeImagen(EstudioImagen estudio, int version, String hallazgos, String impresion, String recomendaciones,
                  String motivoCambio, String autor, String autorNombre) {
        this.estudio = estudio;
        this.version = version;
        this.hallazgos = hallazgos;
        this.impresion = impresion;
        this.recomendaciones = recomendaciones;
        this.motivoCambio = motivoCambio;
        this.autor = autor;
        this.autorNombre = autorNombre;
        this.estado = Estado.BORRADOR;
        this.creadoEn = Instant.now();
        this.modificadoEn = creadoEn;
    }

    void editar(String hallazgos, String impresion, String recomendaciones) {
        if (estado == Estado.FIRMADO) {
            throw new ReglaNegocioException("INFORME_FIRMADO", "Un informe firmado no se modifica: use «Enmendar».");
        }
        this.hallazgos = hallazgos;
        this.impresion = impresion;
        this.recomendaciones = recomendaciones;
        this.modificadoEn = Instant.now();
    }

    void firmar() {
        estado = Estado.FIRMADO;
        firmadoEn = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        firmaHash = huella();
    }

    public String huella() {
        return Huella.de(estudio.getNumero(), version, autor, firmadoEn, hallazgos, impresion, recomendaciones, motivoCambio);
    }

    public Long getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public String getHallazgos() {
        return hallazgos;
    }

    public String getImpresion() {
        return impresion;
    }

    public String getRecomendaciones() {
        return recomendaciones;
    }

    public Estado getEstado() {
        return estado;
    }

    public String getMotivoCambio() {
        return motivoCambio;
    }

    public String getAutor() {
        return autor;
    }

    public String getAutorNombre() {
        return autorNombre;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getModificadoEn() {
        return modificadoEn;
    }

    public Instant getFirmadoEn() {
        return firmadoEn;
    }

    public String getFirmaHash() {
        return firmaHash;
    }
}
