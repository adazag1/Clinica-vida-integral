package co.edu.ucentral.clinica.historia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Diagnóstico del catálogo CIE-10. Un código no se borra: se inactiva (deja de ofrecerse) y el
 * cambio queda en el historial del catálogo (RFAU-005).
 */
@Entity
@Table(name = "diagnostico")
public class Diagnostico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;

    protected Diagnostico() {
    }

    public Diagnostico(String codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
    }

    public void renombrar(String nombre) {
        this.nombre = nombre;
    }

    public void cambiarActivo(boolean activo) {
        this.activo = activo;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActivo() {
        return activo;
    }
}
