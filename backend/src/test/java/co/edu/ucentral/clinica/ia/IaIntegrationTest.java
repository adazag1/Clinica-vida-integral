package co.edu.ucentral.clinica.ia;

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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Épica 4: alertas por reglas con retroalimentación, asistente apagado y dataset anonimizado. */
@SpringBootTest(properties = {"app.seed.habilitado=true", "app.seed.password-prueba=ClaveTest123*"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class IaIntegrationTest {

    private static final AtomicInteger DOCUMENTO = new AtomicInteger(920_000_000);

    @Autowired
    MockMvc mvc;

    @Test
    void signosCriticosGeneranAlertaQueElMedicoCalifica() throws Exception {
        String medico = token("medico");
        String enfermeria = token("enfermeria");
        String admin = token("admin");
        long paciente = paciente("Hipolito", "Tension Alta");

        // La toma de signos dispara la reevaluación: crisis hipertensiva crítica y aviso a los médicos
        enviar(post("/api/historia/" + paciente + "/signos"), enfermeria,
                "{\"presionSistolica\":196,\"presionDiastolica\":124,\"frecuenciaCardiaca\":88}").andExpect(status().isCreated());
        String alertas = mvc.perform(get("/api/ia/pacientes/" + paciente + "/alertas").header("Authorization", "Bearer " + enfermeria))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].regla").value("CRISIS_HTA"))
                .andExpect(jsonPath("$[0].severidad").value("CRITICA"))
                .andExpect(jsonPath("$[0].evidencia[0]").value(startsWith("PA 196/124")))
                .andExpect(jsonPath("$[0].referencia").value(containsString("ACC/AHA")))
                .andReturn().getResponse().getContentAsString();
        long alerta = ((Number) JsonPath.read(alertas, "$[0].id")).longValue();
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem("Alerta clínica crítica")));

        // Reevaluar no duplica la alerta
        mvc.perform(get("/api/ia/pacientes/" + paciente + "/alertas").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(alerta));

        // Solo el médico califica; "no útil" exige comentario
        String url = "/api/ia/alertas/" + alerta + "/retroalimentacion";
        enviar(post(url), enfermeria, "{\"valor\":\"UTIL\"}").andExpect(status().isForbidden());
        enviar(post(url), medico, "{\"valor\":\"NO_UTIL\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("COMENTARIO_REQUERIDO"));
        enviar(post(url), medico, "{\"valor\":\"UTIL\",\"comentario\":\"Se ajustó el antihipertensivo\"}")
                .andExpect(jsonPath("$.retroalimentacion").value("UTIL"))
                .andExpect(jsonPath("$.retroPor").value("medico"));
        mvc.perform(get("/api/ia/reglas").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[?(@.codigo == 'CRISIS_HTA')].utiles").value(hasItem(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));

        // Desactivar la regla cierra la alerta; solo el administrador puede hacerlo
        enviar(put("/api/ia/reglas/CRISIS_HTA"), medico, "{\"activa\":false}").andExpect(status().isForbidden());
        enviar(put("/api/ia/reglas/CRISIS_HTA"), admin, "{\"activa\":false}")
                .andExpect(jsonPath("$.activa").value(false)).andExpect(jsonPath("$.modificadoPor").value("admin"));
        mvc.perform(get("/api/ia/pacientes/" + paciente + "/alertas").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/ia/pacientes/" + paciente + "/alertas").param("historial", "true").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$[0].vigente").value(false))
                .andExpect(jsonPath("$[0].retroalimentacion").value("UTIL"));
        enviar(put("/api/ia/reglas/CRISIS_HTA"), admin, "{\"activa\":true}").andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void asistenteApagadoYVistaPreviaSinIdentificadores() throws Exception {
        String medico = token("medico");
        long paciente = paciente("Eustaquio", "Reservado Cifuentes");
        long consulta = id(enviar(post("/api/consultas"), medico, "{\"pacienteId\":%d}".formatted(paciente)));
        enviar(put("/api/consultas/" + consulta), medico, """
                {"motivoConsulta":"Eustaquio refiere dolor torácico","enfermedadActual":"Llamar al 3001234567 a su hija",
                 "examenFisico":"Sin hallazgos","plan":"Control","diagnosticos":[]}""").andExpect(status().isOk());

        mvc.perform(get("/api/ia/estado").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.asistenteHabilitado").value(false))
                .andExpect(jsonPath("$.totalReglas").value(9))
                .andExpect(jsonPath("$.motivo").value(containsString("apagado")));
        enviar(post("/api/ia/pacientes/" + paciente + "/asistente"), medico, "{\"tipo\":\"RESUMEN\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("IA_DESHABILITADA"));
        mvc.perform(get("/api/ia/pacientes/" + paciente + "/asistente/vista-previa").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.texto").value(containsString("[PACIENTE] refiere dolor torácico")))
                .andExpect(jsonPath("$.texto").value(containsString("grupo de edad 50-54")))
                .andExpect(jsonPath("$.texto").value(not(containsString("Eustaquio"))))
                .andExpect(jsonPath("$.texto").value(not(containsString("3001234567"))));
    }

    @Test
    void datasetAnonimizadoParaDirectivos() throws Exception {
        long paciente = paciente("Dataset", "Exportable Uno");
        enviar(post("/api/historia/" + paciente + "/signos"), token("enfermeria"), "{\"temperatura\":37.2,\"frecuenciaCardiaca\":76}");

        mvc.perform(get("/api/ia/dataset").header("Authorization", "Bearer " + token("medico"))).andExpect(status().isForbidden());
        String csv = mvc.perform(get("/api/ia/dataset").header("Authorization", "Bearer " + token("directivo")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("dataset-clinico-anonimizado")))
                .andExpect(content().string(startsWith(DatasetService.ENCABEZADO)))
                .andReturn().getResponse().getContentAsString();
        List<String> lineas = csv.lines().toList();
        org.assertj.core.api.Assertions.assertThat(csv).doesNotContain("Exportable").doesNotContain(String.valueOf(DOCUMENTO.get()));
        org.assertj.core.api.Assertions.assertThat(lineas).anyMatch(l -> l.contains(",SIGNO,TEMP,37.2,") && l.contains(",MASCULINO,50-54,0,"));
    }

    // ---------- utilidades ----------

    private long paciente(String nombres, String apellidos) throws Exception {
        String doc = String.valueOf(DOCUMENTO.incrementAndGet());
        return id(enviar(post("/api/pacientes"), token("admisiones"), """
                {"tipoDocumento":"CC","numeroDocumento":"%s","nombres":"%s","apellidos":"%s","fechaNacimiento":"1974-02-10",
                 "sexo":"MASCULINO","regimen":"CONTRIBUTIVO","aseguradora":"Nueva EPS","confirmarNoDuplicado":true}"""
                .formatted(doc, nombres, apellidos)).andExpect(status().isCreated()));
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
