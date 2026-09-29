package co.edu.ucentral.clinica.historia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Medicamento del catálogo. El principio activo, la familia y los sinónimos permiten
 * detectar alergias aunque se hayan registrado con otro nombre (RFMT-003).
 */
@Entity
@Table(name = "medicamento")
public class Medicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(name = "principio_activo", nullable = false)
    private String principioActivo;

    @Column(nullable = false)
    private String familia;

    private String sinonimos;

    @Column(nullable = false)
    private String presentacion;

    @Column(nullable = false)
    private String via;

    protected Medicamento() {
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getPrincipioActivo() {
        return principioActivo;
    }

    public String getFamilia() {
        return familia;
    }

    public String getSinonimos() {
        return sinonimos;
    }

    public String getPresentacion() {
        return presentacion;
    }

    public String getVia() {
        return via;
    }
}
