package co.edu.ucentral.clinica.admisiones.dto;

import co.edu.ucentral.clinica.admisiones.Consentimiento;

import java.time.Instant;

public record ConsentimientoDto(Long id, String tipo, String tipoDescripcion, String otorgadoPor, String parentesco,
                                boolean aceptado, String observaciones, String registradoPor, Instant registradoEn) {

    public static ConsentimientoDto de(Consentimiento c) {
        return new ConsentimientoDto(c.getId(), c.getTipo().name(), c.getTipo().getDescripcion(), c.getOtorgadoPor(),
                c.getParentesco(), c.isAceptado(), c.getObservaciones(), c.getCreadoPor(), c.getCreadoEn());
    }
}
