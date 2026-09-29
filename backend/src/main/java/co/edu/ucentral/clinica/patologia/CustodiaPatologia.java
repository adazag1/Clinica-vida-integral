package co.edu.ucentral.clinica.patologia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** RFPAT-008: movimiento en la cadena de custodia del material del caso. */
@Entity
@Table(name = "custodia_patologia")
public class CustodiaPatologia {

    public enum Material {
        PIEZA("Pieza o tejido remanente"),
        BLOQUE("Bloques de parafina"),
        LAMINA("Láminas");

        private final String descripcion;

        Material(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    public enum Accion {
        ARCHIVADO("Archivado"),
        PRESTADO("Prestado"),
        DEVUELTO("Devuelto"),
        DESCARTADO("Descartado");

        private final String descripcion;

        Accion(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caso_id", nullable = false)
    private Long casoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Material material;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Accion accion;

    @Column(nullable = false)
    private int cantidad;

    private String ubicacion;

    private String destinatario;

    private String motivo;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha;

    protected CustodiaPatologia() {
    }

    CustodiaPatologia(Long casoId, Material material, Accion accion, int cantidad, String ubicacion, String destinatario,
                      String motivo, String usuario) {
        this.casoId = casoId;
        this.material = material;
        this.accion = accion;
        this.cantidad = cantidad;
        this.ubicacion = ubicacion;
        this.destinatario = destinatario;
        this.motivo = motivo;
        this.usuario = usuario;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCasoId() {
        return casoId;
    }

    public Material getMaterial() {
        return material;
    }

    public Accion getAccion() {
        return accion;
    }

    public int getCantidad() {
        return cantidad;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public String getDestinatario() {
        return destinatario;
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
