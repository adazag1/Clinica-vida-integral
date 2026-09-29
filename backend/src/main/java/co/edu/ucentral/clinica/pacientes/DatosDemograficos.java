package co.edu.ucentral.clinica.pacientes;

import java.time.LocalDate;

/** Datos demográficos ya validados y limpios que se aplican a un paciente. */
public record DatosDemograficos(TipoDocumento tipoDocumento, String numeroDocumento, String nombres,
                                String apellidos, LocalDate fechaNacimiento, Sexo sexo, String telefono,
                                String email, String direccion, String aseguradora, Regimen regimen) {
}
