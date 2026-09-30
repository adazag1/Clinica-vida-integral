package co.edu.ucentral.clinica.ia;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * US 4.2: alerta que el motor muestra en la historia clínica. Sigue vigente mientras la regla la
 * siga disparando; al dejar de hacerlo se cierra, pero se conserva con la opinión del médico para
 * medir la precisión de cada regla.
 */
@Entity
@Table(name = "alerta_ia")
public class AlertaIa {

    public enum Retroalimentacion {
        UTIL("Útil"),
        NO_UTIL("No útil");

        private final String descripcion;

        Retroalimentacion(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(nullable = false)
    private String regla;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Hallazgo.Severidad severidad;

    private BigDecimal puntaje;

    private String escala;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false)
    private String detalle;

    /** Un dato por línea. */
    @Column(nullable = false)
    private String evidencia;

    private String recomendacion;

    @Column(nullable = false)
    private String huella;

    @Column(name = "generada_en", nullable = false)
    private Instant generadaEn;

    @Column(nullable = false)
    private boolean vigente;

    @Column(name = "cerrada_en")
    private Instant cerradaEn;

    @Enumerated(EnumType.STRING)
    private Retroalimentacion retroalimentacion;

    private String comentario;

    @Column(name = "retro_por")
    private String retroPor;

    @Column(name = "retro_en")
    private Instant retroEn;

    protected AlertaIa() {
    }

    public AlertaIa(Long pacienteId, String regla, Hallazgo h, String huella) {
        this.pacienteId = pacienteId;
        this.regla = regla;
        this.huella = huella;
        this.generadaEn = Instant.now();
        this.vigente = true;
        actualizar(h);
    }

    /** La regla sigue disparando con los mismos criterios: se refresca la evidencia (valores y tiempos). */
    public void actualizar(Hallazgo h) {
        this.severidad = h.severidad();
        this.puntaje = h.puntaje();
        this.escala = recortar(h.escala(), 120);
        this.titulo = recortar(h.titulo(), 200);
        this.detalle = recortar(h.detalle(), 1000);
        this.evidencia = String.join("\n", h.evidencia());
        this.recomendacion = recortar(h.recomendacion(), 600);
    }

    public void cerrar() {
        vigente = false;
        cerradaEn = Instant.now();
    }

    public void retroalimentar(Retroalimentacion valor, String comentario, String usuario) {
        if (valor == Retroalimentacion.NO_UTIL && (comentario == null || comentario.isBlank())) {
            throw new ReglaNegocioException("COMENTARIO_REQUERIDO",
                    "Cuéntenos por qué la alerta no fue útil: así el comité puede ajustar la regla.");
        }
        this.retroalimentacion = valor;
        this.comentario = comentario == null || comentario.isBlank() ? null : comentario.trim();
        this.retroPor = usuario;
        this.retroEn = Instant.now();
    }

    public List<String> evidencias() {
        return evidencia.isEmpty() ? List.of() : Arrays.asList(evidencia.split("\n"));
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public String getRegla() {
        return regla;
    }

    public Hallazgo.Severidad getSeveridad() {
        return severidad;
    }

    public BigDecimal getPuntaje() {
        return puntaje;
    }

    public String getEscala() {
        return escala;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDetalle() {
        return detalle;
    }

    public String getRecomendacion() {
        return recomendacion;
    }

    public String getHuella() {
        return huella;
    }

    public Instant getGeneradaEn() {
        return generadaEn;
    }

    public boolean isVigente() {
        return vigente;
    }

    public Instant getCerradaEn() {
        return cerradaEn;
    }

    public Retroalimentacion getRetroalimentacion() {
        return retroalimentacion;
    }

    public String getComentario() {
        return comentario;
    }

    public String getRetroPor() {
        return retroPor;
    }

    public Instant getRetroEn() {
        return retroEn;
    }
}
