package co.edu.ucentral.clinica.admisiones.dto;

import co.edu.ucentral.clinica.admisiones.NivelTriage;
import co.edu.ucentral.clinica.admisiones.Triage;

import java.time.Instant;

public record TriageDto(SignosVitales signos, String sintomaPrincipal, int nivelSugerido, int nivelAsignado,
                        String descripcionNivel, String justificacionAjuste, String clasificadoPor,
                        Instant clasificadoEn, String modificadoPor, Instant modificadoEn) {

    public static TriageDto de(Triage t) {
        return new TriageDto(t.getSignosVitales(), t.getSintomaPrincipal(), t.getNivelSugerido(),
                t.getNivelAsignado(), NivelTriage.descripcion(t.getNivelAsignado()), t.getJustificacionAjuste(),
                t.getCreadoPor(), t.getCreadoEn(), t.getModificadoPor(), t.getModificadoEn());
    }
}
