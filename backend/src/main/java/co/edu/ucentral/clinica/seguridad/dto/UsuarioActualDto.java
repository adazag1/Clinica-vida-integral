package co.edu.ucentral.clinica.seguridad.dto;

import co.edu.ucentral.clinica.seguridad.Usuario;

public record UsuarioActualDto(Long id, String username, String email, String nombreCompleto,
                               String rol, String rolDescripcion, String cargo) {

    public static UsuarioActualDto de(Usuario u) {
        return new UsuarioActualDto(u.getId(), u.getUsername(), u.getEmail(), u.getNombreCompleto(),
                u.getRol().name(), u.getRol().getDescripcion(), u.getCargo());
    }
}
