package co.edu.ucentral.clinica.facturacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Proveedor por defecto: no transmite a la DIAN. La factura queda con estado SIMULADA para que
 * nadie la tome por una factura electrónica válida ante la autoridad tributaria. La integración con
 * el proveedor real se registra como otro bean marcado con {@code @Primary}.
 */
@Component
public class ProveedorSimulado implements ProveedorFacturaElectronica {

    private static final Logger log = LoggerFactory.getLogger(ProveedorSimulado.class);

    @Override
    public Resultado enviar(String numeroFactura, String cufe, String xml) {
        log.info("Factura {} generada en modo simulado (sin proveedor tecnológico configurado)", numeroFactura);
        return new Resultado("SIMULADA", "No se transmitió a la DIAN: falta contratar el proveedor tecnológico.");
    }
}
