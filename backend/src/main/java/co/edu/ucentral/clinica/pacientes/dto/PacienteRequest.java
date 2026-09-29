package co.edu.ucentral.clinica.pacientes.dto;

import co.edu.ucentral.clinica.pacientes.Regimen;
import co.edu.ucentral.clinica.pacientes.Sexo;
import co.edu.ucentral.clinica.pacientes.TipoDocumento;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Datos para crear o actualizar un paciente. Las reglas que dependen del tipo de documento
 * (formato del número, campos obligatorios) se validan en PacienteService.
 *
 * @param confirmarNoDuplicado el usuario revisó los posibles duplicados y confirma que es otra persona
 * @param motivo               obligatorio al actualizar (RFADM-011)
 */
public record PacienteRequest(
        @NotNull(message = "Seleccione el tipo de documento") TipoDocumento tipoDocumento,
        @Size(max = 20, message = "Máximo 20 caracteres") String numeroDocumento,
        @NotBlank(message = "Ingrese los nombres") @Size(max = 100, message = "Máximo 100 caracteres") String nombres,
        @NotBlank(message = "Ingrese los apellidos") @Size(max = 100, message = "Máximo 100 caracteres") String apellidos,
        @PastOrPresent(message = "La fecha de nacimiento no puede ser futura") LocalDate fechaNacimiento,
        Sexo sexo,
        @Pattern(regexp = "^$|^[0-9+()\\s-]{7,20}$", message = "Teléfono inválido") String telefono,
        @Email(message = "Correo electrónico inválido") @Size(max = 120, message = "Máximo 120 caracteres") String email,
        @Size(max = 200, message = "Máximo 200 caracteres") String direccion,
        @Size(max = 120, message = "Máximo 120 caracteres") String aseguradora,
        Regimen regimen,
        boolean confirmarNoDuplicado,
        @Size(max = 500, message = "Máximo 500 caracteres") String motivo) {
}
