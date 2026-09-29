package co.edu.ucentral.clinica.enfermeria;

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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Enfermería de punta a punta: orden del médico, administración segura, eventos y entrega de turno. */
@SpringBootTest(properties = {"app.seed.habilitado=true", "app.seed.password-prueba=ClaveTest123*"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class EnfermeriaIntegrationTest {

    private static final AtomicInteger DOCUMENTO = new AtomicInteger(400_000_000);

    @Autowired
    MockMvc mvc;

    @Test
    void insulinaExigeLosCincoCorrectosYDobleVerificacion() throws Exception {
        String medico = token("medico");
        String enf = token("enfermeria");
        String doc = documento();
        long paciente = paciente(doc);

        String creada = enviar(post("/api/enfermeria/ordenes"), medico, """
                {"pacienteId":%d,"tipo":"MEDICAMENTO","medicamentoId":%d,"dosis":"10 UI","via":"Subcutánea",
                 "frecuenciaHoras":12,"duracionHoras":48}""".formatted(paciente, medicamento(medico, "insulina")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.altoRiesgo").value(true))
                .andExpect(jsonPath("$.situacion").value("ACTIVA"))
                .andExpect(jsonPath("$.horarios[0].estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        long orden = ((Number) JsonPath.read(creada, "$.id")).longValue();
        String hora = JsonPath.read(creada, "$.horarios[0].programadaPara");
        String siguiente = JsonPath.read(creada, "$.horarios[1].programadaPara");

        // RFENF-005: enfermería se entera de la orden nueva
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + enf))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("alto riesgo"))));

        String url = "/api/enfermeria/ordenes/" + orden + "/ejecuciones";
        long insulina = medicamento(medico, "insulina");
        // Paciente equivocado
        enviar(post(url), enf, administrar(hora, "999", insulina, "10 UI", "Subcutánea", null))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("VERIFICACION_FALLIDA"))
                .andExpect(jsonPath("$.mensaje").value(containsString("documento")));
        // Dosis equivocada
        enviar(post(url), enf, administrar(hora, doc, insulina, "20 UI", "Subcutánea", null))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.mensaje").value(containsString("dosis")));
        // Alto riesgo sin segunda verificación, verificada por sí misma o con clave errada
        enviar(post(url), enf, administrar(hora, doc, insulina, "10 UI", "Subcutánea", null))
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("DOBLE_VALIDACION_REQUERIDA"));
        enviar(post(url), enf, administrar(hora, doc, insulina, "10 UI", "Subcutánea", "enfermeria"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("DOBLE_VALIDACION_INVALIDA"));
        enviar(post(url), enf, administrar(hora, doc, insulina, "10 UI", "Subcutánea", "medico").replace("ClaveTest123*", "mala"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("DOBLE_VALIDACION_INVALIDA"));

        // Todo correcto (tildes y mayúsculas no importan): queda con hora real y quién verificó
        enviar(post(url), enf, administrar(hora, doc, insulina, "10 ui", "subcutanea", "medico"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.horarios[0].estado").value("ADMINISTRADA"))
                .andExpect(jsonPath("$.horarios[0].ejecucion.dobleValidacionPor").value("medico"))
                .andExpect(jsonPath("$.horarios[0].ejecucion.usuario").value("enfermeria"))
                .andExpect(jsonPath("$.horarios[0].ejecucion.verificaciones").value(containsString("PACIENTE")));
        enviar(post(url), enf, administrar(hora, doc, insulina, "10 UI", "Subcutánea", "medico"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("YA_REGISTRADA"));
        enviar(post(url), enf, administrar(siguiente, doc, insulina, "10 UI", "Subcutánea", "medico"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("AUN_NO_ES_HORA"));

        // RFENF-008: el resumen del turno lo refleja sin volver a digitarlo
        mvc.perform(get("/api/enfermeria/pacientes/" + paciente + "/resumen-turno").header("Authorization", "Bearer " + enf))
                .andExpect(jsonPath("$.administrado[0]").value(containsString("Insulina")))
                .andExpect(jsonPath("$.texto").value(containsString("verificó medico")));
    }

    @Test
    void ordenSuspendidaNoSeEjecutaYLaAlergiaBloqueaLaOrden() throws Exception {
        String medico = token("medico");
        String enf = token("enfermeria");
        String doc = documento();
        long paciente = paciente(doc);
        enviar(post("/api/historia/" + paciente + "/antecedentes"), medico,
                "{\"tipo\":\"ALERGIA\",\"descripcion\":\"Penicilina\",\"severidad\":\"SEVERA\"}")
                .andExpect(status().isCreated());

        enviar(post("/api/enfermeria/ordenes"), medico, """
                {"pacienteId":%d,"tipo":"MEDICAMENTO","medicamentoId":%d,"dosis":"500 mg","via":"Oral",
                 "frecuenciaHoras":8,"duracionHoras":72}""".formatted(paciente, medicamento(medico, "amoxicilina")))
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("ALERGIA_MEDICAMENTO"));

        // Cuidado suspendido: ya no se puede registrar
        String cuidado = enviar(post("/api/enfermeria/ordenes"), medico, """
                {"pacienteId":%d,"tipo":"CUIDADO","descripcion":"Curación de herida quirúrgica","frecuenciaHoras":8,
                 "duracionHoras":24}""".formatted(paciente))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.altoRiesgo").value(false))
                .andReturn().getResponse().getContentAsString();
        long idCuidado = ((Number) JsonPath.read(cuidado, "$.id")).longValue();
        String horaCuidado = JsonPath.read(cuidado, "$.horarios[0].programadaPara");
        enviar(post("/api/enfermeria/ordenes/" + idCuidado + "/suspender"), medico, "{\"motivo\":\"Herida cerrada\"}")
                .andExpect(jsonPath("$.situacion").value("SUSPENDIDA"));
        enviar(post("/api/enfermeria/ordenes/" + idCuidado + "/ejecuciones"), enf,
                "{\"programadaPara\":\"%s\",\"resultado\":\"REALIZADO\"}".formatted(horaCuidado))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("ORDEN_NO_VIGENTE"))
                .andExpect(jsonPath("$.mensaje").value(containsString("suspendida")));
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + enf))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Orden suspendida"))));

        // Dosis rechazada por el paciente: exige motivo y avisa al médico
        String acetaminofen = enviar(post("/api/enfermeria/ordenes"), medico, """
                {"pacienteId":%d,"tipo":"MEDICAMENTO","medicamentoId":%d,"dosis":"1 g","via":"Oral",
                 "frecuenciaHoras":6,"duracionHoras":24}""".formatted(paciente, medicamento(medico, "acetaminofen")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long idAcet = ((Number) JsonPath.read(acetaminofen, "$.id")).longValue();
        String horaAcet = JsonPath.read(acetaminofen, "$.horarios[0].programadaPara");
        enviar(post("/api/enfermeria/ordenes/" + idAcet + "/ejecuciones"), enf,
                "{\"programadaPara\":\"%s\",\"resultado\":\"RECHAZADA_POR_PACIENTE\"}".formatted(horaAcet))
                .andExpect(status().isBadRequest());
        enviar(post("/api/enfermeria/ordenes/" + idAcet + "/ejecuciones"), enf,
                "{\"programadaPara\":\"%s\",\"resultado\":\"RECHAZADA_POR_PACIENTE\",\"motivo\":\"Náuseas\"}".formatted(horaAcet))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.horarios[0].estado").value("RECHAZADA_POR_PACIENTE"));
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Dosis no administrada"))));

        // Vista consolidada y censo
        mvc.perform(get("/api/enfermeria/pacientes/" + paciente + "/ordenes").header("Authorization", "Bearer " + enf))
                .andExpect(jsonPath("$.alergias[0]").value(containsString("Penicilina")))
                .andExpect(jsonPath("$.ordenes[*].situacion").value(hasItem("SUSPENDIDA")));
        mvc.perform(get("/api/enfermeria/censo").header("Authorization", "Bearer " + enf))
                .andExpect(jsonPath("$[*].paciente.id").value(hasItem((int) paciente)));
    }

    @Test
    void eventoGraveAvisaYLaEntregaDeTurnoQuedaGuardada() throws Exception {
        String medico = token("medico");
        String enf = token("enfermeria");
        long paciente = paciente(documento());

        // RFENF-010: la orden de laboratorio llega a enfermería para la toma de muestra
        String catalogo = mvc.perform(get("/api/laboratorio/examenes").header("Authorization", "Bearer " + token("laboratorio")))
                .andReturn().getResponse().getContentAsString();
        List<Number> glucosa = JsonPath.read(catalogo, "$[?(@.codigo == 'GLUCOSA')].id");
        enviar(post("/api/laboratorio/ordenes"), medico, "{\"pacienteId\":%d,\"prioridad\":\"HOSPITALIZADO\",\"examenIds\":[%d]}"
                .formatted(paciente, glucosa.getFirst().longValue())).andExpect(status().isCreated());
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + enf))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Toma de muestra"))));

        String evento = """
                {"tipo":"CAIDA","severidad":"GRAVE","ocurridoEn":"%s","lugar":"Habitación 204",
                 "descripcion":"Paciente se levantó sin ayuda y cayó","accionesInmediatas":"Valoración, inmovilización y aviso al médico"}""";
        enviar(post("/api/enfermeria/pacientes/" + paciente + "/eventos"), enf,
                evento.formatted(Instant.now().plusSeconds(3600))).andExpect(status().isBadRequest());
        enviar(post("/api/enfermeria/pacientes/" + paciente + "/eventos"), enf,
                evento.formatted(Instant.now().minusSeconds(600)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportadoPor").value("enfermeria"));
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + token("auditoria")))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Evento adverso grave"))));
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Evento adverso grave"))));

        enviar(post("/api/enfermeria/pacientes/" + paciente + "/notas"), enf, "{\"texto\":\"Paciente tranquilo, sin dolor\"}")
                .andExpect(status().isCreated());
        enviar(post("/api/enfermeria/pacientes/" + paciente + "/entregas"), enf, "{\"nota\":\"Vigilar riesgo de caída\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumen").value(containsString("Caída")))
                .andExpect(jsonPath("$.resumen").value(containsString("Paciente tranquilo")))
                .andExpect(jsonPath("$.resumen").value(containsString("Glucosa")))
                .andExpect(jsonPath("$.nota").value("Vigilar riesgo de caída"));
        mvc.perform(get("/api/enfermeria/pacientes/" + paciente + "/entregas").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$[0].entregadoPor").value("enfermeria"));
    }

    @Test
    void cadaRolHaceSoloLoSuyo() throws Exception {
        long paciente = paciente(documento());
        String orden = "{\"pacienteId\":%d,\"tipo\":\"DIETA\",\"descripcion\":\"Dieta blanda\",\"duracionHoras\":24}".formatted(paciente);
        enviar(post("/api/enfermeria/ordenes"), token("enfermeria"), orden).andExpect(status().isForbidden());
        long id = ((Number) JsonPath.read(enviar(post("/api/enfermeria/ordenes"), token("medico"), orden)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        enviar(post("/api/enfermeria/ordenes/" + id + "/ejecuciones"), token("medico"),
                "{\"programadaPara\":\"2026-01-01T00:00:00Z\",\"resultado\":\"REALIZADO\"}").andExpect(status().isForbidden());
        mvc.perform(get("/api/enfermeria/pacientes/" + paciente + "/ordenes").header("Authorization", "Bearer " + token("auditoria")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/enfermeria/pacientes/" + paciente + "/ordenes").header("Authorization", "Bearer " + token("laboratorio")))
                .andExpect(status().isForbidden());
    }

    // ---------- utilidades ----------

    private static String administrar(String hora, String doc, long medicamento, String dosis, String via, String verifica) {
        String doble = verifica == null ? "" : ",\"dobleValidacion\":{\"usuario\":\"%s\",\"password\":\"ClaveTest123*\"}".formatted(verifica);
        return """
                {"programadaPara":"%s","resultado":"ADMINISTRADA",
                 "verificacion":{"documentoPaciente":"%s","medicamentoId":%d,"dosis":"%s","via":"%s"}%s}"""
                .formatted(hora, doc, medicamento, dosis, via, doble);
    }

    private long medicamento(String t, String texto) throws Exception {
        String json = mvc.perform(get("/api/historia/medicamentos").param("texto", texto).header("Authorization", "Bearer " + t))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(json, "$[0].id");
        return id.longValue();
    }

    private static String documento() {
        return String.valueOf(DOCUMENTO.incrementAndGet());
    }

    private long paciente(String doc) throws Exception {
        return id(enviar(post("/api/pacientes"), token("admisiones"), """
                {"tipoDocumento":"CC","numeroDocumento":"%s","nombres":"Enf","apellidos":"Prueba %s",
                 "fechaNacimiento":"1960-07-21","sexo":"FEMENINO","regimen":"PARTICULAR","confirmarNoDuplicado":true}"""
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
