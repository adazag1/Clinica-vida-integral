package co.edu.ucentral.clinica.comun.excepciones;

import java.time.Instant;
import java.util.Map;

/** Formato único de error que recibe el frontend. */
public record ApiError(Instant fecha, int estado, String codigo, String mensaje, Map<String, String> campos) {

    public static ApiError de(int estado, String codigo, String mensaje) {
        return new ApiError(Instant.now(), estado, codigo, mensaje, Map.of());
    }
}
