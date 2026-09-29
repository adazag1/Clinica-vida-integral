package co.edu.ucentral.clinica.laboratorio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Examen del catálogo (p. ej. hemograma), compuesto por uno o varios analitos. */
@Entity
@Table(name = "examen_lab")
public class ExamenLab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AreaLab area;

    @Column(name = "tipo_muestra", nullable = false)
    private String tipoMuestra;

    @Column(name = "tat_urgente_min", nullable = false)
    private int tatUrgenteMin;

    @Column(name = "tat_rutina_min", nullable = false)
    private int tatRutinaMin;

    @OneToMany(mappedBy = "examen")
    @OrderBy("orden")
    private List<Analito> analitos = new ArrayList<>();

    protected ExamenLab() {
    }

    /** RFLC-012: tiempo máximo de respuesta según la prioridad de la orden. */
    public Duration tat(PrioridadLab prioridad) {
        return Duration.ofMinutes(prioridad == PrioridadLab.URGENTE ? tatUrgenteMin : tatRutinaMin);
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public AreaLab getArea() {
        return area;
    }

    public String getTipoMuestra() {
        return tipoMuestra;
    }

    public int getTatUrgenteMin() {
        return tatUrgenteMin;
    }

    public int getTatRutinaMin() {
        return tatRutinaMin;
    }

    public List<Analito> getAnalitos() {
        return analitos;
    }
}
