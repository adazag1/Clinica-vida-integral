package co.edu.ucentral.clinica.historia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Diagnóstico de una consulta: uno principal y los relacionados; impresión o confirmado. */
@Entity
@Table(name = "consulta_diagnostico")
public class ConsultaDiagnostico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consulta_id")
    private Consulta consulta;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "diagnostico_id")
    private Diagnostico diagnostico;

    @Column(nullable = false)
    private boolean principal;

    /** false = impresión diagnóstica (pendiente de confirmar). */
    @Column(nullable = false)
    private boolean confirmado;

    protected ConsultaDiagnostico() {
    }

    public ConsultaDiagnostico(Diagnostico diagnostico, boolean principal, boolean confirmado) {
        this.diagnostico = diagnostico;
        this.principal = principal;
        this.confirmado = confirmado;
    }

    void asignar(Consulta consulta) {
        this.consulta = consulta;
    }

    public Long getId() {
        return id;
    }

    public Diagnostico getDiagnostico() {
        return diagnostico;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public boolean isConfirmado() {
        return confirmado;
    }
}
