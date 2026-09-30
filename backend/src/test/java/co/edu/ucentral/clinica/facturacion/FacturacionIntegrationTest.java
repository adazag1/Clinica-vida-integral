package co.edu.ucentral.clinica.facturacion;

import co.edu.ucentral.clinica.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ciclo de ingresos completo: del acto clínico al cierre, pasando por auditoría, factura, glosa y pago. */
@SpringBootTest(properties = {"app.seed.habilitado=true", "app.seed.password-prueba=ClaveTest123*"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class FacturacionIntegrationTest {

    private static final AtomicInteger DOCUMENTO = new AtomicInteger(910_000_000);

    @Autowired
    MockMvc mvc;

    @Test
    void delActoClinicoALaCuentaCerrada() throws Exception {
        String medico = token("medico");
        String fact = token("facturacion");
        String audit = token("auditoria");
        long paciente = paciente("CONTRIBUTIVO", "Sanitas");

        // RFRC-002: la consulta firmada genera su cargo sola, con el contrato de la aseguradora (factor 0,95)
        firmarConsulta(medico, paciente, "Tos productiva y fiebre de 39 grados", "Crépitos basales derechos",
                "Neumonía adquirida en la comunidad", "J18.9");
        String cuentas = mvc.perform(get("/api/facturacion/cuentas/pacientes/" + paciente).header("Authorization", "Bearer " + fact))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].contrato").value(containsString("Sanitas")))
                .andExpect(jsonPath("$[0].cargos").value(1))
                .andReturn().getResponse().getContentAsString();
        long cuenta = ((Number) JsonPath.read(cuentas, "$[0].id")).longValue();
        String url = "/api/facturacion/cuentas/" + cuenta;
        mvc.perform(get(url).header("Authorization", "Bearer " + fact))
                .andExpect(jsonPath("$.cargos[0].servicioCodigo").value("890201"))
                .andExpect(jsonPath("$.cargos[0].valorUnitario").value(42750))
                .andExpect(jsonPath("$.cargos[0].tieneSoporte").value(true));

        // Cargos manuales: TC con tarifa pactada que exige autorización, hemograma sin soporte y un excluido
        long tac = servicio(fact, "879111");
        long hemograma = servicio(fact, "902210");
        long perfil = servicio(fact, "903868");
        enviar(post(url + "/cargos"), fact, "{\"servicioId\":%d,\"cantidad\":1,\"soporte\":\"Orden médica del 29/09\"}".formatted(tac))
                .andExpect(status().isCreated());
        String conHemograma = enviar(post(url + "/cargos"), fact, "{\"servicioId\":%d,\"cantidad\":1}".formatted(hemograma))
                .andReturn().getResponse().getContentAsString();
        List<Number> idsHemograma = JsonPath.read(conHemograma, "$.cargos[?(@.servicioCodigo == '902210')].id");
        long cargoHemograma = idsHemograma.getFirst().longValue();
        enviar(post(url + "/cargos"), fact, "{\"servicioId\":%d,\"cantidad\":1,\"soporte\":\"Orden médica del 29/09\"}".formatted(perfil));
        enviar(post(url + "/cargos"), fact, "{\"servicioId\":%d,\"cantidad\":1,\"soporte\":\"Orden médica del 29/09\"}".formatted(tac))
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("CARGO_DUPLICADO"));

        // RFRC-012: la simulación muestra lo que falta y lo que paga el paciente
        mvc.perform(get(url + "/simulacion").header("Authorization", "Bearer " + fact))
                .andExpect(jsonPath("$.facturable").value(false))
                .andExpect(jsonPath("$.bloqueos").value(hasItem(containsString("soporte clínico"))))
                .andExpect(jsonPath("$.bloqueos").value(hasItem(containsString("autorización"))))
                .andExpect(jsonPath("$.lineasPaciente[0].codigo").value("903868"));
        enviar(post(url + "/facturar"), fact, "").andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("CUENTA_ESTADO_INVALIDO"));

        // Auditoría: sin codificación no se aprueba; se devuelve y facturación corrige
        enviar(post(url + "/enviar-auditoria"), fact, "{}").andExpect(jsonPath("$.resumen.estado").value("EN_AUDITORIA"));
        String auditoria = "/api/auditoria-clinica/cuentas/" + cuenta;
        mvc.perform(get(auditoria).header("Authorization", "Bearer " + audit))
                .andExpect(jsonPath("$.consultas", hasSize(1)))
                .andExpect(jsonPath("$.sugerencias[0].codigo").value("J18.9"))
                .andExpect(jsonPath("$.sugerencias[0].registradoPorMedico").value(true))
                .andExpect(jsonPath("$.verificacion[?(@.punto =~ /.*codificado.*/)].cumple").value(hasItem(false)));
        enviar(post(auditoria + "/aprobar"), audit, "").andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("AUDITORIA_BLOQUEADA"));
        enviar(post(auditoria + "/devolver"), audit,
                "{\"motivo\":\"SOPORTE_FALTANTE\",\"detalle\":\"El hemograma no tiene orden\",\"dirigidaA\":\"FACTURACION\"}")
                .andExpect(jsonPath("$.cuenta.resumen.estado").value("DEVUELTA"));
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + fact))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("devuelta"))));
        enviar(put(url + "/cargos/" + cargoHemograma + "/soporte"), fact, "{\"soporte\":\"Orden médica del 29/09\"}");
        enviar(put(url + "/autorizacion"), fact, "{\"numero\":\"AUT-778812\"}");
        enviar(post(url + "/enviar-auditoria"), fact, "{\"respuesta\":\"Se adjuntó la orden del hemograma y la autorización\"}");

        // RFAU-003 / RFAU-009: codificación confirmada; corregirla exige motivo y queda en el historial
        enviar(put(auditoria + "/codificacion"), audit, "{\"diagnosticoPrincipal\":\"J18.9\",\"relacionados\":[\"R50.9\"]}")
                .andExpect(jsonPath("$.nombrePrincipal").value(containsString("Neumonía")));
        enviar(put(auditoria + "/codificacion"), audit, "{\"diagnosticoPrincipal\":\"J18.9\",\"relacionados\":[\"R05\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.motivo").exists());
        enviar(put(auditoria + "/codificacion"), audit, "{\"diagnosticoPrincipal\":\"J18.9\",\"relacionados\":[\"R05\"],\"motivo\":\"La fiebre es parte del cuadro\"}")
                .andExpect(jsonPath("$.historial", hasSize(3)));
        enviar(put(auditoria + "/codificacion"), audit, "{\"diagnosticoPrincipal\":\"X99.9\"}").andExpect(status().isBadRequest());
        enviar(post(auditoria + "/aprobar"), audit, "")
                .andExpect(jsonPath("$.cuenta.resumen.estado").value("AUDITADA"))
                .andExpect(jsonPath("$.devoluciones[0].corregidaEn").exists())
                .andExpect(jsonPath("$.devoluciones[0].respuesta").value(containsString("orden del hemograma")));

        // RFRC-009 / RFRC-011: factura con CUFE; no se factura dos veces
        String facturada = enviar(post(url + "/facturar"), fact, "")
                .andExpect(jsonPath("$.resumen.estado").value("FACTURADA"))
                .andExpect(jsonPath("$.factura.numero").value(startsWith("FEV")))
                .andExpect(jsonPath("$.factura.estadoDian").value("SIMULADA"))
                .andExpect(jsonPath("$.factura.valorPaciente").value(32000))
                .andExpect(jsonPath("$.factura.total").value(270325))
                .andReturn().getResponse().getContentAsString();
        String cufe = JsonPath.read(facturada, "$.factura.cufe");
        org.assertj.core.api.Assertions.assertThat(cufe).hasSize(96);
        mvc.perform(get(url + "/factura.xml").header("Authorization", "Bearer " + fact))
                .andExpect(content().string(containsString("<cbc:UUID schemeName=\"CUFE-SHA384\">" + cufe)));
        enviar(post(url + "/facturar"), fact, "").andExpect(status().is(422));
        mvc.perform(get("/api/facturacion/cartera").header("Authorization", "Bearer " + token("directivo")))
                .andExpect(jsonPath("$.facturas[*].cuentaId").value(hasItem((int) cuenta)));

        // RFRC-008: glosa por soportes, respuesta y pago del saldo
        enviar(post(url + "/glosas"), fact, glosa("SOPORTES", "101", 17575)).andExpect(status().isBadRequest());
        String glosada = enviar(post(url + "/glosas"), fact, glosa("SOPORTES", "301", 17575))
                .andExpect(jsonPath("$.resumen.estado").value("GLOSADA"))
                .andExpect(jsonPath("$.glosas[0].causaClinica").value(true))
                .andReturn().getResponse().getContentAsString();
        long glosa = ((Number) JsonPath.read(glosada, "$.glosas[0].id")).longValue();
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + audit))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Glosa por causa clínica"))));
        enviar(post(url + "/glosas/" + glosa + "/respuesta"), fact, "{\"valorAceptado\":17575,\"respuesta\":\"Se acepta: la orden llegó después\"}")
                .andExpect(jsonPath("$.factura.saldo").value(252750));
        enviar(post(url + "/pagos"), fact, "{\"valor\":252750,\"fecha\":\"%s\",\"medio\":\"Transferencia\",\"referencia\":\"TRX-1\"}".formatted(LocalDate.now()))
                .andExpect(jsonPath("$.resumen.estado").value("PAGADA"));
        enviar(post(url + "/cerrar"), fact, "")
                .andExpect(jsonPath("$.resumen.estado").value("CERRADA"))
                .andExpect(jsonPath("$.eventos[*].accion").value(hasItem("CERRADA")));
    }

    @Test
    void capitacionSeReportaSinCobroYLosRolesEstanSeparados() throws Exception {
        String fact = token("facturacion");
        String audit = token("auditoria");
        long paciente = paciente("SUBSIDIADO", "Compensar");
        long cuenta = ((Number) JsonPath.read(enviar(post("/api/facturacion/cuentas"), fact, "{\"pacienteId\":%d}".formatted(paciente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumen.modalidad").value("Capitación"))
                .andReturn().getResponse().getContentAsString(), "$.resumen.id")).longValue();
        String url = "/api/facturacion/cuentas/" + cuenta;
        enviar(post(url + "/cargos"), fact, "{\"servicioId\":%d,\"cantidad\":1,\"soporte\":\"Orden de control\"}".formatted(servicio(fact, "890201")));
        enviar(post(url + "/enviar-auditoria"), fact, "{}");

        // RNFAU-003: facturación no aprueba auditoría y auditoría no factura
        enviar(post("/api/auditoria-clinica/cuentas/" + cuenta + "/aprobar"), fact, "").andExpect(status().isForbidden());
        enviar(put("/api/auditoria-clinica/cuentas/" + cuenta + "/codificacion"), audit, "{\"diagnosticoPrincipal\":\"R05\"}");
        enviar(post("/api/auditoria-clinica/cuentas/" + cuenta + "/aprobar"), audit, "").andExpect(status().isOk());
        enviar(post(url + "/facturar"), audit, "").andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", "Bearer " + token("medico"))).andExpect(status().isForbidden());

        enviar(post(url + "/facturar"), fact, "")
                .andExpect(jsonPath("$.factura.total").value(0))
                .andExpect(jsonPath("$.resumen.estado").value("PAGADA"));
    }

    @Test
    void catalogoConVersionesYTablero() throws Exception {
        String audit = token("auditoria");
        int antes = JsonPath.read(mvc.perform(get("/api/auditoria-clinica/catalogos/diagnosticos/version").header("Authorization", "Bearer " + audit))
                .andReturn().getResponse().getContentAsString(), "$.version");
        enviar(post("/api/auditoria-clinica/catalogos/diagnosticos"), audit, "{\"codigo\":\"j189x\",\"nombre\":\"x\"}").andExpect(status().isBadRequest());
        String creado = enviar(post("/api/auditoria-clinica/catalogos/diagnosticos"), audit, "{\"codigo\":\"U07.1\",\"nombre\":\"COVID-19, virus identificado\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(creado, "$.id")).longValue();
        enviar(put("/api/auditoria-clinica/catalogos/diagnosticos/" + id), audit,
                "{\"nombre\":\"COVID-19, virus identificado\",\"activo\":false,\"motivo\":\"Prueba de inactivación\"}")
                .andExpect(jsonPath("$.activo").value(false));
        mvc.perform(get("/api/auditoria-clinica/catalogos/diagnosticos/version").header("Authorization", "Bearer " + audit))
                .andExpect(jsonPath("$.version").value(antes + 2));
        mvc.perform(get("/api/auditoria-clinica/catalogos/diagnosticos/historial").header("Authorization", "Bearer " + audit))
                .andExpect(jsonPath("$[0].accion").value("INACTIVADO"));
        // Inactivo ya no se ofrece al médico
        mvc.perform(get("/api/historia/diagnosticos").param("texto", "U07.1").header("Authorization", "Bearer " + token("medico")))
                .andExpect(jsonPath("$", hasSize(0)));

        String hoy = LocalDate.now().toString();
        mvc.perform(get("/api/auditoria-clinica/tablero").param("desde", hoy).param("hasta", hoy).header("Authorization", "Bearer " + audit))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cuentasEnAuditoria").exists());
    }

    // ---------- utilidades ----------

    private void firmarConsulta(String medico, long paciente, String motivo, String examen, String plan, String cie10) throws Exception {
        long consulta = id(enviar(post("/api/consultas"), medico, "{\"pacienteId\":%d}".formatted(paciente)).andExpect(status().isOk()));
        String dx = mvc.perform(get("/api/historia/diagnosticos").param("texto", cie10).header("Authorization", "Bearer " + medico))
                .andReturn().getResponse().getContentAsString();
        long dxId = ((Number) JsonPath.read(dx, "$[0].id")).longValue();
        enviar(put("/api/consultas/" + consulta), medico, """
                {"motivoConsulta":"%s","enfermedadActual":"%s","examenFisico":"%s","plan":"%s",
                 "diagnosticos":[{"diagnosticoId":%d,"principal":true,"confirmado":true}]}""".formatted(motivo, motivo, examen, plan, dxId))
                .andExpect(status().isOk());
        enviar(post("/api/consultas/" + consulta + "/finalizar"), medico, "{\"password\":\"ClaveTest123*\"}").andExpect(status().isOk());
    }

    private long servicio(String t, String codigo) throws Exception {
        String json = mvc.perform(get("/api/facturacion/servicios").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(json, "$[?(@.codigo == '" + codigo + "')].id");
        return ids.getFirst().longValue();
    }

    private static String glosa(String concepto, String codigo, long valor) {
        return "{\"concepto\":\"%s\",\"codigo\":\"%s\",\"descripcion\":\"Falta la orden del hemograma\",\"valor\":%d,\"recibidaEn\":\"%s\"}"
                .formatted(concepto, codigo, valor, LocalDate.now());
    }

    private long paciente(String regimen, String aseguradora) throws Exception {
        String doc = String.valueOf(DOCUMENTO.incrementAndGet());
        return id(enviar(post("/api/pacientes"), token("admisiones"), """
                {"tipoDocumento":"CC","numeroDocumento":"%s","nombres":"Fact","apellidos":"Prueba %s","fechaNacimiento":"1972-04-04",
                 "sexo":"MASCULINO","regimen":"%s","aseguradora":"%s","confirmarNoDuplicado":true}"""
                .formatted(doc, doc, regimen, aseguradora)).andExpect(status().isCreated()));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String t, String json) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long id(ResultActions r) throws Exception {
        Number id = JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private String token(String usuario) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"password\":\"ClaveTest123*\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }
}
