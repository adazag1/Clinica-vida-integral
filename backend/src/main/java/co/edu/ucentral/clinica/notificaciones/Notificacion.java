package co.edu.ucentral.clinica.notificaciones;

import co.edu.ucentral.clinica.seguridad.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Aviso interno que aparece en la campana. Va a un rol completo (p. ej. todos los médicos) o a
 * un usuario puntual. Un aviso de rol se marca leído para todo el rol: basta con que uno lo atienda.
 */
@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_destino")
    private Rol rolDestino;

    @Column(name = "usuario_destino")
    private String usuarioDestino;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false)
    private String mensaje;

    /** Ruta del frontend a la que lleva el aviso, p. ej. /admisiones/15. */
    private String enlace;

    @Column(nullable = false)
    private Instant fecha;

    @Column(name = "leida_en")
    private Instant leidaEn;

    protected Notificacion() {
    }

    public Notificacion(Rol rolDestino, String usuarioDestino, String titulo, String mensaje, String enlace) {
        this.rolDestino = rolDestino;
        this.usuarioDestino = usuarioDestino;
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.enlace = enlace;
        this.fecha = Instant.now();
    }

    public void marcarLeida() {
        if (leidaEn == null) {
            leidaEn = Instant.now();
        }
    }

    public boolean esPara(String usuario, Rol rol) {
        return usuario.equalsIgnoreCase(usuarioDestino) || (rolDestino != null && rolDestino == rol);
    }

    public Long getId() {
        return id;
    }

    public Rol getRolDestino() {
        return rolDestino;
    }

    public String getUsuarioDestino() {
        return usuarioDestino;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String getEnlace() {
        return enlace;
    }

    public Instant getFecha() {
        return fecha;
    }

    public Instant getLeidaEn() {
        return leidaEn;
    }
}
