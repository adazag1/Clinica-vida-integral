package co.edu.ucentral.clinica.ia.dto;

import co.edu.ucentral.clinica.ia.AlertaIa;
import co.edu.ucentral.clinica.ia.ReglaClinica;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class IaDtos {

    private IaDtos() {
    }

    public record AlertaDto(Long id, String regla, String reglaNombre, String referencia, String severidad,
                            String severidadDescripcion, BigDecimal puntaje, String escala, String titulo, String detalle,
                            List<String> evidencia, String recomendacion, Instant generadaEn, boolean vigente,
                            Instant cerradaEn, String retroalimentacion, String comentario, String retroPor) {
        public static AlertaDto de(AlertaIa a, ReglaClinica regla) {
            return new AlertaDto(a.getId(), a.getRegla(), regla == null ? a.getRegla() : regla.nombre(),
                    regla == null ? null : regla.referencia(), a.getSeveridad().name(), a.getSeveridad().getDescripcion(),
                    a.getPuntaje(), a.getEscala(), a.getTitulo(), a.getDetalle(), a.evidencias(), a.getRecomendacion(),
                    a.getGeneradaEn(), a.isVigente(), a.getCerradaEn(),
                    a.getRetroalimentacion() == null ? null : a.getRetroalimentacion().name(), a.getComentario(), a.getRetroPor());
        }
    }

    /**
     * Precisión = útiles / (útiles + no útiles). Es la precisión percibida por los médicos sobre las
     * alertas que calificaron, no una sensibilidad: las alertas que el motor no generó no se ven aquí.
     */
    public record ReglaDto(String codigo, String nombre, String descripcion, String referencia, boolean activa,
                           String modificadoPor, Instant modificadoEn, long alertas, long utiles, long noUtiles,
                           BigDecimal precision, List<String> comentariosRecientes) {
    }

    public record EstadoIaDto(boolean reglasActivas, int totalReglas, boolean asistenteHabilitado, boolean asistenteConfigurado,
                              String modelo, String motivo, long consultasAsistente30d, BigDecimal costoAsistente30dUsd) {
    }

    public record RespuestaAsistenteDto(String tipo, String texto, String modelo, Integer tokensEntrada, Integer tokensSalida,
                                        BigDecimal costoUsd, String advertencia, String textoEnviado) {
    }
}
