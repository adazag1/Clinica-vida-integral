package co.edu.ucentral.clinica.agenda;

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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Agendamiento de punta a punta contra PostgreSQL real, con los profesionales de SemillaAgenda. */
@SpringBootTest(properties = {"app.seed.habilitado=true", "app.seed.password-prueba=ClaveTest123*"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class AgendaIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final AtomicInteger DOCUMENTO = new AtomicInteger(700_000_000);
    /** Cada prueba usa una semana distinta para no chocar con las citas de las demás. */
    private static final AtomicInteger SEMANA = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;

    private LocalDate lunes() {
        return LocalDate.now(BOGOTA).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(SEMANA.getAndIncrement());
    }

    private static String hora(LocalDate dia, int h, int m) {
        return ZonedDateTime.of(dia, LocalTime.of(h, m), BOGOTA).toInstant().toString();
    }

    @Test
    void noPermiteDobleAsignacionNiFueraDeHorario() throws Exception {
        String t = token("agendamiento");
        LocalDate dia = lunes();
        long perez = profesional(t, "Dr. Juan Pérez");
        long control = tipo(t, "CONTROL");

        cita(t, paciente(t), perez, control, hora(dia, 9, 0), "NORMAL")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$.historial[0].accion").value("CREADA"));
        // Se cruza con la de las 9:00 (control de 20 min)
        cita(t, paciente(t), perez, control, hora(dia, 9, 10), "NORMAL")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("HORARIO_OCUPADO"));
        // A la hora del almuerzo no atiende
        cita(t, paciente(t), perez, control, hora(dia, 12, 30), "NORMAL")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("FUERA_DE_HORARIO"));
        // 16:00-17:00 está reservado para prioridades
        cita(t, paciente(t), perez, control, hora(dia, 16, 0), "NORMAL")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("CUPO_RESERVADO"));
        cita(t, paciente(t), perez, control, hora(dia, 16, 0), "URGENTE").andExpect(status().isCreated());
    }

    @Test
    void dosPersonasAgendandoElMismoCupoAlTiempoSoloUnaLoConsigue() throws Exception {
        String t = token("agendamiento");
        LocalDate dia = lunes();
        long ramirez = profesional(t, "Dra. Laura Ramírez");
        long control = tipo(t, "CONTROL");
        List<Long> pacientes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pacientes.add(paciente(t));
        }

        ExecutorService hilos = Executors.newFixedThreadPool(pacientes.size());
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (Long p : pacientes) {
            Callable<Integer> reservar = () -> {
                salida.await();
                return cita(t, p, ramirez, control, hora(dia, 10, 0), "NORMAL").andReturn().getResponse().getStatus();
            };
            resultados.add(hilos.submit(reservar));
        }
        salida.countDown();
        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> f : resultados) {
            estados.add(f.get());
        }
        hilos.shutdown();

        assertThat(estados).filteredOn(s -> s == 201).hasSize(1);
        assertThat(estados).filteredOn(s -> s == 422).hasSize(pacientes.size() - 1);
    }

    @Test
    void alCancelarElCupoPasaAlPrimeroDeLaListaDeEspera() throws Exception {
        String t = token("agendamiento");
        LocalDate dia = lunes();
        long rojas = profesional(t, "Dr. Felipe Rojas");
        long control = tipo(t, "CONTROL");
        long cardiologia = especialidad(t, "Cardiología");

        long citaId = id(cita(t, paciente(t), rojas, control, hora(dia, 8, 0), "NORMAL").andExpect(status().isCreated()));
        long normal = paciente(t);
        long urgente = paciente(t);
        enviar(post("/api/lista-espera"), t, """
                {"pacienteId":%d,"especialidadId":%d,"tipoCitaId":%d,"prioridad":"NORMAL"}"""
                .formatted(normal, cardiologia, control)).andExpect(status().isCreated());
        enviar(post("/api/lista-espera"), t, """
                {"pacienteId":%d,"especialidadId":%d,"tipoCitaId":%d,"prioridad":"URGENTE","motivo":"Dolor torácico"}"""
                .formatted(urgente, cardiologia, control)).andExpect(status().isCreated());

        // Aunque llegó después, el urgente recibe el cupo
        enviar(post("/api/citas/" + citaId + "/cancelar"), t, "{\"motivo\":\"SOLICITUD_PACIENTE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cita.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.asignadaListaEspera.paciente.id").value((int) urgente))
                .andExpect(jsonPath("$.asignadaListaEspera.inicio").value(hora(dia, 8, 0)));

        mvc.perform(get("/api/lista-espera").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$[*].paciente.id").value(hasItem((int) normal)));
    }

    @Test
    void reprogramarConservaElHistorialYLiberaElCupo() throws Exception {
        String t = token("agendamiento");
        LocalDate dia = lunes();
        long perez = profesional(t, "Dr. Juan Pérez");
        long control = tipo(t, "CONTROL");
        long p = paciente(t);
        long original = id(cita(t, p, perez, control, hora(dia, 10, 0), "NORMAL"));

        long nueva = id(enviar(post("/api/citas/" + original + "/reprogramar"), t,
                "{\"inicio\":\"%s\",\"detalle\":\"El paciente viaja\"}".formatted(hora(dia.plusDays(1), 11, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cita.reprogramadaDesdeId").value((int) original)));

        mvc.perform(get("/api/citas/" + original).header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.estado").value("REPROGRAMADA"))
                .andExpect(jsonPath("$.historial[*].accion").value(hasItem("REPROGRAMADA")));
        assertThat(nueva).isNotEqualTo(original);
        // El horario original quedó libre
        cita(t, paciente(t), perez, control, hora(dia, 10, 0), "NORMAL").andExpect(status().isCreated());
    }

    @Test
    void elPacienteConfirmaYCancelaDesdeElEnlaceSinIniciarSesion() throws Exception {
        String t = token("agendamiento");
        LocalDate dia = lunes();
        String enlace = JsonPath.read(cita(t, paciente(t), profesional(t, "Dra. Natalia Ortiz"), tipo(t, "PRIMERA_VEZ"),
                hora(dia, 14, 0), "NORMAL").andReturn().getResponse().getContentAsString(), "$.enlacePaciente");
        String token = enlace.substring(enlace.lastIndexOf('/') + 1);

        mvc.perform(get("/api/publico/citas/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeResponder").value(true))
                .andExpect(jsonPath("$.paciente").value("Paciente"));
        mvc.perform(post("/api/publico/citas/" + token + "/confirmar"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        mvc.perform(post("/api/publico/citas/" + token + "/cancelar"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
        mvc.perform(get("/api/publico/citas/no-existe")).andExpect(status().isNotFound());
    }

    @Test
    void agendarUnaEcografiaAvisaARadiologia() throws Exception {
        String t = token("agendamiento");
        cita(t, paciente(t), profesional(t, "Dra. Natalia Ortiz"), tipo(t, "ECOGRAFIA"), hora(lunes(), 8, 0), "NORMAL")
                .andExpect(status().isCreated());
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + token("radiologia")))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem("Preparación: Ecografía")));
    }

    @Test
    void unBloqueoImpideAgendarYNoSePuedeBloquearSobreCitas() throws Exception {
        String t = token("agendamiento");
        LocalDate dia = lunes();
        long rojas = profesional(t, "Dr. Felipe Rojas");
        long control = tipo(t, "CONTROL");
        enviar(post("/api/agenda/bloqueos"), t, """
                {"profesionalId":%d,"inicio":"%s","fin":"%s","motivo":"CAPACITACION"}"""
                .formatted(rojas, hora(dia, 8, 0), hora(dia, 10, 0))).andExpect(status().isCreated());
        cita(t, paciente(t), rojas, control, hora(dia, 9, 0), "NORMAL")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("AGENDA_BLOQUEADA"));

        cita(t, paciente(t), rojas, control, hora(dia, 11, 0), "NORMAL").andExpect(status().isCreated());
        enviar(post("/api/agenda/bloqueos"), t, """
                {"profesionalId":%d,"inicio":"%s","fin":"%s","motivo":"PERMISO"}"""
                .formatted(rojas, hora(dia, 10, 30), hora(dia, 12, 0)))
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("BLOQUEO_CON_CITAS"));
    }

    @Test
    void laDisponibilidadNoOfreceCuposOcupados() throws Exception {
        String t = token("agendamiento");
        LocalDate dia = lunes();
        long perez = profesional(t, "Dr. Juan Pérez");
        long control = tipo(t, "CONTROL");
        cita(t, paciente(t), perez, control, hora(dia, 8, 0), "NORMAL").andExpect(status().isCreated());
        String cupos = mvc.perform(get("/api/agenda/disponibilidad").header("Authorization", "Bearer " + t)
                        .param("profesionalId", String.valueOf(perez)).param("tipoCitaId", String.valueOf(control))
                        .param("desde", dia.toString()).param("dias", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> inicios = JsonPath.read(cupos, "$[*].inicio");
        assertThat(inicios).doesNotContain(hora(dia, 8, 0)).contains(hora(dia, 8, 20));
    }

    // ---------- utilidades ----------

    private ResultActions cita(String t, long paciente, long profesional, long tipo, String inicio, String prioridad)
            throws Exception {
        return enviar(post("/api/citas"), t, """
                {"pacienteId":%d,"profesionalId":%d,"tipoCitaId":%d,"inicio":"%s","prioridad":"%s"}"""
                .formatted(paciente, profesional, tipo, inicio, prioridad));
    }

    private long paciente(String t) throws Exception {
        String doc = String.valueOf(DOCUMENTO.incrementAndGet());
        return id(enviar(post("/api/pacientes"), token("admisiones"), """
                {"tipoDocumento":"CC","numeroDocumento":"%s","nombres":"Paciente","apellidos":"Agenda %s",
                 "fechaNacimiento":"1990-01-01","sexo":"FEMENINO","email":"p%s@correo.test","regimen":"PARTICULAR",
                 "confirmarNoDuplicado":true}""".formatted(doc, doc, doc)).andExpect(status().isCreated()));
    }

    private long profesional(String t, String nombre) throws Exception {
        return catalogo(t, "$.profesionales[?(@.nombre == '" + nombre + "')].id");
    }

    private long tipo(String t, String codigo) throws Exception {
        return catalogo(t, "$.tipos[?(@.codigo == '" + codigo + "')].id");
    }

    private long especialidad(String t, String nombre) throws Exception {
        return catalogo(t, "$.especialidades[?(@.nombre == '" + nombre + "')].id");
    }

    private long catalogo(String t, String ruta) throws Exception {
        String json = mvc.perform(get("/api/agenda/catalogos").header("Authorization", "Bearer " + t))
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(json, ruta);
        return ids.getFirst().longValue();
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String t, String json) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long id(ResultActions r) throws Exception {
        String json = r.andReturn().getResponse().getContentAsString();
        Number id = json.contains("\"cita\"") ? JsonPath.read(json, "$.cita.id") : JsonPath.read(json, "$.id");
        return id.longValue();
    }

    private String token(String usuario) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"password\":\"ClaveTest123*\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }
}
