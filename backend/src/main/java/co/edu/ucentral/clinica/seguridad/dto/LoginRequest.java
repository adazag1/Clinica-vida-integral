package co.edu.ucentral.clinica.seguridad.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Ingrese su usuario o correo") String usuario,
        @NotBlank(message = "Ingrese su contraseña") String password) {
}
