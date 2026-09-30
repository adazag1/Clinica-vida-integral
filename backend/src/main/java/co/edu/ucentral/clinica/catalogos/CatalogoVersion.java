package co.edu.ucentral.clinica.catalogos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Versión vigente de un catálogo: sube con cada alta, cambio o inactivación (RFAU-005). */
@Entity
@Table(name = "catalogo_version")
public class CatalogoVersion {

    @Id
    private String catalogo;

    @Column(nullable = false)
    private int version;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "actualizado_por", nullable = false)
    private String actualizadoPor;

    protected CatalogoVersion() {
    }

    int subir(String usuario) {
        version++;
        actualizadoEn = Instant.now();
        actualizadoPor = usuario;
        return version;
    }

    public String getCatalogo() {
        return catalogo;
    }

    public int getVersion() {
        return version;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public String getActualizadoPor() {
        return actualizadoPor;
    }
}
