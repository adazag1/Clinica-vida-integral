package co.edu.ucentral.clinica.agenda.dto;

import co.edu.ucentral.clinica.agenda.Profesional;

public record ProfesionalDto(Long id, String nombre, Long especialidadId, String especialidad, Long sedeId,
                             String sede, String consultorio, Long usuarioId) {

    public static ProfesionalDto de(Profesional p) {
        return new ProfesionalDto(p.getId(), p.getNombre(), p.getEspecialidad().getId(),
                p.getEspecialidad().getNombre(), p.getSede().getId(), p.getSede().getNombre(), p.getConsultorio(),
                p.getUsuarioId());
    }
}
