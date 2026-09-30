package co.edu.ucentral.clinica.facturacion.dto;

import co.edu.ucentral.clinica.facturacion.Contrato;
import co.edu.ucentral.clinica.facturacion.ContratoRegla;
import co.edu.ucentral.clinica.facturacion.Glosa;
import co.edu.ucentral.clinica.facturacion.TipoServicio;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Cuerpos de las peticiones de facturación. */
public final class FacturacionSolicitudes {

    private FacturacionSolicitudes() {
    }

    /** Cargo que no viene de un módulo clínico (insumos, procedimientos, estancia). */
    public record CargoManualRequest(
            @NotNull(message = "Seleccione el servicio") Long servicioId,
            @NotNull @Min(value = 1, message = "Mínimo 1") @Max(value = 999, message = "Máximo 999") Integer cantidad,
            @Size(max = 300, message = "Máximo 300 caracteres") String soporte,
            @Size(max = 200) String soporteEnlace,
            @PastOrPresent(message = "No puede ser una fecha futura") Instant fechaServicio) {
    }

    public record SoporteRequest(
            @NotBlank(message = "Describa el soporte (orden, nota, registro)") @Size(max = 300) String soporte,
            @Size(max = 200) String enlace) {
    }

    public record MotivoRequest(
            @NotBlank(message = "Registre el motivo") @Size(max = 300, message = "Máximo 300 caracteres") String motivo) {
    }

    public record AutorizacionRequest(
            @NotBlank(message = "Ingrese el número de autorización") @Size(max = 60) String numero) {
    }

    /** @param respuesta qué se corrigió, si la cuenta vuelve de una devolución */
    public record EnvioAuditoriaRequest(@Size(max = 1000, message = "Máximo 1000 caracteres") String respuesta) {
    }

    public record GlosaRequest(
            @NotNull(message = "Seleccione el concepto") Glosa.Concepto concepto,
            @NotBlank @Pattern(regexp = "^\\d{3}$", message = "El código de glosa tiene 3 dígitos (p. ej. 301)") String codigo,
            @NotBlank(message = "Describa la glosa") @Size(max = 500) String descripcion,
            @NotNull @DecimalMin(value = "1", message = "El valor debe ser mayor que cero") BigDecimal valor,
            Long cargoId,
            @NotNull(message = "Indique cuándo se recibió") @PastOrPresent(message = "No puede ser una fecha futura") LocalDate recibidaEn) {
    }

    public record RespuestaGlosaRequest(
            @NotNull @PositiveOrZero(message = "No puede ser negativo") BigDecimal valorAceptado,
            @NotBlank(message = "Escriba la respuesta a la aseguradora") @Size(max = 1000) String respuesta) {
    }

    public record PagoRequest(
            @NotNull @DecimalMin(value = "1", message = "El valor debe ser mayor que cero") BigDecimal valor,
            @NotNull(message = "Indique la fecha del pago") @PastOrPresent(message = "No puede ser una fecha futura") LocalDate fecha,
            @NotBlank(message = "Indique el medio de pago") @Size(max = 30) String medio,
            @Size(max = 60) String referencia) {
    }

    public record ServicioRequest(
            @NotBlank(message = "Ingrese el código") @Size(max = 20) String codigo,
            @NotBlank(message = "Ingrese el nombre") @Size(max = 200) String nombre,
            @NotNull(message = "Seleccione el tipo") TipoServicio tipo,
            @NotNull @DecimalMin(value = "0", message = "No puede ser negativa") BigDecimal tarifaBase,
            Boolean requiereAutorizacion,
            Boolean activo) {
    }

    public record ContratoRequest(
            @NotBlank(message = "Ingrese la aseguradora") @Size(max = 120) String aseguradora,
            @NotBlank(message = "Ingrese el nombre del contrato") @Size(max = 150) String nombre,
            @NotNull(message = "Seleccione la modalidad") Contrato.Modalidad modalidad,
            @NotNull @DecimalMin(value = "0.10", message = "Entre 0,10 y 3") @DecimalMax(value = "3", message = "Entre 0,10 y 3") BigDecimal factorTarifa,
            @NotNull @Min(value = 0, message = "Entre 0 y 365") @Max(value = 365, message = "Entre 0 y 365") Integer diasPago,
            @DecimalMin(value = "1", message = "Debe ser mayor que cero") BigDecimal topeCuenta,
            @NotNull(message = "Indique desde cuándo rige") LocalDate vigenteDesde,
            LocalDate vigenteHasta,
            Boolean activo) {
    }

    public record ReglaRequest(
            @NotNull(message = "Seleccione el servicio") Long servicioId,
            @NotNull(message = "Seleccione el tipo de regla") ContratoRegla.Tipo tipo,
            @DecimalMin(value = "0", message = "No puede ser negativa") BigDecimal valor,
            @Min(value = 1, message = "Mínimo 1") Integer cantidadMax) {
    }

    public record PaqueteRequest(
            @NotBlank @Size(max = 20) String codigo,
            @NotBlank @Size(max = 150) String nombre,
            @NotNull @DecimalMin(value = "1", message = "Debe ser mayor que cero") BigDecimal precio,
            @NotEmpty(message = "Seleccione los servicios incluidos") @Size(min = 2, message = "Un paquete agrupa al menos dos servicios") List<Long> servicioIds) {
    }
}
