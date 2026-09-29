package co.edu.ucentral.clinica.agenda.dto;

import co.edu.ucentral.clinica.agenda.Especialidad;
import co.edu.ucentral.clinica.agenda.Sede;
import co.edu.ucentral.clinica.agenda.TipoCita;

import java.util.List;

/**
 * Parametrización para los filtros y formularios de la agenda.
 *
 * @param miProfesionalId agenda propia del usuario actual si es un médico con agenda (null si no)
 */
public record CatalogosDto(List<SedeDto> sedes, List<EspecialidadDto> especialidades, List<TipoCitaDto> tipos,
                           List<ProfesionalDto> profesionales, Long miProfesionalId) {

    public record SedeDto(Long id, String nombre, String ciudad, String direccion) {
        public static SedeDto de(Sede s) {
            return new SedeDto(s.getId(), s.getNombre(), s.getCiudad(), s.getDireccion());
        }
    }

    public record EspecialidadDto(Long id, String nombre) {
        public static EspecialidadDto de(Especialidad e) {
            return new EspecialidadDto(e.getId(), e.getNombre());
        }
    }

    public record TipoCitaDto(Long id, String codigo, String nombre, int duracionMinutos, boolean requierePreparacion,
                              String instruccionesPreparacion) {
        public static TipoCitaDto de(TipoCita t) {
            return new TipoCitaDto(t.getId(), t.getCodigo(), t.getNombre(), t.getDuracionMinutos(),
                    t.isRequierePreparacion(), t.getInstruccionesPreparacion());
        }
    }
}
