package co.edu.ucentral.clinica.facturacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RFRC-005 / RFRC-006 / RFRC-012: aplica el contrato a los cargos de la cuenta y arma lo que se
 * facturaría. Se usa igual para la simulación y para la factura definitiva, así lo que se revisa es
 * exactamente lo que se emite.
 *
 * <ul>
 *   <li>Excluidos del contrato: los paga el paciente, no van en la factura a la aseguradora.</li>
 *   <li>Capitación: los servicios quedan en cero (los cubre la cápita), pero se reportan.</li>
 *   <li>Tope de cantidad: lo que excede no se factura.</li>
 *   <li>Paquete: si la cuenta tiene todos sus servicios, se cobran como una línea al precio pactado.</li>
 *   <li>Tope de la cuenta: si el subtotal lo supera, se ajusta hasta el tope.</li>
 * </ul>
 */
public final class Liquidacion {

    public record Linea(String codigo, String descripcion, int cantidad, BigDecimal valorUnitario, BigDecimal valorTotal,
                        String nota, List<Long> cargoIds) {
    }

    public record Resultado(List<Linea> lineas, List<Linea> lineasPaciente, BigDecimal subtotal, BigDecimal ajustes,
                            BigDecimal total, BigDecimal valorPaciente, List<String> advertencias, List<String> bloqueos) {
        public boolean facturable() {
            return bloqueos.isEmpty();
        }
    }

    private Liquidacion() {
    }

    public static Resultado calcular(Cuenta cuenta) {
        List<Cargo> cargos = cuenta.activos().stream().filter(c -> c.getFacturaId() == null).toList();
        Contrato contrato = cuenta.getContrato();
        List<String> advertencias = new ArrayList<>();
        List<String> bloqueos = bloqueos(cuenta, cargos);

        // Lo que no cubre el contrato lo paga el paciente
        List<Linea> paciente = agrupar(cargos.stream().filter(Cargo::isACargoPaciente).toList(), "Excluido del contrato");
        BigDecimal valorPaciente = suma(paciente);
        if (!paciente.isEmpty()) {
            advertencias.add(paciente.size() + " servicio(s) excluidos del contrato quedan a cargo del paciente ($"
                    + dinero(valorPaciente) + ").");
        }
        List<Cargo> cubiertos = cargos.stream().filter(c -> !c.isACargoPaciente()).toList();

        if (contrato.getModalidad() == Contrato.Modalidad.CAPITACION) {
            List<Linea> lineas = agrupar(cubiertos, "Cubierto por la cápita").stream()
                    .map(l -> new Linea(l.codigo(), l.descripcion(), l.cantidad(), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                            "Cubierto por la cápita", l.cargoIds()))
                    .toList();
            advertencias.add("Contrato por capitación: los servicios se reportan pero no generan cobro por evento.");
            return new Resultado(lineas, paciente, cero(), cero(), cero(), valorPaciente, advertencias, bloqueos);
        }

        // Cantidades por servicio, respetando el tope de cada uno
        Map<ServicioFacturable, List<Cargo>> porServicio = new LinkedHashMap<>();
        cubiertos.stream().sorted(Comparator.comparing(Cargo::getFechaServicio))
                .forEach(c -> porServicio.computeIfAbsent(c.getServicio(), k -> new ArrayList<>()).add(c));
        Map<ServicioFacturable, Integer> disponibles = new LinkedHashMap<>();
        porServicio.forEach((s, lista) -> {
            int cantidad = lista.stream().mapToInt(Cargo::getCantidad).sum();
            Integer tope = contrato.regla(s, ContratoRegla.Tipo.TOPE_CANTIDAD).map(ContratoRegla::getCantidadMax).orElse(null);
            if (tope != null && cantidad > tope) {
                advertencias.add(s.getNombre() + ": " + (cantidad - tope) + " unidad(es) sobre el tope del contrato (" + tope
                        + ") no se facturan.");
                cantidad = tope;
            }
            disponibles.put(s, cantidad);
        });

        List<Linea> lineas = new ArrayList<>();
        // Paquetes: si están todos sus servicios, una unidad de cada uno pasa al paquete
        for (Paquete p : contrato.getPaquetes()) {
            while (!p.getServicios().isEmpty() && p.getServicios().stream().allMatch(s -> disponibles.getOrDefault(s, 0) > 0)) {
                List<Long> ids = new ArrayList<>();
                p.getServicios().forEach(s -> {
                    disponibles.merge(s, -1, Integer::sum);
                    porServicio.get(s).stream().findFirst().ifPresent(c -> ids.add(c.getId()));
                });
                lineas.add(new Linea(p.getCodigo(), p.getNombre(), 1, p.getPrecio().setScale(2), p.getPrecio().setScale(2),
                        "Paquete del contrato", ids));
                advertencias.add("Se aplicó el paquete «" + p.getNombre() + "».");
            }
        }

        porServicio.forEach((s, lista) -> {
            int cantidad = disponibles.get(s);
            if (cantidad <= 0) return;
            BigDecimal unitario = lista.getFirst().getValorUnitario();
            lineas.add(new Linea(s.getCodigo(), s.getNombre(), cantidad, unitario,
                    unitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP), null,
                    lista.stream().map(Cargo::getId).toList()));
        });

