package co.edu.ucentral.clinica.imagenes;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** RFRAD-007: doble lectura con resultado estructurado. */
@Entity
@Table(name = "segunda_opinion")
public class SegundaOpinion {

    public enum Concordancia {
        CONCORDANTE("Concordante"),
        DISCREPANCIA_MENOR("Discrepancia menor"),
        DISCREPANCIA_MAYOR("Discrepancia mayor (cambia la conducta)");

        private final String descripcion;

        Concordancia(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "estudio_id", nullable = false)
    private Long estudioId;

    @Column(name = "solicitada_por", nullable = false)
    private String solicitadaPor;

    @Column(name = "asignada_a", nullable = false)
    private String asignadaA;

    @Column(nullable = false)
    private String pregunta;

    @Column(name = "solicitada_en", nullable = false)
    private Instant solicitadaEn;

    @Enumerated(EnumType.STRING)
    private Concordancia concordancia;

    private String comentario;

    @Column(name = "respondida_en")
    private Instant respondidaEn;

    protected SegundaOpinion() {
    }

    SegundaOpinion(Long estudioId, String solicitadaPor, String asignadaA, String pregunta) {
        this.estudioId = estudioId;
        this.solicitadaPor = solicitadaPor;
        this.asignadaA = asignadaA;
        this.pregunta = pregunta;
        this.solicitadaEn = Instant.now();
    }

    void responder(String usuario, Concordancia concordancia, String comentario) {
        if (!asignadaA.equalsIgnoreCase(usuario)) {
            throw new ReglaNegocioException("OPINION_AJENA", "Esta segunda lectura está asignada a " + asignadaA + ".");
        }
        if (respondidaEn != null) {
            throw new ReglaNegocioException("OPINION_RESPONDIDA", "Esta segunda lectura ya fue respondida.");
        }
        this.concordancia = concordancia;
        this.comentario = comentario;
        this.respondidaEn = Instant.now();
    }

    public boolean isPendiente() {
        return respondidaEn == null;
    }

    public Long getId() {
        return id;
    }

    public Long getEstudioId() {
        return estudioId;
    }

    public String getSolicitadaPor() {
        return solicitadaPor;
    }

    public String getAsignadaA() {
        return asignadaA;
    }

    public String getPregunta() {
        return pregunta;
    }

    public Instant getSolicitadaEn() {
        return solicitadaEn;
    }

    public Concordancia getConcordancia() {
        return concordancia;
    }

    public String getComentario() {
        return comentario;
    }

    public Instant getRespondidaEn() {
        return respondidaEn;
    }
}
