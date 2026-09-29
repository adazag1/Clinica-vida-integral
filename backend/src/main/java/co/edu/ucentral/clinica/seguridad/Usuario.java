package co.edu.ucentral.clinica.seguridad;

import co.edu.ucentral.clinica.comun.modelo.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "usuario")
public class Usuario extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "nombre_completo", nullable = false)
    private String nombreCompleto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    private String cargo;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(nullable = false)
    private boolean bloqueado;

    @Column(name = "ultimo_acceso")
    private Instant ultimoAcceso;

    protected Usuario() {
    }

    public Usuario(String username, String email, String passwordHash, String nombreCompleto,
                   Rol rol, String cargo) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nombreCompleto = nombreCompleto;
        this.rol = rol;
        this.cargo = cargo;
    }

    /** Suma un intento fallido y bloquea la cuenta al llegar al máximo permitido. */
    public void registrarIntentoFallido(int maxIntentos) {
        intentosFallidos++;
        if (intentosFallidos >= maxIntentos) {
            bloqueado = true;
        }
    }

    public void registrarAccesoExitoso() {
        intentosFallidos = 0;
        ultimoAcceso = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public Rol getRol() {
        return rol;
    }

    public String getCargo() {
        return cargo;
    }

    public boolean isActivo() {
        return activo;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public boolean isBloqueado() {
        return bloqueado;
    }

    public Instant getUltimoAcceso() {
        return ultimoAcceso;
    }
}
