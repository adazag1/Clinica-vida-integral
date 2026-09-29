package co.edu.ucentral.clinica.enfermeria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;

/** RFENF-008: entrega de turno con el resumen tal como estaba al entregar (no se recalcula después). */
@Entity
@Table(name = "entrega_turno")
public class EntregaTurno {

    public enum Turno {
        MANANA("Mañana", LocalTime.of(7, 0)),
        TARDE("Tarde", LocalTime.of(13, 0)),
        NOCHE("Noche", LocalTime.of(19, 0));

        private final String descripcion;
        private final LocalTime inicio;

        Turno(String descripcion, LocalTime inicio) {
            this.descripcion = descripcion;
            this.inicio = inicio;
        }

        public String getDescripcion() {
            return descripcion;
        }

        public LocalTime getInicio() {
            return inicio;
        }

        /** Turno de una hora local: mañana 7–13, tarde 13–19, noche 19–7. */
        public static Turno de(LocalTime hora) {
            if (!hora.isBefore(MANANA.inicio) && hora.isBefore(TARDE.inicio)) return MANANA;
            if (!hora.isBefore(TARDE.inicio) && hora.isBefore(NOCHE.inicio)) return TARDE;
            return NOCHE;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Turno turno;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resumen;

    private String nota;

    @Column(name = "entregado_por", nullable = false)
    private String entregadoPor;

    @Column(nullable = false)
    private Instant fecha;

    protected EntregaTurno() {
    }

    public EntregaTurno(Long pacienteId, Turno turno, String resumen, String nota, String entregadoPor) {
        this.pacienteId = pacienteId;
        this.turno = turno;
        this.resumen = resumen;
        this.nota = nota;
        this.entregadoPor = entregadoPor;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public Turno getTurno() {
        return turno;
    }

    public String getResumen() {
        return resumen;
    }

    public String getNota() {
        return nota;
    }

    public String getEntregadoPor() {
        return entregadoPor;
    }

    public Instant getFecha() {
        return fecha;
    }
}
