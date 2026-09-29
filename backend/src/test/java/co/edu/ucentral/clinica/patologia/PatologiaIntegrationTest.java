package co.edu.ucentral.clinica.patologia;

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

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Patología de punta a punta: número único, recepción segura, registro estructurado, versiones y custodia. */
@SpringBootTest(properties = {"app.seed.habilitado=true", "app.seed.password-prueba=ClaveTest123*"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class PatologiaIntegrationTest {

    private static final AtomicInteger DOCUMENTO = new AtomicInteger(600_000_000);

    @Autowired
    MockMvc mvc;

    @Test
    void biopsiaIntraoperatoriaSeRecibeInformaYCorrigeConVersiones() throws Exception {
        String medico = token("medico");
        String pat = token("patologia");
        String doc = documento();
        long paciente = paciente(doc);

        long caso = id(enviar(post("/api/patologia/casos"), medico, """
                {"pacienteId":%d,"tipo":"BIOPSIA","prioridad":"INTRAOPERATORIO","sitioAnatomico":"Mama derecha, cuadrante superior externo",
                 "datosClinicos":"Nódulo de 2 cm, BI-RADS 5","numeroMuestras":2}""".formatted(paciente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumen.numero").value(matchesPattern("^B\\d{2}-\\d{6}$")))
                .andExpect(jsonPath("$.resumen.estado").value("SOLICITADO")));
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + pat))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Intraoperatorio"))));
        mvc.perform(get("/api/patologia/casos").header("Authorization", "Bearer " + pat))
                .andExpect(jsonPath("$[0].prioridad").value("INTRAOPERATORIO"));

        // Recepción: el rótulo de otro paciente no entra, y el intento queda registrado
        enviar(post("/api/patologia/casos/" + caso + "/recibir"), pat, "{\"documentoRotulo\":\"123\",\"muestrasRecibidas\":2}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("IDENTIFICACION_NO_COINCIDE"));
        enviar(post("/api/patologia/casos/" + caso + "/recibir"), pat, "{\"documentoRotulo\":\"%s\",\"muestrasRecibidas\":1}".formatted(doc))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.observacion").exists());
        enviar(post("/api/patologia/casos/" + caso + "/recibir"), pat, "{\"documentoRotulo\":\"%s\",\"muestrasRecibidas\":2}".formatted(doc))
                .andExpect(jsonPath("$.resumen.estado").value("RECIBIDO"))
                .andExpect(jsonPath("$.plantilla.tipo").value("BIOPSIA"))
                .andExpect(jsonPath("$.eventos[*].accion").value(hasItem("IDENTIFICACION_RECHAZADA")));

        // RFPAT-004: los campos estructurados se validan con la plantilla
        enviar(put("/api/patologia/casos/" + caso + "/macroscopia"), pat, "{\"valores\":{\"fragmentos\":\"dos\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.fragmentos").value(containsString("número")))
                .andExpect(jsonPath("$.campos.medidas").exists());
        enviar(put("/api/patologia/casos/" + caso + "/macroscopia"), pat,
                "{\"valores\":{\"fragmentos\":\"2\",\"medidas\":\"12 x 2 x 2\",\"cassettes\":\"1\",\"campo_inventado\":\"x\"}}")
                .andExpect(jsonPath("$.resumen.estado").value("MACROSCOPIA"))
                .andExpect(jsonPath("$.macroscopia.campo_inventado").doesNotExist());
        enviar(put("/api/patologia/casos/" + caso + "/microscopia"), pat,
                "{\"valores\":{\"descripcion_micro\":\"Proliferación de células ductales atípicas\",\"displasia\":\"Grave\"},\"diagnostico\":\"x\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.displasia").exists());
        enviar(put("/api/patologia/casos/" + caso + "/microscopia"), pat, """
                {"valores":{"descripcion_micro":"Proliferación de células ductales atípicas que infiltran el estroma",
                 "displasia":"No evaluable","malignidad":"Positivo"},"diagnostico":"Carcinoma ductal infiltrante"}""")
                .andExpect(status().isOk());

        // RFPAT-006: validar exige contraseña y deja usuario, fecha y estado
        enviar(post("/api/patologia/casos/" + caso + "/validar"), pat, "{\"password\":\"mala\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("FIRMA_INVALIDA"));
        enviar(post("/api/patologia/casos/" + caso + "/validar"), pat, "{\"password\":\"ClaveTest123*\"}")
                .andExpect(jsonPath("$.resumen.estado").value("INFORMADO"))
                .andExpect(jsonPath("$.versiones", hasSize(1)))
                .andExpect(jsonPath("$.versiones[0].integro").value(true))
                .andExpect(jsonPath("$.validadoPor").value("patologia"))
                .andExpect(jsonPath("$.retencionHasta").value(notNullValue()));
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Informe de patología"))));
        mvc.perform(get("/api/documentos/pacientes/" + paciente).header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$[*].tipo").value(hasItem("INFORME_PATOLOGIA")));

        // RFPAT-007: la corrección exige motivo, crea la versión 2 y conserva la original
        enviar(post("/api/patologia/casos/" + caso + "/validar"), pat, "{\"password\":\"ClaveTest123*\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("SIN_CAMBIOS"));
        enviar(put("/api/patologia/casos/" + caso + "/microscopia"), pat, """
                {"valores":{"descripcion_micro":"Proliferación de células ductales atípicas que infiltran el estroma",
                 "displasia":"No evaluable","malignidad":"Positivo"},"diagnostico":"Carcinoma ductal infiltrante, grado histológico 2"}""")
                .andExpect(jsonPath("$.cambiosSinValidar").value(true));
        enviar(post("/api/patologia/casos/" + caso + "/validar"), pat, "{\"password\":\"ClaveTest123*\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.motivoCambio").exists());
        enviar(post("/api/patologia/casos/" + caso + "/validar"), pat, "{\"password\":\"ClaveTest123*\",\"motivoCambio\":\"Se agrega el grado\"}")
                .andExpect(jsonPath("$.versiones", hasSize(2)))
                .andExpect(jsonPath("$.versiones[0].motivoCambio").value("Se agrega el grado"))
                .andExpect(jsonPath("$.versiones[1].diagnostico").value("Carcinoma ductal infiltrante"))
                .andExpect(jsonPath("$.cambiosSinValidar").value(false));
    }

    @Test
    void custodiaRespetaLaRetencionYElPrestamo() throws Exception {
        String pat = token("patologia");
        String doc = documento();
        long caso = id(enviar(post("/api/patologia/casos"), token("medico"), """
                {"pacienteId":%d,"tipo":"CITOLOGIA","prioridad":"RUTINA","sitioAnatomico":"Cuello uterino","numeroMuestras":1}"""
                .formatted(paciente(doc))).andExpect(jsonPath("$.resumen.numero").value(matchesPattern("^C\\d{2}-\\d{6}$"))));
        String custodia = "/api/patologia/casos/" + caso + "/custodia";
        enviar(post(custodia), pat, "{\"material\":\"LAMINA\",\"accion\":\"ARCHIVADO\",\"cantidad\":2,\"ubicacion\":\"x\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("CASO_ESTADO_INVALIDO"));
        enviar(post("/api/patologia/casos/" + caso + "/recibir"), pat, "{\"documentoRotulo\":\"%s\",\"muestrasRecibidas\":1}".formatted(doc));

        enviar(post(custodia), pat, "{\"material\":\"LAMINA\",\"accion\":\"DESCARTADO\",\"cantidad\":2}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("RETENCION_VIGENTE"));
        enviar(post(custodia), pat, "{\"material\":\"LAMINA\",\"accion\":\"DEVUELTO\",\"cantidad\":2}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("SIN_PRESTAMO"));
        enviar(post(custodia), pat, "{\"material\":\"LAMINA\",\"accion\":\"PRESTADO\",\"cantidad\":2}")
                .andExpect(status().isBadRequest());
        enviar(post(custodia), pat, "{\"material\":\"LAMINA\",\"accion\":\"PRESTADO\",\"cantidad\":2,\"destinatario\":\"Hospital Universitario (segunda opinión)\"}")
                .andExpect(jsonPath("$.ubicacionMaterial[0]").value(containsString("prestado a Hospital Universitario")));
        enviar(post(custodia), pat, "{\"material\":\"LAMINA\",\"accion\":\"DEVUELTO\",\"cantidad\":2,\"ubicacion\":\"Archivo de láminas, gaveta 12\"}")
                .andExpect(jsonPath("$.custodia", hasSize(2)))
                .andExpect(jsonPath("$.ubicacionMaterial[0]").value(containsString("gaveta 12")));
    }

    @Test
    void plantillaPorServicioYNumerosUnicos() throws Exception {
        String pat = token("patologia");
        enviar(post("/api/patologia/plantillas"), pat, """
                {"tipo":"BIOPSIA","servicio":"Gastroenterología","nombre":"Biopsia gástrica",
                 "campos":[{"clave":"helicobacter","etiqueta":"H. pylori","seccion":"MICRO","tipo":"opcion","opciones":["Presente"],"requerido":true}]}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.helicobacter").exists());
        enviar(post("/api/patologia/plantillas"), pat, """
                {"tipo":"BIOPSIA","servicio":"Gastroenterología","nombre":"Biopsia gástrica",
                 "campos":[{"clave":"fragmentos","etiqueta":"Fragmentos","seccion":"MACRO","tipo":"numero","requerido":true},
                           {"clave":"helicobacter","etiqueta":"H. pylori","seccion":"MICRO","tipo":"opcion","opciones":["Presente","Ausente"],"requerido":true}]}""")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.servicio").value("Gastroenterología"));

        String medico = token("medico");
        long p = paciente(documento());
        String cuerpo = "{\"pacienteId\":%d,\"tipo\":\"PIEZA_QUIRURGICA\",\"prioridad\":\"URGENTE\",\"sitioAnatomico\":\"Apéndice\",\"numeroMuestras\":1}".formatted(p);
        String a = JsonPath.read(enviar(post("/api/patologia/casos"), medico, cuerpo).andReturn().getResponse().getContentAsString(), "$.resumen.numero");
        enviar(post("/api/patologia/casos"), medico, cuerpo).andExpect(jsonPath("$.resumen.numero").value(not(a)));
        enviar(post("/api/patologia/casos"), pat, cuerpo).andExpect(status().isForbidden());
    }

    // ---------- utilidades ----------

    private static String documento() {
        return String.valueOf(DOCUMENTO.incrementAndGet());
    }

    private long paciente(String doc) throws Exception {
        return id(enviar(post("/api/pacientes"), token("admisiones"), """
                {"tipoDocumento":"CC","numeroDocumento":"%s","nombres":"Pat","apellidos":"Prueba %s",
                 "fechaNacimiento":"1965-05-05","sexo":"FEMENINO","regimen":"PARTICULAR","confirmarNoDuplicado":true}"""
                .formatted(doc, doc)).andExpect(status().isCreated()));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String t, String json) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long id(ResultActions r) throws Exception {
        String json = r.andReturn().getResponse().getContentAsString();
        Number id = json.contains("\"resumen\"") ? JsonPath.read(json, "$.resumen.id") : JsonPath.read(json, "$.id");
        return id.longValue();
    }

    private String token(String usuario) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"password\":\"ClaveTest123*\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }
}
