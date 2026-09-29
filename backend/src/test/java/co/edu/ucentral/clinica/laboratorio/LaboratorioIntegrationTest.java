package co.edu.ucentral.clinica.laboratorio;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Laboratorio de punta a punta: orden del médico, muestra, resultados, validación y aviso. */
@SpringBootTest(properties = {"app.seed.habilitado=true", "app.seed.password-prueba=ClaveTest123*"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class LaboratorioIntegrationTest {

    private static final AtomicInteger DOCUMENTO = new AtomicInteger(300_000_000);

    @Autowired
    MockMvc mvc;

    @Test
    void potasioCriticoSeConfirmaSeInformaYLlegaAlMedico() throws Exception {
        String medico = token("medico");
        String lab = token("laboratorio");
        long paciente = paciente("MASCULINO");
        long consulta = id(enviar(post("/api/consultas"), medico, "{\"pacienteId\":%d}".formatted(paciente)));

        long orden = id(enviar(post("/api/laboratorio/ordenes"), medico, """
                {"pacienteId":%d,"consultaId":%d,"prioridad":"URGENTE","examenIds":[%d]}"""
                .formatted(paciente, consulta, examen(lab, "ELECTROLITOS")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ORDENADA"))
                .andExpect(jsonPath("$.numero").value(containsString("ORD-"))));

        // RFLC-005: la urgente aparece en la cola del laboratorio
        mvc.perform(get("/api/laboratorio/ordenes").header("Authorization", "Bearer " + lab))
                .andExpect(jsonPath("$[*].id").value(hasItem((int) orden)));

        enviar(post("/api/laboratorio/ordenes/" + orden + "/muestra"), lab, "{\"tomadaPor\":\"Aux. Toma de muestras\"}")
                .andExpect(jsonPath("$.estado").value("MUESTRA_RECIBIDA"));

        long k = analito(lab, "K");
        long na = analito(lab, "NA");
        long item = ordenExamen(lab, orden);
        enviar(put("/api/laboratorio/ordenes/" + orden + "/resultados"), lab, """
                {"trazabilidad":[],"valores":[{"analitoId":%d,"valor":6.8},{"analitoId":%d,"valor":140}]}"""
                .formatted(k, na))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.tieneCriticos").value(true))
                .andExpect(jsonPath("$.examenes[0].resultados[?(@.codigo == 'K')].interpretacion").value(hasItem("CRITICO_ALTO")))
                .andExpect(jsonPath("$.pendientesValidacion").value(hasItem(containsString("equipo, lote de reactivo"))))
                .andExpect(jsonPath("$.pendientesValidacion").value(hasItem(containsString("confirme el valor crítico de Potasio"))));

        // Un valor registrado no se cambia en silencio
        enviar(put("/api/laboratorio/ordenes/" + orden + "/resultados"), lab,
                "{\"trazabilidad\":[],\"valores\":[{\"analitoId\":%d,\"valor\":4.5}]}".formatted(k))
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("USAR_REPETICION"));

        // Trazabilidad, control de calidad del día y repetición de confirmación
        enviar(put("/api/laboratorio/ordenes/" + orden + "/resultados"), lab, """
                {"trazabilidad":[{"ordenExamenId":%d,"equipo":"Cobas c311","loteReactivo":"L-2291","calibracion":"2026-09-28"}],
                 "valores":[]}""".formatted(item)).andExpect(status().isOk());
        enviar(post("/api/laboratorio/control-calidad"), lab, """
                {"area":"QUIMICA","equipo":"Cobas c311","niveles":"Normal y patológico","loteControl":"QC-77","aceptado":true}""")
                .andExpect(status().isCreated());
        enviar(post("/api/laboratorio/ordenes/" + orden + "/resultados/" + k + "/repetir"), lab,
                "{\"valor\":6.9,\"motivo\":\"CONFIRMAR_CRITICO\"}")
                .andExpect(jsonPath("$.examenes[0].resultados[?(@.codigo == 'K')].repeticiones").value(hasItem(1)))
                .andExpect(jsonPath("$.pendientesValidacion").isEmpty());

        // Sin registrar a quién se informó el crítico no se libera
        enviar(post("/api/laboratorio/ordenes/" + orden + "/validar"), lab, "{}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.mensaje").value(containsString("informó el valor crítico")));
        enviar(post("/api/laboratorio/ordenes/" + orden + "/validar"), lab,
                "{\"contactoCritico\":\"Dr. Juan Pérez\",\"medioCritico\":\"Llamada\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADA"))
                .andExpect(jsonPath("$.criticoContacto").value("Dr. Juan Pérez"));

        // RFMT-004: el médico recibe el aviso y el resumen clínico lo destaca
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Resultado crítico"))));
        mvc.perform(get("/api/historia/" + paciente + "/resumen").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.resultadosRecientes[?(@.analito == 'Potasio')].interpretacion").value(hasItem("CRITICO_ALTO")))
                .andExpect(jsonPath("$.alertas").value(hasItem(containsString("Resultado crítico: Potasio"))));

        // RFLC-004: la siguiente orden compara contra este resultado (delta check)
        long segunda = id(enviar(post("/api/laboratorio/ordenes"), medico, """
                {"pacienteId":%d,"prioridad":"URGENTE","examenIds":[%d]}""".formatted(paciente, examen(lab, "ELECTROLITOS"))));
        enviar(post("/api/laboratorio/ordenes/" + segunda + "/muestra"), lab, "{\"tomadaPor\":\"Aux\"}");
        enviar(put("/api/laboratorio/ordenes/" + segunda + "/resultados"), lab,
                "{\"trazabilidad\":[],\"valores\":[{\"analitoId\":%d,\"valor\":4.0}]}".formatted(k))
                .andExpect(jsonPath("$.examenes[0].resultados[?(@.codigo == 'K')].valorPrevio").value(hasItem(6.9)))
                .andExpect(jsonPath("$.examenes[0].resultados[?(@.codigo == 'K')].alertaDelta").value(hasItem(true)))
                .andExpect(jsonPath("$.advertencias[0]").value(containsString("Potasio cambió")));
    }

    @Test
    void elRangoDependeDelSexoDelPaciente() throws Exception {
        String medico = token("medico");
        String lab = token("laboratorio");
        long hgb = analito(lab, "HGB");
        for (String[] caso : new String[][]{{"FEMENINO", "NORMAL"}, {"MASCULINO", "BAJO"}}) {
            long p = paciente(caso[0]);
            long orden = id(enviar(post("/api/laboratorio/ordenes"), medico,
                    "{\"pacienteId\":%d,\"prioridad\":\"AMBULATORIO\",\"examenIds\":[%d]}".formatted(p, examen(lab, "HEMOGRAMA"))));
            enviar(post("/api/laboratorio/ordenes/" + orden + "/muestra"), lab, "{\"tomadaPor\":\"Aux\"}");
            enviar(put("/api/laboratorio/ordenes/" + orden + "/resultados"), lab,
                    "{\"trazabilidad\":[],\"valores\":[{\"analitoId\":%d,\"valor\":12.5}]}".formatted(hgb))
                    .andExpect(jsonPath("$.examenes[0].resultados[?(@.codigo == 'HGB')].interpretacion").value(hasItem(caso[1])));
        }
    }

    @Test
    void muestraRechazadaAvisaAlMedicoYPermiteTomarOtra() throws Exception {
        String medico = token("medico");
        String lab = token("laboratorio");
        long orden = id(enviar(post("/api/laboratorio/ordenes"), medico,
                "{\"pacienteId\":%d,\"prioridad\":\"HOSPITALIZADO\",\"examenIds\":[%d]}".formatted(paciente("FEMENINO"),
                        examen(lab, "GLUCOSA"))));
        enviar(post("/api/laboratorio/ordenes/" + orden + "/muestra"), lab, "{\"tomadaPor\":\"Aux\"}");
        enviar(post("/api/laboratorio/ordenes/" + orden + "/rechazar"), lab, "{\"motivo\":\"HEMOLIZADA\"}")
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazoDescripcion").value("Muestra hemolizada"));
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Muestra rechazada"))));
        enviar(post("/api/laboratorio/ordenes/" + orden + "/nueva-muestra"), lab, "")
                .andExpect(jsonPath("$.estado").value("ORDENADA"))
                .andExpect(jsonPath("$.historial[*].accion").value(hasItem("MUESTRA_RECHAZADA")));
    }

    @Test
    void elLaboratorioAjustaRangosSinDesarrolloYValidaCoherencia() throws Exception {
        String lab = token("laboratorio");
        String catalogo = mvc.perform(get("/api/laboratorio/examenes").header("Authorization", "Bearer " + lab))
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(catalogo, "$[*].analitos[?(@.codigo == 'GLU')].rangos[0].id");
        long rango = ids.getFirst().longValue();
        enviar(put("/api/laboratorio/rangos/" + rango), lab, "{\"refMin\":70,\"refMax\":100,\"criticoMin\":40,\"criticoMax\":90}")
                .andExpect(status().isBadRequest());
        enviar(put("/api/laboratorio/rangos/" + rango), lab, "{\"refMin\":70,\"refMax\":100,\"criticoMin\":45,\"criticoMax\":450}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticoMin").value(45))
                .andExpect(jsonPath("$.modificadoPor").value("laboratorio"));
    }

    @Test
    void cadaRolHaceSoloLoSuyo() throws Exception {
        String lab = token("laboratorio");
        long p = paciente("FEMENINO");
        enviar(post("/api/laboratorio/ordenes"), lab,
                "{\"pacienteId\":%d,\"prioridad\":\"AMBULATORIO\",\"examenIds\":[%d]}".formatted(p, examen(lab, "TSH")))
                .andExpect(status().isForbidden());
        enviar(post("/api/laboratorio/ordenes/1/validar"), token("medico"), "{}").andExpect(status().isForbidden());
    }

    // ---------- utilidades ----------

    private long examen(String t, String codigo) throws Exception {
        return catalogo(t, "$[?(@.codigo == '" + codigo + "')].id");
    }

    private long analito(String t, String codigo) throws Exception {
        return catalogo(t, "$[*].analitos[?(@.codigo == '" + codigo + "')].id");
    }

    private long catalogo(String t, String ruta) throws Exception {
        String json = mvc.perform(get("/api/laboratorio/examenes").header("Authorization", "Bearer " + t))
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(json, ruta);
        return ids.getFirst().longValue();
    }

    private long ordenExamen(String t, long orden) throws Exception {
        String json = mvc.perform(get("/api/laboratorio/ordenes/" + orden).header("Authorization", "Bearer " + t))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(json, "$.examenes[0].id");
        return id.longValue();
    }

    private long paciente(String sexo) throws Exception {
        String doc = String.valueOf(DOCUMENTO.incrementAndGet());
        return id(enviar(post("/api/pacientes"), token("admisiones"), """
                {"tipoDocumento":"CC","numeroDocumento":"%s","nombres":"Lab","apellidos":"Prueba %s",
                 "fechaNacimiento":"1975-03-10","sexo":"%s","regimen":"PARTICULAR","confirmarNoDuplicado":true}"""
                .formatted(doc, doc, sexo)).andExpect(status().isCreated()));
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
