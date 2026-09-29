package co.edu.ucentral.clinica.documentos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** RNFGAC-006: toda acción sobre un documento (incluso verlo o descargarlo) con usuario y hora. */
@Entity
@Table(name = "documento_evento")
public class DocumentoEvento {

    public enum Accion {
        REGISTRADO, VER, DESCARGAR, NUEVA_VERSION, UBICACION, CAMBIO_ESTADO, ELIMINADO, SOLICITADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "documento_id", nullable = false)
    private Long documentoId;

    @Column(nullable = false)
    private String accion;

    private String detalle;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha;

    protected DocumentoEvento() {
    }

    DocumentoEvento(Long documentoId, Accion accion, String detalle, String usuario) {
        this.documentoId = documentoId;
        this.accion = accion.name();
        this.detalle = detalle;
        this.usuario = usuario;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentoId() {
        return documentoId;
    }

    public String getAccion() {
        return accion;
    }

    public String getDetalle() {
        return detalle;
    }

    public String getUsuario() {
        return usuario;
    }

    public Instant getFecha() {
        return fecha;
    }
}
