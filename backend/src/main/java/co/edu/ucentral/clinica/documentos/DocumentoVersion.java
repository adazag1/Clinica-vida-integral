package co.edu.ucentral.clinica.documentos;

import co.edu.ucentral.clinica.archivos.Archivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** RFGAC-005: cada versión de un documento cargado, con su archivo y el motivo del cambio. */
@Entity
@Table(name = "documento_version")
public class DocumentoVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "documento_id", nullable = false)
    private Long documentoId;

    @Column(nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "archivo_id")
    private Archivo archivo;

    private String motivo;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha;

    protected DocumentoVersion() {
    }

    DocumentoVersion(Long documentoId, int version, Archivo archivo, String motivo, String usuario) {
        this.documentoId = documentoId;
        this.version = version;
        this.archivo = archivo;
        this.motivo = motivo;
        this.usuario = usuario;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentoId() {
        return documentoId;
    }

    public int getVersion() {
        return version;
    }

    public Archivo getArchivo() {
        return archivo;
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