        BigDecimal subtotal = suma(lineas);
        BigDecimal ajustes = cero();
        BigDecimal tope = contrato.getTopeCuenta();
        if (tope != null && subtotal.compareTo(tope) > 0) {
            ajustes = tope.subtract(subtotal).setScale(2, RoundingMode.HALF_UP);
            advertencias.add("El total supera el tope del contrato por cuenta ($" + dinero(tope) + "): se ajusta.");
        }
        return new Resultado(lineas, paciente, subtotal, ajustes, subtotal.add(ajustes), valorPaciente, advertencias, bloqueos);
    }

    /** RFRC-003 / RFRC-004: lo que impide emitir la factura. */
    private static List<String> bloqueos(Cuenta cuenta, List<Cargo> cargos) {
        List<String> bloqueos = new ArrayList<>();
        if (cargos.isEmpty()) bloqueos.add("la cuenta no tiene cargos por facturar");
        cargos.stream().filter(c -> !c.isTieneSoporte())
                .forEach(c -> bloqueos.add(c.getServicio().getNombre() + " no tiene soporte clínico"));
        if (!cuenta.isUrgencia() && cuenta.getNumeroAutorizacion() == null) {
            cargos.stream().filter(c -> c.isRequiereAutorizacion() && !c.isACargoPaciente())
                    .forEach(c -> bloqueos.add(c.getServicio().getNombre() + " exige autorización de la aseguradora"));
        }
        return bloqueos;
    }

    private static List<Linea> agrupar(List<Cargo> cargos, String nota) {
        Map<String, List<Cargo>> grupos = new LinkedHashMap<>();
        cargos.forEach(c -> grupos.computeIfAbsent(c.getServicio().getCodigo() + "|" + c.getValorUnitario(), k -> new ArrayList<>()).add(c));
        return grupos.values().stream().map(lista -> {
            Cargo c = lista.getFirst();
            int cantidad = lista.stream().mapToInt(Cargo::getCantidad).sum();
            return new Linea(c.getServicio().getCodigo(), c.getServicio().getNombre(), cantidad, c.getValorUnitario(),
                    c.getValorUnitario().multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP), nota,
                    lista.stream().map(Cargo::getId).toList());
        }).toList();
    }

    private static BigDecimal suma(List<Linea> lineas) {
        return lineas.stream().map(Linea::valorTotal).reduce(cero(), BigDecimal::add);
    }

    private static BigDecimal cero() {
        return BigDecimal.ZERO.setScale(2);
    }

    static String dinero(BigDecimal v) {
        return String.format(java.util.Locale.forLanguageTag("es-CO"), "%,.0f", v);
    }
}
