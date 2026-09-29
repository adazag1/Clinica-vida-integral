package co.edu.ucentral.clinica.patologia;

import co.edu.ucentral.clinica.comun.seguridad.Huella;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFPAT-006 / RFPAT-007 / RNFPAT-001: cada validación guarda una versión inmutable del informe con
 * autor, fecha, motivo del cambio y huella. La versión 1 es la original y nunca se pierde.
 */
@Entity
@Table(name = "informe_patologia")
public class InformePatologia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caso_id", nullable = false)
    private Long casoId;

    @Column(nullable = false)
    private int version;

    @Convert(converter = ValoresJson.class)
    @Column(nullable = false)
    private Map<String, String> macroscopia;

    @Convert(converter = ValoresJson.class)
    @Column(nullable = false)
    private Map<String, String> microscopia;

    @Column(nullable = false)
    private String diagnostico;

    @Column(name = "motivo_cambio")
    private String motivoCambio;

    @Column(nullable = false)
    private String autor;

    @Column(name = "autor_nombre", nullable = false)
    private String autorNombre;

    @Column(name = "validado_en", nullable = false)
    private Instant validadoEn;

    @Column(name = "firma_hash", nullable = false)
    private String firmaHash;

    protected InformePatologia() {
    }

    InformePatologia(CasoPatologia caso, int version, String motivoCambio, String autor, String autorNombre) {
        this.casoId = caso.getId();
        this.version = version;
        this.macroscopia = new LinkedHashMap<>(caso.getMacroscopia());
        this.microscopia = new LinkedHashMap<>(caso.getMicroscopia());
        this.diagnostico = caso.getDiagnostico();
        this.motivoCambio = motivoCambio;
        this.autor = autor;
        this.autorNombre = autorNombre;
        this.validadoEn = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        this.firmaHash = huella(caso.getNumero());
    }

    public String huella(String numeroCaso) {
        return Huella.de(numeroCaso, version, autor, validadoEn, new java.util.TreeMap<>(macroscopia),
                new java.util.TreeMap<>(microscopia), diagnostico, motivoCambio);
    }

    public Long getId() {
        return id;
    }

    public Long getCasoId() {
        return casoId;
    }

    public int getVersion() {
        return version;
    }

    public Map<String, String> getMacroscopia() {
        return macroscopia;
    }

    public Map<String, String> getMicroscopia() {
        return microscopia;
    }

    public String getDiagnostico() {
        return diagnostico;
    }

    public String getMotivoCambio() {
        return motivoCambio;
    }

    public String getAutor() {
        return autor;
    }

    public String getAutorNombre() {
        return autorNombre;
    }

    public Instant getValidadoEn() {
        return validadoEn;
    }

    public String getFirmaHash() {
        return firmaHash;
    }
}
