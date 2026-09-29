package co.edu.ucentral.clinica.documentos;

import java.time.LocalDate;

/**
 * RFGAC-009: un módulo clínico produjo un documento (consulta firmada, resultado validado, informe
 * firmado). Gestión documental lo registra en la historia del paciente con un enlace al original.
 * Se publica dentro de la misma transacción: si el registro falla, falla la firma.
 *
 * @param modulo     módulo que lo generó (CONSULTA, LABORATORIO, IMAGENES, PATOLOGIA)
 * @param referencia identificador en ese módulo; repetir la publicación registra una nueva versión
 */
public record DocumentoGenerado(Long pacienteId, Long episodioId, TipoDocumento tipo, String titulo, String servicio,
                                String profesional, String modulo, String referencia, String enlace, LocalDate fecha,
                                String motivoVersion) {
}
