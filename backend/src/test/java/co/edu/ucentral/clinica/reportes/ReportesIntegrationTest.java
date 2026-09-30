package co.edu.ucentral.clinica.reportes;

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

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Épica 5: tablero clínico del CEO y reporte financiero del CFO. */
@SpringBootTest(properties = {"app.seed.habilitado=true", "app.seed.password-prueba=ClaveTest123*"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ReportesIntegrationTest {

    private static final AtomicInteger DOCUMENTO = new AtomicInteger(930_000_000);

    @Autowired
    MockMvc mvc;

    @Test
    void tableroClinicoCuentaLosDiagnosticosCriticos() throws Exception {
        String medico = token("medico");
        String directivo = token("directivo");
        firmarConsulta(medico, paciente(), "J18.9");
        // Un diagnóstico no crítico no entra al indicador
        firmarConsulta(medico, paciente(), "J00");

        mvc.perform(get("/api/reportes/clinico").header("Authorization", "Bearer " + medico)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/clinico").header("Authorization", "Bearer " + directivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimos30Dias.casos").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.porDiagnostico[*].codigo").value(hasItem("J18.9")))
                .andExpect(jsonPath("$.recientes[*].codigo").value(org.hamcrest.Matchers.not(hasItem("J00"))))
                .andExpect(jsonPath("$.metaHoras").value(33.6))
                .andExpect(jsonPath("$.estado").value("META_ALCANZADA"))
                .andExpect(jsonPath("$.componentes.length()").value(4))
                .andExpect(jsonPath("$.parametrosDePrueba").value(true));
    }

    @Test
    void parametrosYCostosAlimentanElReporteFinanciero() throws Exception {
        String directivo = token("directivo");
        enviar(put("/api/reportes/parametros/costos.meta_reduccion_pct"), directivo, "{\"valor\":\"140\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.valor").exists());
        enviar(put("/api/reportes/parametros/diagnostico.inicio"), directivo, "{\"valor\":\"01/01/2026\"}").andExpect(status().isBadRequest());
        enviar(put("/api/reportes/parametros/costos.linea_base_mensual"), directivo, "{\"valor\":\"200000000\"}")
                .andExpect(jsonPath("$.prueba").value(false))
                .andExpect(jsonPath("$.modificadoPor").value("directivo"));

        String mes = YearMonth.now(ZoneId.of("America/Bogota")).toString();
        enviar(put("/api/reportes/costos"), directivo, "{\"mes\":\"%s\",\"categoria\":\"PERSONAL_ADMINISTRATIVO\",\"valor\":150000000}".formatted(mes))
                .andExpect(status().isOk());
        // Registrar de nuevo reemplaza el valor
        enviar(put("/api/reportes/costos"), directivo, "{\"mes\":\"%s\",\"categoria\":\"PERSONAL_ADMINISTRATIVO\",\"valor\":140000000}".formatted(mes))
                .andExpect(jsonPath("$.valor").value(140000000));
        enviar(put("/api/reportes/costos"), directivo, "{\"mes\":\"%s\",\"categoria\":\"PLATAFORMA\",\"valor\":8000000,\"nota\":\"Azure\"}".formatted(mes));
        enviar(put("/api/reportes/costos"), directivo, "{\"mes\":\"%s\",\"categoria\":\"PAPELERIA\",\"valor\":1}"
                .formatted(YearMonth.now().plusMonths(2))).andExpect(status().isBadRequest());
        enviar(put("/api/reportes/costos"), token("facturacion"), "{\"mes\":\"%s\",\"categoria\":\"PAPELERIA\",\"valor\":1}".formatted(mes))
                .andExpect(status().isForbidden());

        String json = mvc.perform(get("/api/reportes/financiero").header("Authorization", "Bearer " + directivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costosMeta").value(150000000))
                .andExpect(jsonPath("$.roi.inversion").value(3000000000L))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Object> ultimo = JsonPath.read(json, "$.meses[?(@.mes == '" + mes + "')]");
        org.assertj.core.api.Assertions.assertThat(ultimo).hasSize(1);
        Number ahorro = JsonPath.read(json, "$.meses[-1].ahorro");
        Number neto = JsonPath.read(json, "$.meses[-1].beneficioNeto");
        org.assertj.core.api.Assertions.assertThat(ahorro.longValue()).isEqualTo(60_000_000L);
        org.assertj.core.api.Assertions.assertThat(neto.longValue()).isGreaterThanOrEqualTo(52_000_000L);

        mvc.perform(get("/api/reportes/financiero.csv").header("Authorization", "Bearer " + directivo))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("﻿Mes;Costo administrativo")));
    }

    // ---------- utilidades ----------

    private void firmarConsulta(String medico, long paciente, String cie10) throws Exception {
        long consulta = id(enviar(post("/api/consultas"), medico, "{\"pacienteId\":%d}".formatted(paciente)).andExpect(status().isOk()));
        String dx = mvc.perform(get("/api/historia/diagnosticos").param("texto", cie10).header("Authorization", "Bearer " + medico))
                .andReturn().getResponse().getContentAsString();
        long dxId = ((Number) JsonPath.read(dx, "$[0].id")).longValue();
        enviar(put("/api/consultas/" + consulta), medico, """
                {"motivoConsulta":"Control","enfermedadActual":"Cuadro de 2 días","examenFisico":"Hallazgos compatibles","plan":"Manejo",
                 "diagnosticos":[{"diagnosticoId":%d,"principal":true,"confirmado":true}]}""".formatted(dxId)).andExpect(status().isOk());
        enviar(post("/api/consultas/" + consulta + "/finalizar"), medico, "{\"password\":\"ClaveTest123*\"}").andExpect(status().isOk());
    }

    private long paciente() throws Exception {
        String doc = String.valueOf(DOCUMENTO.incrementAndGet());
        return id(enviar(post("/api/pacientes"), token("admisiones"), """
                {"tipoDocumento":"CC","numeroDocumento":"%s","nombres":"Indicador","apellidos":"Prueba %s","fechaNacimiento":"1960-05-05",
                 "sexo":"FEMENINO","regimen":"CONTRIBUTIVO","aseguradora":"Nueva EPS","confirmarNoDuplicado":true}"""
                .formatted(doc, doc)).andExpect(status().isCreated()));
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
