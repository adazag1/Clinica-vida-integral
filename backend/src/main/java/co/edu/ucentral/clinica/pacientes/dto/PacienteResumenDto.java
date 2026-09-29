package co.edu.ucentral.clinica.pacientes.dto;

import co.edu.ucentral.clinica.pacientes.Paciente;

import java.time.LocalDate;

/** Datos mínimos para listas y encabezados (búsqueda, duplicados, lista de espera). */
public record PacienteResumenDto(Long id, String tipoDocumento, String numeroDocumento,
                                 boolean identificacionTemporal, String nombreCompleto,
                                 LocalDate fechaNacimiento, Integer edad, String sexo, String telefono,
                                 String aseguradora) {

    public static PacienteResumenDto de(Paciente p) {
        return new PacienteResumenDto(p.getId(), p.getTipoDocumento().name(), p.getNumeroDocumento(),
                p.isIdentificacionTemporal(), p.getNombreCompleto(), p.getFechaNacimiento(), p.getEdad(),
                p.getSexo() == null ? null : p.getSexo().name(), p.getTelefono(), p.getAseguradora());
    }
}
