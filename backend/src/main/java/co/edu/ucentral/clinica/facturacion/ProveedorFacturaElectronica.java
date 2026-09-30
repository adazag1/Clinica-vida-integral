package co.edu.ucentral.clinica.facturacion;

/**
 * RFRC-009: envío de la factura electrónica a la DIAN a través de un proveedor tecnológico
 * autorizado. La implementación real depende del proveedor que contrate la clínica; mientras
 * tanto se usa {@link ProveedorSimulado}, que no envía nada.
 */
public interface ProveedorFacturaElectronica {

    /** @param estado SIMULADA, ENVIADA, ACEPTADA o RECHAZADA; mensaje, la respuesta del proveedor */
    record Resultado(String estado, String mensaje) {
    }

    Resultado enviar(String numeroFactura, String cufe, String xml);
}
