package co.edu.ucentral.clinica.ia;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que una regla encontró. El puntaje es el de una escala clínica publicada (qSOFA, KDIGO,
 * TFG CKD-EPI...), no una probabilidad calculada por un modelo entrenado.
 *
 * @param evidencia los datos concretos que dispararon la alerta, con su valor y fecha
 */
public record Hallazgo(Severidad severidad, BigDecimal puntaje, String escala, String titulo, String detalle,
                       List<String> evidencia, String recomendacion) {

    public enum Severidad {
        INFO("Informativa"),
        ALERTA("Alerta"),
        CRITICA("Crítica");

        private final String descripcion;

        Severidad(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }
}
