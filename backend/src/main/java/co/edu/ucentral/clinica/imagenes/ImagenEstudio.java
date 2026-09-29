package co.edu.ucentral.clinica.imagenes;

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

@Entity
@Table(name = "imagen_estudio")
public class ImagenEstudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estudio_id")
    private EstudioImagen estudio;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "archivo_id")
    private Archivo archivo;

    @Column(nullable = false)
    private int orden;

    private String descripcion;

    /** RFRAD-010: imagen clave, adjunta al informe. */
    @Column(nullable = false)
    private boolean clave;

    protected ImagenEstudio() {
    }

    ImagenEstudio(EstudioImagen estudio, Archivo archivo, int orden) {
        this.estudio = estudio;
        this.archivo = archivo;
        this.orden = orden;
    }

    void marcar(boolean clave, String descripcion) {
        this.clave = clave;
        this.descripcion = descripcion;
    }

    public Long getId() {
        return id;
    }

    public EstudioImagen getEstudio() {
        return estudio;
    }

    public Archivo getArchivo() {
        return archivo;
    }

    public int getOrden() {
        return orden;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isClave() {
        return clave;
    }
}
