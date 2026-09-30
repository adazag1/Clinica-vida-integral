package co.edu.ucentral.clinica.reportes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Meta o línea base de un indicador de directivos. Mientras sea de prueba, el tablero lo advierte. */
@Entity
@Table(name = "parametro_indicador")
public class ParametroIndicador {

    @Id
    private String clave;

    @Column(nullable = false)
    private String valor;

    @Column(nullable = false)
    private String descripcion;

    private String unidad;

    @Column(nullable = false)
    private boolean prueba;

    @Column(nullable = false)
    private int orden;

    @Column(name = "modificado_por")
    private String modificadoPor;

    @Column(name = "modificado_en")
    private Instant modificadoEn;

    protected ParametroIndicador() {
    }

    public void cambiar(String valor, String usuario) {
        this.valor = valor;
        this.prueba = false;
        this.modificadoPor = usuario;
        this.modificadoEn = Instant.now();
    }

    public String getClave() {
        return clave;
    }

    public String getValor() {
        return valor;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getUnidad() {
        return unidad;
    }

    public boolean isPrueba() {
        return prueba;
    }

    public int getOrden() {
        return orden;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }

    public Instant getModificadoEn() {
        return modificadoEn;
    }
}
