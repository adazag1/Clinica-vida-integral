package co.edu.ucentral.clinica.imagenes;

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

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Imágenes diagnósticas de punta a punta: orden, imágenes, informe firmado y versionado, doble lectura y bloqueo. */
@SpringBootTest(properties = {"app.seed.habilitado=true", "app.seed.password-prueba=ClaveTest123*"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ImagenesIntegrationTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    private static final AtomicInteger DOCUMENTO = new AtomicInteger(510_000_000);

    @Autowired
    MockMvc mvc;

    @Test
    void estudioUrgenteSeInformaConHallazgoCriticoYSeVersiona() throws Exception {
        String medico = token("medico");
        String rad = token("radiologia");
        long paciente = paciente();
        long estudio = ordenar(medico, paciente, "TC_CRANEO", "URGENTE");

        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + rad))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Estudio urgente"))));
        // RFRAD-004: el urgente encabeza la lista de trabajo
        mvc.perform(get("/api/imagenes/estudios").header("Authorization", "Bearer " + rad))
                .andExpect(jsonPath("$[0].prioridad").value("URGENTE"));

        // Sin imágenes no hay lectura; un archivo que no es imagen se rechaza por su contenido
        enviar(put("/api/imagenes/estudios/" + estudio + "/informe"), rad, "{\"hallazgos\":\"x\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("SIN_IMAGENES"));
        mvc.perform(multipart("/api/imagenes/estudios/" + estudio + "/imagenes")
                        .file(new MockMultipartFile("archivos", "virus.png", "image/png", "MZ ejecutable".getBytes()))
                        .header("Authorization", "Bearer " + rad))
                .andExpect(status().isBadRequest());
        String conImagen = mvc.perform(multipart("/api/imagenes/estudios/" + estudio + "/imagenes")
                        .file(new MockMultipartFile("archivos", "axial-1.png", "image/png", PNG))
                        .param("studyInstanceUid", "1.2.840.113619.2.55.3")
                        .header("Authorization", "Bearer " + rad))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.estado").value("ADQUIRIDO"))
                .andExpect(jsonPath("$.imagenes[0].visible").value(true))
                .andExpect(jsonPath("$.plantilla.hallazgos").value(containsString("Parénquima cerebral")))
                .andReturn().getResponse().getContentAsString();
        long imagen = ((Number) JsonPath.read(conImagen, "$.imagenes[0].id")).longValue();
        mvc.perform(get("/api/imagenes/estudios/" + estudio + "/imagenes/" + imagen).header("Authorization", "Bearer " + medico))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(PNG));
        enviar(put("/api/imagenes/estudios/" + estudio + "/imagenes/" + imagen), rad, "{\"clave\":true,\"descripcion\":\"Hematoma\"}")
                .andExpect(jsonPath("$.imagenes[0].clave").value(true));

        enviar(put("/api/imagenes/estudios/" + estudio + "/informe"), rad,
                "{\"hallazgos\":\"Colección hiperdensa extraaxial frontoparietal derecha.\",\"impresion\":\"Hematoma subdural agudo.\"}")
                .andExpect(jsonPath("$.resumen.estado").value("EN_LECTURA"))
                .andExpect(jsonPath("$.borrador.version").value(1));
        // RFRAD-008: un hallazgo crítico no se firma sin registrar a quién se comunicó
        enviar(post("/api/imagenes/estudios/" + estudio + "/firmar"), rad, "{\"password\":\"ClaveTest123*\",\"critico\":true}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.mensaje").value(containsString("hallazgo crítico")));
        enviar(post("/api/imagenes/estudios/" + estudio + "/firmar"), rad,
                "{\"password\":\"mala\",\"critico\":true,\"criticoReceptor\":\"Dr. Juan Pérez\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("FIRMA_INVALIDA"));
        enviar(post("/api/imagenes/estudios/" + estudio + "/firmar"), rad,
                "{\"password\":\"ClaveTest123*\",\"critico\":true,\"criticoReceptor\":\"Dr. Juan Pérez\",\"criticoMedio\":\"Llamada\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.estado").value("INFORMADO"))
                .andExpect(jsonPath("$.resumen.critico").value(true))
                .andExpect(jsonPath("$.criticoReceptor").value("Dr. Juan Pérez"))
                .andExpect(jsonPath("$.vigente.version").value(1))
                .andExpect(jsonPath("$.vigente.integro").value(true));

        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Hallazgo crítico"))));
        // RFGAC-009: el informe firmado queda en la gestión documental del paciente
        mvc.perform(get("/api/documentos/pacientes/" + paciente).header("Authorization", "Bearer " + medico))
                .andExpect(jsonPath("$[*].tipo").value(hasItem("INFORME_RADIOLOGIA")));

        // RNFRAD-002: la corrección es una versión nueva; la firmada no cambia
        enviar(put("/api/imagenes/estudios/" + estudio + "/informe"), rad, "{\"hallazgos\":\"cambio\",\"impresion\":\"cambio\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("ESTUDIO_INFORMADO"));
        enviar(post("/api/imagenes/estudios/" + estudio + "/enmendar"), rad, "{\"motivo\":\"Se precisa el espesor\"}")
                .andExpect(jsonPath("$.borrador.version").value(2))
                .andExpect(jsonPath("$.vigente.version").value(1));
        enviar(put("/api/imagenes/estudios/" + estudio + "/informe"), rad,
                "{\"hallazgos\":\"Colección hiperdensa extraaxial frontoparietal derecha de 12 mm.\",\"impresion\":\"Hematoma subdural agudo de 12 mm.\"}");
        enviar(post("/api/imagenes/estudios/" + estudio + "/firmar"), rad, "{\"password\":\"ClaveTest123*\"}")                .andExpect(jsonPath("$.vigente.version").value(2))
                .andExpect(jsonPath("$.vigente.motivoCambio").value("Se precisa el espesor"))
                .andExpect(jsonPath("$.versiones", hasSize(2)))
                .andExpect(jsonPath("$.versiones[1].impresion").value("Hematoma subdural agudo."));

        // RFRAD-012: aparece en las métricas del periodo
        String hoy = LocalDate.now().toString();
        mvc.perform(get("/api/imagenes/metricas").param("desde", hoy).param("hasta", hoy).header("Authorization", "Bearer " + rad))
                .andExpect(jsonPath("$[?(@.estudio == 'Tomografía de cráneo simple')].informados").value(hasItem(1)));
    }

    @Test
    void segundaLecturaYBloqueoPorIdentificacion() throws Exception {
        String medico = token("medico");
        String rad = token("radiologia");
        String rad2 = token("radiologia2");
        long estudio = ordenar(medico, paciente(), "RX_TORAX", "AMBULATORIO");
        mvc.perform(multipart("/api/imagenes/estudios/" + estudio + "/imagenes")
                .file(new MockMultipartFile("archivos", "pa.png", "image/png", PNG)).header("Authorization", "Bearer " + rad));

        enviar(post("/api/imagenes/estudios/" + estudio + "/segunda-opinion"), rad, "{\"asignadaA\":\"radiologia\",\"pregunta\":\"¿Nódulo?\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("OPINION_PROPIA"));
        String conOpinion = enviar(post("/api/imagenes/estudios/" + estudio + "/segunda-opinion"), rad,
                "{\"asignadaA\":\"radiologia2\",\"pregunta\":\"¿Nódulo en lóbulo superior derecho?\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long opinion = ((Number) JsonPath.read(conOpinion, "$.segundasOpiniones[0].id")).longValue();
        mvc.perform(get("/api/imagenes/estudios").param("vista", "MIOS").header("Authorization", "Bearer " + rad2))
                .andExpect(jsonPath("$[?(@.id == " + estudio + ")].segundaOpinionPendiente").value(hasItem(true)));
        enviar(post("/api/imagenes/estudios/" + estudio + "/segunda-opinion/" + opinion), rad2, "{\"concordancia\":\"DISCREPANCIA_MAYOR\"}")
                .andExpect(status().isBadRequest());
        enviar(post("/api/imagenes/estudios/" + estudio + "/segunda-opinion/" + opinion), rad2,
                "{\"concordancia\":\"DISCREPANCIA_MAYOR\",\"comentario\":\"Nódulo espiculado de 9 mm; sugiero TC.\"}")
                .andExpect(jsonPath("$.segundasOpiniones[0].concordancia").value("DISCREPANCIA_MAYOR"));
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + rad))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("Segunda lectura"))));

        // RFRAD-011: bloqueado no se informa; al desbloquear vuelve a su estado
        enviar(post("/api/imagenes/estudios/" + estudio + "/bloquear"), rad, "{\"motivo\":\"El nombre en la imagen no coincide\"}")
                .andExpect(jsonPath("$.resumen.estado").value("BLOQUEADO"));
        enviar(put("/api/imagenes/estudios/" + estudio + "/informe"), rad, "{\"hallazgos\":\"x\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.codigo").value("ESTUDIO_BLOQUEADO"));
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + token("admisiones")))
                .andExpect(jsonPath("$.notificaciones[*].titulo").value(hasItem(containsString("bloqueado"))));
        enviar(post("/api/imagenes/estudios/" + estudio + "/desbloquear"), rad, "{\"motivo\":\"Admisiones corrigió los datos del paciente\"}")
                .andExpect(jsonPath("$.resumen.estado").value("ADQUIRIDO"));
    }

    @Test
    void cadaRolHaceSoloLoSuyo() throws Exception {
        String medico = token("medico");
        long paciente = paciente();
        long estudio = ordenar(medico, paciente, "ECO_ABDOMEN", "HOSPITALIZADO");
        enviar(post("/api/imagenes/estudios"), token("radiologia"),
                "{\"pacienteId\":%d,\"tipoEstudioId\":1,\"prioridad\":\"URGENTE\",\"motivo\":\"x\"}".formatted(paciente))
                .andExpect(status().isForbidden());
        enviar(post("/api/imagenes/estudios/" + estudio + "/firmar"), medico, "{\"password\":\"ClaveTest123*\"}")
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/imagenes/estudios/" + estudio).header("Authorization", "Bearer " + token("laboratorio")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/imagenes/estudios/" + estudio).header("Authorization", "Bearer " + medico))
                .andExpect(status().isOk());
    }

    // ---------- utilidades ----------

    private long ordenar(String medico, long paciente, String codigo, String prioridad) throws Exception {
        String tipos = mvc.perform(get("/api/imagenes/tipos").header("Authorization", "Bearer " + medico))
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(tipos, "$[?(@.codigo == '" + codigo + "')].id");
        return id(enviar(post("/api/imagenes/estudios"), medico, """
                {"pacienteId":%d,"tipoEstudioId":%d,"prioridad":"%s","motivo":"Trauma craneoencefálico","diagnosticoPresuntivo":"S06.9"}"""
                .formatted(paciente, ids.getFirst().longValue(), prioridad)).andExpect(status().isCreated()), "$.resumen.id");
    }

    private long paciente() throws Exception {
        String doc = String.valueOf(DOCUMENTO.incrementAndGet());
        return id(enviar(post("/api/pacientes"), token("admisiones"), """
                {"tipoDocumento":"CC","numeroDocumento":"%s","nombres":"Img","apellidos":"Prueba %s",
                 "fechaNacimiento":"1970-01-15","sexo":"MASCULINO","regimen":"PARTICULAR","confirmarNoDuplicado":true}"""
                .formatted(doc, doc)).andExpect(status().isCreated()), "$.id");
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String t, String json) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long id(ResultActions r, String ruta) throws Exception {
        Number id = JsonPath.read(r.andReturn().getResponse().getContentAsString(), ruta);
        return id.longValue();
    }

    private String token(String usuario) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"password\":\"ClaveTest123*\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }
}
