package co.edu.ucentral.clinica.seguridad.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiraEn, UsuarioActualDto usuario) {
}
