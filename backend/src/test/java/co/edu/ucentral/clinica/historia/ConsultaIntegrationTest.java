package co.edu.ucentral.clinica.historia;

import co.edu.ucentral.clinica.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

/** Consulta médica de punta a punta: desde admisiones hasta la firma. */
@SpringBootTest(properties = {"app.seed.habilitado=true", "app.seed.password-prueba=ClaveTest123*"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ConsultaIntegrationTest {

    private static final AtomicInteger DOCUMENTO = new AtomicInteger(500_000_000);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flujoCompletoDesdeAdmisionesHastaLaFirma() throws Exception {
        String admisiones = token("admisiones");
        String enfermeria = token("enfermeria");
        String medico = token("medico");
        long paciente = paciente(admisiones);

        // Enfermería registra una alergia severa a la penicilina
        enviar(post("/api/historia/" + paciente + "/antecedentes"), enfermeria, """
                {"tipo":"ALERGIA","descripcion":"Penicilina","detalle":"Anafilaxia","severidad":"SEVERA"}""")
                .andExpect(status().isCreated());

        // Admisión por consulta externa (particular: sin verificación de cobertura) y paso a consulta
        long episodio = id(enviar(post("/api/episodios"), admisiones,
                "{\"pacienteId\":%d,\"tipoAtencion\":\"CONSULTA_EXTERNA\",\"motivoConsulta\":\"Dolor de garganta y fiebre\"}"
                        .formatted(paciente)));
        enviar(post("/api/episodios/" + episodio + "/consentimientos"), admisiones,
                "{\"tipo\":\"CONSENTIMIENTO_INFORMADO\",\"otorgadoPor\":\"El paciente\",\"aceptado\":true}");
        enviar(post("/api/episodios/" + episodio + "/enviar-a-consulta"), admisiones, "").andExpect(status().isOk());

        // El médico lo ve en su lista y abre la consulta; el motivo viene de admisiones
        mvc.perform(get("/api/consultas/por-atender").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$[*].episodioId").value(hasItem((int) episodio)));
        long consulta = id(enviar(post("/api/consultas"), medico,
                "{\"pacienteId\":%d,\"episodioId\":%d}".formatted(paciente, episodio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.motivoConsulta").value("Dolor de garganta y fiebre")));
        mvc.perform(get("/api/episodios/" + episodio).header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.estado").value("EN_ATENCION"));

        // RFMT-002: el resumen muestra la alergia como alerta
        mvc.perform(get("/api/historia/" + paciente + "/resumen").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.alergias[0].descripcion").value("Penicilina"))
                .andExpect(jsonPath("$.alertas").value(hasItem("Alergia severa: Penicilina")));

        // RFMT-007: no se puede firmar incompleta
        enviar(post("/api/consultas/" + consulta + "/finalizar"), medico, "{\"password\":\"ClaveTest123*\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("CONSULTA_INCOMPLETA"));

        // RFMT-003: amoxicilina bloqueada por la alergia; azitromicina permitida
        long amoxicilina = catalogo(medico, "/api/historia/medicamentos?texto=amoxicilina");
        long azitromicina = catalogo(medico, "/api/historia/medicamentos?texto=azitromicina");
        String receta = "{\"medicamentoId\":%d,\"dosis\":\"500 mg\",\"frecuencia\":\"Cada 24 horas\",\"duracionDias\":3}";
        enviar(post("/api/consultas/" + consulta + "/prescripciones"), medico, receta.formatted(amoxicilina))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("ALERGIA_MEDICAMENTO"))
                .andExpect(jsonPath("$.mensaje").value(containsString("Penicilina")));
        enviar(post("/api/consultas/" + consulta + "/prescripciones"), medico, receta.formatted(azitromicina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prescripciones[0].medicamento").value("Azitromicina 500 mg"));

        long faringitis = catalogo(medico, "/api/historia/diagnosticos?texto=J02.9");
        enviar(put("/api/consultas/" + consulta), medico, """
                {"motivoConsulta":"Dolor de garganta y fiebre","enfermedadActual":"Cuadro de 2 días de odinofagia",
                 "examenFisico":"Orofaringe eritematosa, sin exudado","plan":"Manejo ambulatorio, control en 5 días",
                 "diagnosticos":[{"diagnosticoId":%d,"principal":true,"confirmado":false}]}""".formatted(faringitis))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendientes").isEmpty());

        // RFMT-011: firma con la contraseña del médico
        enviar(post("/api/consultas/" + consulta + "/finalizar"), medico, "{\"password\":\"otra\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("FIRMA_INVALIDA"));
        enviar(post("/api/consultas/" + consulta + "/finalizar"), medico, "{\"password\":\"ClaveTest123*\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FIRMADA"))
                .andExpect(jsonPath("$.firmaHash").isNotEmpty());
        mvc.perform(get("/api/episodios/" + episodio).header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.estado").value("CERRADO"));

        // Firmada, ya no se modifica; la huella confirma que no fue alterada
        enviar(put("/api/consultas/" + consulta), medico, "{\"plan\":\"cambio\",\"diagnosticos\":[]}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("CONSULTA_FIRMADA"));
        mvc.perform(get("/api/consultas/" + consulta + "/verificar-firma").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.valida").value(true));

        // Si alguien altera el registro directamente en la base de datos, la verificación lo detecta
        jdbc.update("update consulta set plan = 'Alterado' where id = ?", consulta);
        mvc.perform(get("/api/consultas/" + consulta + "/verificar-firma").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.valida").value(false));

        // RFMT-009: queda en el historial y la fórmula vigente aparece en el resumen
        mvc.perform(get("/api/historia/" + paciente + "/consultas").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$[0].diagnosticos[0].codigo").value("J02.9"));
        mvc.perform(get("/api/historia/" + paciente + "/resumen").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.medicamentosActuales[*].nombre").value(hasItem("Azitromicina 500 mg")));
    }

    @Test
    void signosCriticosGeneranAlertaYDestacanAlPaciente() throws Exception {
        String medico = token("medico");
        long paciente = paciente(token("admisiones"));
        enviar(post("/api/historia/" + paciente + "/signos"), token("enfermeria"), """
                {"presionSistolica":85,"presionDiastolica":50,"frecuenciaCardiaca":128,"saturacionOxigeno":86,
                 "temperatura":38.4}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alertas[?(@.campo == 'saturacionOxigeno')].nivel").value(hasItem("CRITICO")))
                .andExpect(jsonPath("$.alertas[?(@.campo == 'frecuenciaCardiaca')].nivel").value(hasItem("ANORMAL")));
        mvc.perform(get("/api/historia/" + paciente + "/resumen").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.alertas[0]").value(containsString("Saturación de oxígeno 86 %")));
    }

    @Test
    void soloElMedicoAbreConsultas() throws Exception {
        long paciente = paciente(token("admisiones"));
        enviar(post("/api/consultas"), token("enfermeria"), "{\"pacienteId\":%d}".formatted(paciente))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/historia/" + paciente + "/resumen").header("Authorization", "Bearer " + token("facturacion")))
                .andExpect(status().isForbidden());
    }

    // ---------- utilidades ----------

    private long paciente(String admisiones) throws Exception {
        String doc = String.valueOf(DOCUMENTO.incrementAndGet());
        return id(enviar(post("/api/pacientes"), admisiones, """
                {"tipoDocumento":"CC","numeroDocumento":"%s","nombres":"Consulta","apellidos":"Prueba %s",
                 "fechaNacimiento":"1980-06-15","sexo":"MASCULINO","regimen":"PARTICULAR","confirmarNoDuplicado":true}"""
                .formatted(doc, doc)).andExpect(status().isCreated()));
    }

    private long catalogo(String t, String url) throws Exception {
        String json = mvc.perform(get(url).header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(json, "$[*].id");
        return ids.getFirst().longValue();
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
