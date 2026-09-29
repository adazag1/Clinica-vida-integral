package co.edu.ucentral.clinica.admisiones;

import co.edu.ucentral.clinica.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo de admisión de punta a punta contra PostgreSQL real (Testcontainers). */
@SpringBootTest(properties = {"app.seed.habilitado=true", "app.seed.password-prueba=ClaveTest123*"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class AdmisionesIntegrationTest {

    /** Documentos distintos en cada prueba: la base se comparte entre clases de prueba. */
    private static final AtomicInteger DOCUMENTO = new AtomicInteger(900_000_000);

    @Autowired
    MockMvc mvc;

    @Test
    void flujoCompletoDeUrgenciasHastaLaListaDelMedico() throws Exception {
        String admisiones = token("admisiones");
        String enfermeria = token("enfermeria");
        long pacienteId = crearPaciente(admisiones, "Laura", "Rincón Díaz", "1985-03-14", "3001112233");

        long episodioId = id(enviar(post("/api/episodios"), admisiones, """
                {"pacienteId":%d,"tipoAtencion":"URGENCIAS","motivoConsulta":"Dificultad respiratoria"}
                """.formatted(pacienteId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(startsWith("AT-")))
                .andExpect(jsonPath("$.estado").value("EN_ADMISION"))
                .andExpect(jsonPath("$.pendientes[*].codigo").value(hasItem("TRIAGE"))));

        // Con datos incompletos no puede pasar a consulta
        enviar(post("/api/episodios/" + episodioId + "/enviar-a-consulta"), enfermeria, "")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("EPISODIO_INCOMPLETO"));

        // Saturación 88 % sugiere triage II; asignar otro nivel exige justificación
        String signos = """
                {"frecuenciaCardiaca":98,"frecuenciaRespiratoria":24,"presionSistolica":130,
                 "presionDiastolica":85,"temperatura":37.2,"saturacionOxigeno":88,"glasgow":15,"dolor":3}""";
        enviar(put("/api/episodios/" + episodioId + "/triage"), enfermeria,
                "{\"signos\":" + signos + ",\"sintomaPrincipal\":\"Disnea\",\"nivelAsignado\":3}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.justificacionAjuste").exists());
        enviar(put("/api/episodios/" + episodioId + "/triage"), enfermeria,
                "{\"signos\":" + signos + ",\"sintomaPrincipal\":\"Disnea\",\"nivelAsignado\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triage.nivelSugerido").value(2))
                .andExpect(jsonPath("$.triage.clasificadoPor").value("enfermeria"));

        enviar(put("/api/episodios/" + episodioId + "/excepcion-urgencia"), enfermeria,
                "{\"justificacion\":\"Paciente con hipoxemia, no puede esperar autorización\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCobertura").value("EXCEPCION_URGENCIA"));
        enviar(post("/api/episodios/" + episodioId + "/consentimientos"), admisiones, """
                {"tipo":"CONSENTIMIENTO_INFORMADO","otorgadoPor":"Laura Rincón Díaz","aceptado":true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentimientos[0].registradoPor").value("admisiones"))
                .andExpect(jsonPath("$.pendientes").isEmpty());

        enviar(post("/api/episodios/" + episodioId + "/enviar-a-consulta"), enfermeria, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_ESPERA_CONSULTA"));

        mvc.perform(get("/api/episodios/lista-espera").header("Authorization", "Bearer " + token("medico")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esperaConsulta[*].id").value(hasItem((int) episodioId)));

        // Ya en espera de consulta no se puede modificar la admisión
        enviar(put("/api/episodios/" + episodioId + "/triage"), enfermeria,
                "{\"signos\":" + signos + ",\"sintomaPrincipal\":\"Disnea\",\"nivelAsignado\":2}")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("EPISODIO_NO_EDITABLE"));
    }

    @Test
    void noPermiteDosAtencionesAbiertasParaElMismoPaciente() throws Exception {
        String admisiones = token("admisiones");
        long pacienteId = crearPaciente(admisiones, "Pedro", "Salas Mora", "1970-01-20", null);
        String episodio = "{\"pacienteId\":%d,\"tipoAtencion\":\"CONSULTA_EXTERNA\",\"motivoConsulta\":\"Control\"}"
                .formatted(pacienteId);
        enviar(post("/api/episodios"), admisiones, episodio).andExpect(status().isCreated());
        enviar(post("/api/episodios"), admisiones, episodio)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("EPISODIO_ABIERTO"));
    }

    @Test
    void detectaDuplicadoConNombreMalEscritoYExigeConfirmacion() throws Exception {
        String admisiones = token("admisiones");
        crearPaciente(admisiones, "John", "Pérez Gómez", "1990-05-10", "3205550000");

        String parecido = pacienteJson("CC", String.valueOf(DOCUMENTO.incrementAndGet()), "Jhon", "Peres Gomez",
                "1990-05-10", null, false);
        enviar(post("/api/pacientes/posibles-duplicados"), admisiones, parecido)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paciente.nombreCompleto").value("John Pérez Gómez"))
                .andExpect(jsonPath("$[0].coincidencias").value(hasItem("Misma fecha de nacimiento")));

        enviar(post("/api/pacientes"), admisiones, parecido)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("POSIBLE_DUPLICADO"));
        enviar(post("/api/pacientes"), admisiones, parecido.replace("\"confirmarNoDuplicado\":false",
                "\"confirmarNoDuplicado\":true"))
                .andExpect(status().isCreated());
    }

    @Test
    void noPermiteRegistrarDosVecesElMismoDocumento() throws Exception {
        String admisiones = token("admisiones");
        String documento = String.valueOf(DOCUMENTO.incrementAndGet());
        enviar(post("/api/pacientes"), admisiones,
                pacienteJson("CC", documento, "Ana", "Torres Vélez", "1995-07-01", null, true))
                .andExpect(status().isCreated());
        enviar(post("/api/pacientes"), admisiones,
                pacienteJson("CC", documento, "Ana María", "Torres", "1995-07-01", null, true))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("PACIENTE_EXISTENTE"));
    }

    @Test
    void pacienteSinDocumentoRecibeIdentificadorTemporalYLuegoSuDocumentoConTrazabilidad() throws Exception {
        String admisiones = token("admisiones");
        long id = id(enviar(post("/api/pacientes"), admisiones, """
                {"tipoDocumento":"TEMPORAL","nombres":"NN","apellidos":"Masculino adulto","sexo":"MASCULINO",
                 "confirmarNoDuplicado":true}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroDocumento").value(startsWith("TMP-")))
                .andExpect(jsonPath("$.identificacionTemporal").value(true)));

        // Sin documento solo puede ingresar por urgencias
        enviar(post("/api/episodios"), admisiones,
                "{\"pacienteId\":%d,\"tipoAtencion\":\"CONSULTA_EXTERNA\",\"motivoConsulta\":\"Control\"}".formatted(id))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("IDENTIFICACION_REQUERIDA"));

        String documento = String.valueOf(DOCUMENTO.incrementAndGet());
        String sinMotivo = pacienteJson("CC", documento, "Mario", "Quintero León", "1980-02-02", null, true);
        enviar(put("/api/pacientes/" + id), admisiones, sinMotivo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.motivo").exists());
        enviar(put("/api/pacientes/" + id), admisiones,
                sinMotivo.replace("}", ",\"motivo\":\"Familiar presentó la cédula\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroDocumento").value(documento))
                .andExpect(jsonPath("$.identificacionTemporal").value(false));

        mvc.perform(get("/api/pacientes/" + id + "/cambios").header("Authorization", "Bearer " + admisiones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.campo == 'numeroDocumento')].valorNuevo").value(hasItem(documento)))
                .andExpect(jsonPath("$[0].motivo").value("Familiar presentó la cédula"))
                .andExpect(jsonPath("$[0].usuario").value("admisiones"));
    }

    @Test
    void buscaSinImportarTildesNiOrdenDeLasPalabras() throws Exception {
        String admisiones = token("admisiones");
        crearPaciente(admisiones, "Sofía", "Ñáñez Úsuga", "2001-11-30", null);
        mvc.perform(get("/api/pacientes").param("texto", "usuga sofia")
                        .header("Authorization", "Bearer " + token("medico")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nombreCompleto").value(hasItem("Sofía Ñáñez Úsuga")));
    }

    @Test
    void rechazaSignosVitalesImposibles() throws Exception {
        enviar(post("/api/episodios/triage/sugerencia"), token("enfermeria"), """
                {"signos":{"frecuenciaCardiaca":800,"frecuenciaRespiratoria":16,"presionSistolica":80,
                 "presionDiastolica":120,"temperatura":36.5,"saturacionOxigeno":97,"glasgow":15,"dolor":0}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['signos.frecuenciaCardiaca']").exists())
                .andExpect(jsonPath("$.campos['signos.presionCoherente']").exists());
    }

    @Test
    void elMedicoNoPuedeRegistrarPacientes() throws Exception {
        enviar(post("/api/pacientes"), token("medico"),
                pacienteJson("CC", String.valueOf(DOCUMENTO.incrementAndGet()), "X", "Y", "2000-01-01", null, true))
                .andExpect(status().isForbidden());
    }

    // ---------- utilidades ----------

    private long crearPaciente(String token, String nombres, String apellidos, String fechaNacimiento,
                               String telefono) throws Exception {
        return id(enviar(post("/api/pacientes"), token, pacienteJson("CC", String.valueOf(DOCUMENTO.incrementAndGet()),
                nombres, apellidos, fechaNacimiento, telefono, true))
                .andExpect(status().isCreated()));
    }

    private static String pacienteJson(String tipo, String documento, String nombres, String apellidos,
                                       String fechaNacimiento, String telefono, boolean confirmar) {
        return """
                {"tipoDocumento":"%s","numeroDocumento":"%s","nombres":"%s","apellidos":"%s",
                 "fechaNacimiento":"%s","sexo":"FEMENINO","telefono":%s,"aseguradora":"EPS Prueba",
                 "regimen":"CONTRIBUTIVO","confirmarNoDuplicado":%s}"""
                .formatted(tipo, documento, nombres, apellidos, fechaNacimiento,
                        telefono == null ? "null" : "\"" + telefono + "\"", confirmar);
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String token, String json) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long id(ResultActions acciones) throws Exception {
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(acciones.andReturn().getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private String token(String usuario) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"password\":\"ClaveTest123*\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(res.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
