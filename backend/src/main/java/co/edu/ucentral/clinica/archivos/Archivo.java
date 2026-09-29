package co.edu.ucentral.clinica.archivos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Metadatos de un archivo cargado. El contenido vive en la misma fila pero no se mapea aquí,
 * para que listar imágenes o documentos no traiga los bytes (ver {@link ArchivoRepository}).
 */
@Entity
@Table(name = "archivo")
public class Archivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "tipo_mime", nullable = false)
    private String tipoMime;

    @Column(nullable = false)
    private long tamano;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "subido_por", nullable = false)
    private String subidoPor;

    @Column(name = "subido_en", nullable = false)
    private Instant subidoEn;

    protected Archivo() {
    }

    Archivo(String nombre, String tipoMime, long tamano, String sha256, String subidoPor) {
        this.nombre = nombre;
        this.tipoMime = tipoMime;
        this.tamano = tamano;
        this.sha256 = sha256;
        this.subidoPor = subidoPor;
        this.subidoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    public long getTamano() {
        return tamano;
    }

    public String getSha256() {
        return sha256;
    }

    public String getSubidoPor() {
        return subidoPor;
    }

    public Instant getSubidoEn() {
        return subidoEn;
    }

    public boolean isImagen() {
        return tipoMime.startsWith("image/");
    }
}
