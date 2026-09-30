package co.edu.ucentral.clinica.ia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Estado configurable de una regla del motor: activa o no, y quién la cambió. */
@Entity
@Table(name = "regla_ia")
public class ReglaIa {

    @Id
    private String codigo;

    @Column(nullable = false)
    private boolean activa;

    @Column(name = "modificado_por")
    private String modificadoPor;

    @Column(name = "modificado_en")
    private Instant modificadoEn;

    protected ReglaIa() {
    }

    public ReglaIa(String codigo) {
        this.codigo = codigo;
        this.activa = true;
    }

    public void cambiarActiva(boolean activa, String usuario) {
        this.activa = activa;
        this.modificadoPor = usuario;
        this.modificadoEn = Instant.now();
    }

    public String getCodigo() {
        return codigo;
    }

    public boolean isActiva() {
        return activa;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }

    public Instant getModificadoEn() {
        return modificadoEn;
    }
}
