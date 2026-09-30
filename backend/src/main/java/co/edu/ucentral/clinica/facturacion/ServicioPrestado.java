package co.edu.ucentral.clinica.facturacion;

import java.time.Instant;

/**
 * RFRC-002 / RFRC-013: un módulo clínico documentó un servicio (consulta firmada, resultado
 * validado, informe firmado, dosis administrada). Facturación genera el cargo después de que el
 * acto clínico se confirma: un problema en la facturación nunca impide firmar o administrar.
 *
 * @param claveOrigen   identifica el servicio facturable (LAB:HEMOGRAMA, IMG:RX_TORAX, MED:24...)
 * @param modulo        módulo de origen; con {@code referencia} evita cargos duplicados (RFRC-011)
 * @param soporte       el documento clínico que respalda el cargo (RFRC-003)
 */
public record ServicioPrestado(Long pacienteId, Long consultaId, Long episodioId, String claveOrigen, int cantidad,
                               String modulo, String referencia, String soporte, String enlace, Instant fecha) {
}
