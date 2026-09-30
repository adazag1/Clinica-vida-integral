package co.edu.ucentral.clinica.auditoriaclinica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** Codificación de la cuenta (diagnóstico principal y relacionados CIE-10) con la que se factura y se reportan los RIPS. */
@Entity
@Table(name = "codificacion")
public class Codificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false, unique = true)
    private Long cuentaId;

    @Column(name = "diagnostico_principal")
    private String diagnosticoPrincipal;

    @Column(name = "diagnosticos_relacionados")
    private String diagnosticosRelacionados;

    @Column(name = "modificado_por")
    private String modificadoPor;

    @Column(name = "modificado_en")
    private Instant modificadoEn;

    protected Codificacion() {
    }

    Codificacion(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    void actualizar(String principal, List<String> relacionados, String usuario) {
        this.diagnosticoPrincipal = principal;
        this.diagnosticosRelacionados = relacionados.isEmpty() ? null : String.join(",", relacionados);
        this.modificadoPor = usuario;
        this.modificadoEn = Instant.now();
    }

    public List<String> relacionados() {
        return diagnosticosRelacionados == null ? List.of() : Arrays.asList(diagnosticosRelacionados.split(","));
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public String getDiagnosticoPrincipal() {
        return diagnosticoPrincipal;
    }

    public String getDiagnosticosRelacionados() {
        return diagnosticosRelacionados;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }

    public Instant getModificadoEn() {
        return modificadoEn;
    }
}
