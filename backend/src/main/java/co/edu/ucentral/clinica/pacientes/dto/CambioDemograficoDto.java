package co.edu.ucentral.clinica.pacientes.dto;

import co.edu.ucentral.clinica.pacientes.CambioDemografico;

import java.time.Instant;

public record CambioDemograficoDto(Long id, String campo, String valorAnterior, String valorNuevo, String motivo,
                                   String usuario, Instant fecha) {

    public static CambioDemograficoDto de(CambioDemografico c) {
        return new CambioDemograficoDto(c.getId(), c.getCampo(), c.getValorAnterior(), c.getValorNuevo(),
                c.getMotivo(), c.getUsuario(), c.getFecha());
    }
}
