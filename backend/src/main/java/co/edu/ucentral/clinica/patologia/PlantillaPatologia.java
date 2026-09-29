package co.edu.ucentral.clinica.patologia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

/** RFPAT-005: campos del informe por tipo de estudio, personalizables por servicio. */
@Entity
@Table(name = "plantilla_patologia")
public class PlantillaPatologia {

    public enum Seccion { MACRO, MICRO }

    public enum TipoCampo { texto, numero, opcion }

    /** Definición de un campo de la plantilla. */
    public record Campo(String clave, String etiqueta, Seccion seccion, TipoCampo tipo, List<String> opciones,
                        boolean requerido, String unidad) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Campo>> LISTA = new TypeReference<>() {
    };

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCaso tipo;

    private String servicio;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String campos;

    @Column(name = "modificado_en")
    private Instant modificadoEn;

    @Column(name = "modificado_por")
    private String modificadoPor;

    protected PlantillaPatologia() {
    }

    PlantillaPatologia(TipoCaso tipo, String servicio, String nombre, List<Campo> campos, String usuario) {
        this.tipo = tipo;
        this.servicio = servicio;
        this.nombre = nombre;
        actualizar(nombre, campos, usuario);
    }

    void actualizar(String nombre, List<Campo> campos, String usuario) {
        this.nombre = nombre;
        this.campos = JSON.writeValueAsString(campos);
        this.modificadoPor = usuario;
        this.modificadoEn = Instant.now();
    }

    public List<Campo> getCampos() {
        return JSON.readValue(campos, LISTA);
    }

    public List<Campo> campos(Seccion seccion) {
        return getCampos().stream().filter(c -> c.seccion() == seccion).toList();
    }

    public Long getId() {
        return id;
    }

    public TipoCaso getTipo() {
        return tipo;
    }

    public String getServicio() {
        return servicio;
    }

    public String getNombre() {
        return nombre;
    }

    public Instant getModificadoEn() {
        return modificadoEn;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }
}
