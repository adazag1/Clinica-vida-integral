package co.edu.ucentral.clinica.pacientes.dto;

import co.edu.ucentral.clinica.pacientes.Paciente;

import java.time.Instant;
import java.time.LocalDate;

public record PacienteDto(Long id, String tipoDocumento, String tipoDocumentoDescripcion, String numeroDocumento,
                          boolean identificacionTemporal, String nombres, String apellidos, String nombreCompleto,
                          LocalDate fechaNacimiento, Integer edad, String sexo, String telefono, String email,
                          String direccion, String aseguradora, String regimen, Instant creadoEn, String creadoPor,
                          Instant modificadoEn, String modificadoPor) {

    public static PacienteDto de(Paciente p) {
        return new PacienteDto(p.getId(), p.getTipoDocumento().name(), p.getTipoDocumento().getDescripcion(),
                p.getNumeroDocumento(), p.isIdentificacionTemporal(), p.getNombres(), p.getApellidos(),
                p.getNombreCompleto(), p.getFechaNacimiento(), p.getEdad(),
                p.getSexo() == null ? null : p.getSexo().name(), p.getTelefono(), p.getEmail(), p.getDireccion(),
                p.getAseguradora(), p.getRegimen() == null ? null : p.getRegimen().name(), p.getCreadoEn(),
                p.getCreadoPor(), p.getModificadoEn(), p.getModificadoPor());
    }
}
