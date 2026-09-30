package co.edu.ucentral.clinica.facturacion;

import java.util.EnumSet;
import java.util.Set;

/** RFRC-007: ciclo de la cuenta, desde el primer cargo hasta el cierre financiero. */
public enum EstadoCuenta {
    ABIERTA("Abierta: acumulando cargos"),
    EN_AUDITORIA("En auditoría"),
    DEVUELTA("Devuelta por auditoría"),
    AUDITADA("Auditada: lista para facturar"),
    FACTURADA("Facturada"),
    GLOSADA("Glosada"),
    PAGADA("Pagada"),
    CERRADA("Cerrada");

    private final String descripcion;

    EstadoCuenta(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Estados en los que todavía se pueden agregar o anular cargos. */
    public static final Set<EstadoCuenta> EDITABLES = EnumSet.of(ABIERTA, DEVUELTA);
}
