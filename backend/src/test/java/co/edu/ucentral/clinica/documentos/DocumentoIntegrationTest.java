package co.edu.ucentral.clinica.documentos;

import co.edu.ucentral.clinica.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gestión documental: carga, versiones, acceso por rol, búsqueda, ciclo de vida, solicitudes y reportes. */
@SpringBootTest(properties = {"app.seed.habilitado=true", "app.seed.password-prueba=ClaveTest123*"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class DocumentoIntegrationTest {

    private static final AtomicInteger DOCUMENTO = new AtomicInteger(810_000_000);
    private static final byte[] PDF_1 = "%PDF-1.4\n% version uno".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_2 = "%PDF-1.4\n% version dos corregida".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    MockMvc mvc;

    @Test
    void cargaVersionaYControlaElAccesoPorRol() throws Exception {
        String archivo = token("archivo");
        String admisiones = token("admisiones");
        String doc = documento();
        long paciente = paciente(doc);

        // Admisiones: consentimientos sí, documentos clínicos no
        cargar(admisiones, paciente, "CONSENTIMIENTO", "Consentimiento de hospitalización", PDF_1).andExpect(status().isCreated());
        cargar(admisiones, paciente, "EPICRISIS", "Epicrisis", PDF_1).andExpect(status().isForbidden());
        // Un ejecutable renombrado a .pdf se rechaza por su contenido
        mvc.perform(conDatos(multipart("/api/documentos"), paciente, "RESULTADO_EXTERNO", "Resultado externo")
                        .file(new MockMultipartFile("archivo", "resultado.pdf", "application/pdf", "MZ...".getBytes()))
                        .header("Authorization", "Bearer " + archivo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.archivo").value(containsString("Formato no permitido")));

        long id = ((Number) JsonPath.read(cargar(archivo, paciente, "RESULTADO_EXTERNO", "Resonancia de rodilla externa", PDF_1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumen.versionActual").value(1))
                .andExpect(jsonPath("$.retencionHasta").value(LocalDate.now().plusYears(15).toString()))
                .andReturn().getResponse().getContentAsString(), "$.resumen.id")).longValue();

        // RFGAC-005: la nueva versión exige motivo y la original se conserva
        mvc.perform(multipart("/api/documentos/" + id + "/versiones")
                        .file(new MockMultipartFile("archivo", "rodilla-v2.pdf", "application/pdf", PDF_2))
                        .header("Authorization", "Bearer " + archivo))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/documentos/" + id + "/versiones")
                        .file(new MockMultipartFile("archivo", "rodilla-v2.pdf", "application/pdf", PDF_2))
                        .param("motivo", "El laboratorio externo corrigió el informe")
                        .header("Authorization", "Bearer " + archivo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versiones", hasSize(2)))
                .andExpect(jsonPath("$.versiones[0].motivo").value("El laboratorio externo corrigió el informe"));
        mvc.perform(get("/api/documentos/" + id + "/contenido").header("Authorization", "Bearer " + token("medico")))
                .andExpect(content().bytes(PDF_2));
        mvc.perform(get("/api/documentos/" + id + "/contenido").param("version", "1").header("Authorization", "Bearer " + archivo))
                .andExpect(content().contentType("application/pdf")).andExpect(content().bytes(PDF_1));

        // RNFGAC-006: ver y descargar queda registrado con el usuario
        mvc.perform(get("/api/documentos/" + id).header("Authorization", "Bearer " + archivo))
                .andExpect(jsonPath("$.eventos[*].accion").value(hasItem("DESCARGAR")))
                .andExpect(jsonPath("$.eventos[*].usuario").value(hasItem("medico")));
        mvc.perform(get("/api/documentos/" + id).header("Authorization", "Bearer " + admisiones)).andExpect(status().isForbidden());

        // RFGAC-003: búsqueda por documento del paciente y palabras clave; admisiones solo ve los administrativos
        mvc.perform(get("/api/documentos").param("documentoPaciente", doc).param("texto", "rodilla").header("Authorization", "Bearer " + archivo))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].pacienteDocumento").value("CC " + doc));
        mvc.perform(get("/api/documentos").param("documentoPaciente", doc).header("Authorization", "Bearer " + admisiones))
                .andExpect(jsonPath("$.content[*].tipo").value(not(hasItem("RESULTADO_EXTERNO"))))
                .andExpect(jsonPath("$.content[*].tipo").value(hasItem("CONSENTIMIENTO")));
    }

    @Test
    void cicloDeVidaRespetaLaRetencionYLaUbicacionFisica() throws Exception {
        String archivo = token("archivo");
        long paciente = paciente(documento());
        long reciente = idDe(cargar(archivo, paciente, "EPICRISIS", "Epicrisis 2026", PDF_1));
        enviar(post("/api/documentos/" + reciente + "/estado"), archivo, "{\"estado\":\"ELIMINADO\",\"motivo\":\"Depuración\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("RETENCION_VIGENTE"));
        enviar(post("/api/documentos/" + reciente + "/estado"), archivo, "{\"estado\":\"EN_CUSTODIA\",\"motivo\":\"Original firmado\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("SIN_UBICACION"));
        enviar(put("/api/documentos/" + reciente + "/ubicacion"), archivo, "{\"sala\":\"A\",\"estanteria\":\"3\",\"seccion\":\"B\",\"caja\":\"27\"}")
                .andExpect(jsonPath("$.resumen.ubicacion").value("Sala A, estantería 3, sección B, caja 27"));
        enviar(post("/api/documentos/" + reciente + "/estado"), archivo, "{\"estado\":\"EN_CUSTODIA\",\"motivo\":\"Original firmado\"}")
                .andExpect(jsonPath("$.resumen.estado").value("EN_CUSTODIA"));

        // Con la retención cumplida se elimina el contenido y se conservan metadatos y huella
        long antiguo = idDe(mvc.perform(conDatos(multipart("/api/documentos"), paciente, "EPICRISIS", "Epicrisis 2009")
                .file(new MockMultipartFile("archivo", "epicrisis.pdf", "application/pdf", PDF_1))
                .param("fechaDocumento", LocalDate.now().minusYears(16).toString())
                .header("Authorization", "Bearer " + archivo)));
        enviar(post("/api/documentos/" + antiguo + "/estado"), archivo, "{\"estado\":\"ELIMINADO\",\"motivo\":\"Retención de 15 años cumplida\"}")
                .andExpect(jsonPath("$.resumen.estado").value("ELIMINADO"))
                .andExpect(jsonPath("$.versiones[0].sha256").exists());
        mvc.perform(get("/api/documentos/" + antiguo + "/contenido").header("Authorization", "Bearer " + archivo))
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("DOCUMENTO_ELIMINADO"));
        enviar(post("/api/documentos/" + antiguo + "/estado"), archivo, "{\"estado\":\"ACTIVO\",\"motivo\":\"x\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        enviar(post("/api/documentos/" + reciente + "/estado"), token("medico"), "{\"estado\":\"ARCHIVADO\",\"motivo\":\"x\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void solicitudesAlArchivoYReporte() throws Exception {
        String archivo = token("archivo");
        String medico = token("medico");
        long paciente = paciente(documento());
        long documento = idDe(cargar(archivo, paciente, "HISTORIA_CLINICA", "Historia clínica física 2019", PDF_1));

        long solicitud = ((Number) JsonPath.read(enviar(post("/api/documentos/solicitudes"), medico,
                        "{\"pacienteId\":%d,\"documentoId\":%d,\"motivo\":\"Control postquirúrgico\",\"urgente\":true}".formatted(paciente, documento))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + archivo))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Solicitud urgente de archivo"))));
        mvc.perform(get("/api/documentos/solicitudes").header("Authorization", "Bearer " + archivo))
                .andExpect(jsonPath("$[?(@.id == " + solicitud + ")].documentoTitulo").value(hasItem("Historia clínica física 2019")));

        enviar(post("/api/documentos/solicitudes/" + solicitud + "/devolver"), archivo, "{}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("SOLICITUD_ESTADO_INVALIDO"));
        enviar(post("/api/documentos/solicitudes/" + solicitud + "/entregar"), archivo, "{\"respuesta\":\"Entregada en consultorio 4\"}")
                .andExpect(jsonPath("$.estado").value("ENTREGADA"));
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Documento disponible"))));
        mvc.perform(get("/api/documentos/solicitudes/mias").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$[0].estado").value("ENTREGADA"));
        enviar(post("/api/documentos/solicitudes/" + solicitud + "/devolver"), archivo, "{}")
                .andExpect(jsonPath("$.estado").value("DEVUELTA"));

        String hoy = LocalDate.now().toString();
        mvc.perform(get("/api/documentos/reportes").param("desde", hoy).param("hasta", hoy).header("Authorization", "Bearer " + token("directivo")))
                .andExpect(jsonPath("$.solicitudesPeriodo").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.porTipo[*].clave").value(hasItem("HISTORIA_CLINICA")))
                .andExpect(jsonPath("$.accionesPeriodo[*].clave").value(hasItem("SOLICITADO")));
    }

    // ---------- utilidades ----------

    private ResultActions cargar(String t, long paciente, String tipo, String titulo, byte[] pdf) throws Exception {
        return mvc.perform(conDatos(multipart("/api/documentos"), paciente, tipo, titulo)
                .file(new MockMultipartFile("archivo", titulo + ".pdf", "application/pdf", pdf))
                .header("Authorization", "Bearer " + t));
    }

    private static MockMultipartHttpServletRequestBuilder conDatos(MockMultipartHttpServletRequestBuilder b, long paciente,
                                                                   String tipo, String titulo) {
        b.param("pacienteId", String.valueOf(paciente)).param("tipo", tipo).param("titulo", titulo)
                .param("servicio", "Hospitalización").param("profesional", "Dr. Juan Pérez").param("soporte", "DIGITAL");
        if (!titulo.contains("2009")) b.param("fechaDocumento", LocalDate.now().toString());
        return b;
    }

    private static String documento() {
        return String.valueOf(DOCUMENTO.incrementAndGet());
    }

    private long paciente(String doc) throws Exception {
        return ((Number) JsonPath.read(enviar(post("/api/pacientes"), token("admisiones"), """
                {"tipoDocumento":"CC","numeroDocumento":"%s","nombres":"Doc","apellidos":"Prueba %s",
                 "fechaNacimiento":"1980-02-02","sexo":"FEMENINO","regimen":"PARTICULAR","confirmarNoDuplicado":true}"""
                .formatted(doc, doc)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static long idDe(ResultActions r) throws Exception {
        Number id = JsonPath.read(r.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.resumen.id");
        return id.longValue();
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String t, String json) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String token(String usuario) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"password\":\"ClaveTest123*\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }
}
