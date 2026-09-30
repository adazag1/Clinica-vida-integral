package co.edu.ucentral.clinica.facturacion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LiquidacionTest {

    private final ServicioFacturable consulta = servicio("890701", "Consulta de urgencias", 78000, false);
    private final ServicioFacturable troponina = servicio("903437", "Troponina I", 64000, false);
    private final ServicioFacturable rx = servicio("871121", "Rx de tórax", 52000, false);
    private final ServicioFacturable hemograma = servicio("902210", "Hemograma", 18500, false);
    private final ServicioFacturable perfil = servicio("903868", "Perfil lipídico", 32000, false);
    private final ServicioFacturable tac = servicio("879111", "TC de cráneo", 245000, true);

    private static ServicioFacturable servicio(String codigo, String nombre, long tarifa, boolean autorizacion) {
        return new ServicioFacturable(codigo, nombre, TipoServicio.PROCEDIMIENTO, BigDecimal.valueOf(tarifa), autorizacion, "prueba");
    }

    private static Contrato contrato(Contrato.Modalidad modalidad, String factor, Long tope) {
        return new Contrato("Aseguradora", "Contrato", modalidad, new BigDecimal(factor), 30, tope == null ? null : BigDecimal.valueOf(tope),
                LocalDate.of(2026, 1, 1), null, "prueba");
    }

    private static Cuenta cuenta(Contrato c, boolean urgencia, String autorizacion) {
        return new Cuenta("CTA-1", 1L, null, c, "Aseguradora", autorizacion, urgencia);
    }

    private static void cargar(Cuenta cuenta, ServicioFacturable s, int cantidad, String soporte) {
        cuenta.getCargos().add(new Cargo(cuenta, s, cantidad, FacturacionService.tarifa(cuenta.getContrato(), s), soporte, null, null,
                null, Instant.now(), "prueba"));
    }

    @Test
    void aplicaElFactorDelContratoYLaTarifaPactada() {
        Contrato c = contrato(Contrato.Modalidad.EVENTO, "0.900", null);
        c.getReglas().add(new ContratoRegla(c, tac, ContratoRegla.Tipo.TARIFA_PACTADA, BigDecimal.valueOf(210000), null));
        assertThat(FacturacionService.tarifa(c, consulta).valorUnitario()).isEqualByComparingTo("70200");
        assertThat(FacturacionService.tarifa(c, tac).valorUnitario()).isEqualByComparingTo("210000");
        assertThat(FacturacionService.tarifa(c, tac).requiereAutorizacion()).isTrue();
    }

    @Test
    void excluidoLoPagaElPacienteYElTopeDeCantidadNoSeFactura() {
        Contrato c = contrato(Contrato.Modalidad.EVENTO, "1.000", null);
        c.getReglas().add(new ContratoRegla(c, perfil, ContratoRegla.Tipo.EXCLUSION, null, null));
        c.getReglas().add(new ContratoRegla(c, hemograma, ContratoRegla.Tipo.TOPE_CANTIDAD, null, 2));
        Cuenta cu = cuenta(c, false, null);
        cargar(cu, perfil, 1, "Orden 1");
        cargar(cu, hemograma, 3, "Orden 2");
        Liquidacion.Resultado r = Liquidacion.calcular(cu);
        assertThat(r.lineasPaciente()).singleElement().satisfies(l -> assertThat(l.codigo()).isEqualTo("903868"));
        assertThat(r.valorPaciente()).isEqualByComparingTo("32000");
        assertThat(r.lineas()).singleElement().satisfies(l -> {
            assertThat(l.codigo()).isEqualTo("902210");
            assertThat(l.cantidad()).isEqualTo(2);
        });
        assertThat(r.total()).isEqualByComparingTo("37000");
        assertThat(r.advertencias()).anyMatch(a -> a.contains("sobre el tope"));
    }

    @Test
    void paqueteCompletoSeCobraComoUnaLinea() {
        Contrato c = contrato(Contrato.Modalidad.EVENTO, "1.000", null);
        c.getPaquetes().add(new Paquete(c, "PQ-DT", "Dolor torácico", BigDecimal.valueOf(150000), Set.of(consulta, troponina, rx)));
        Cuenta cu = cuenta(c, true, null);
        cargar(cu, consulta, 1, "Consulta firmada");
        cargar(cu, troponina, 1, "Orden validada");
        cargar(cu, rx, 1, "Informe firmado");
        cargar(cu, hemograma, 1, "Orden validada");
        Liquidacion.Resultado r = Liquidacion.calcular(cu);
        assertThat(r.lineas()).extracting(Liquidacion.Linea::codigo).containsExactlyInAnyOrder("PQ-DT", "902210");
        assertThat(r.total()).isEqualByComparingTo("168500");
    }

    @Test
    void paqueteIncompletoSeCobraPorServicio() {
        Contrato c = contrato(Contrato.Modalidad.EVENTO, "1.000", null);
        c.getPaquetes().add(new Paquete(c, "PQ-DT", "Dolor torácico", BigDecimal.valueOf(150000), Set.of(consulta, troponina, rx)));
        Cuenta cu = cuenta(c, true, null);
        cargar(cu, consulta, 1, "Consulta firmada");
        cargar(cu, troponina, 1, "Orden validada");
        assertThat(Liquidacion.calcular(cu).total()).isEqualByComparingTo("142000");
    }

    @Test
    void topeDeLaCuentaAjustaElTotal() {
        Cuenta cu = cuenta(contrato(Contrato.Modalidad.EVENTO, "1.000", 100000L), false, null);
        cargar(cu, consulta, 1, "Consulta firmada");
        cargar(cu, troponina, 1, "Orden validada");
        Liquidacion.Resultado r = Liquidacion.calcular(cu);
        assertThat(r.subtotal()).isEqualByComparingTo("142000");
        assertThat(r.ajustes()).isEqualByComparingTo("-42000");
        assertThat(r.total()).isEqualByComparingTo("100000");
    }

    @Test
    void capitacionReportaSinCobrar() {
        Cuenta cu = cuenta(contrato(Contrato.Modalidad.CAPITACION, "1.000", null), false, null);
        cargar(cu, consulta, 1, "Consulta firmada");
        Liquidacion.Resultado r = Liquidacion.calcular(cu);
        assertThat(r.lineas()).hasSize(1);
        assertThat(r.total()).isEqualByComparingTo("0");
    }

    @Test
    void sinSoporteOSinAutorizacionNoSeFactura() {
        Cuenta ambulatoria = cuenta(contrato(Contrato.Modalidad.EVENTO, "1.000", null), false, null);
        cargar(ambulatoria, tac, 1, "Informe firmado");
        cargar(ambulatoria, hemograma, 1, null);
        Liquidacion.Resultado r = Liquidacion.calcular(ambulatoria);
        assertThat(r.facturable()).isFalse();
        assertThat(r.bloqueos()).anyMatch(b -> b.contains("autorización")).anyMatch(b -> b.contains("soporte"));

        // En urgencias no se exige autorización previa
        Cuenta urgencias = cuenta(contrato(Contrato.Modalidad.EVENTO, "1.000", null), true, null);
        cargar(urgencias, tac, 1, "Informe firmado");
        assertThat(Liquidacion.calcular(urgencias).facturable()).isTrue();
    }
}
