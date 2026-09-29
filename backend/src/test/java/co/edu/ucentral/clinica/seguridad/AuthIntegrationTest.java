package co.edu.ucentral.clinica.seguridad;

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.seed.habilitado=true", "app.seed.password-prueba=ClaveTest123*"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Test
    void loginExitosoDevuelveTokenYPermiteConsultarMe() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"medico\",\"password\":\"ClaveTest123*\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("MEDICO"))
                .andReturn();

        Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(res.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + m.group(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("medico"));
    }

    @Test
    void loginConCorreoTambienFunciona() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"laboratorio@clinicavidaintegral.test\",\"password\":\"ClaveTest123*\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("LABORATORIO"));
    }

    @Test
    void cuentaSeBloqueaTrasCincoIntentosFallidos() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"usuario\":\"archivo\",\"password\":\"incorrecta\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        }
        assertThat(usuarios.findByUsernameIgnoreCase("archivo").orElseThrow().isBloqueado()).isTrue();

        // Ni con la contraseña correcta entra una cuenta bloqueada
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"archivo\",\"password\":\"ClaveTest123*\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinTokenNoSePuedeAccederALaApi() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void medicoNoPuedeVerAuditoria() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"medico\",\"password\":\"ClaveTest123*\"}"))
                .andReturn();
        Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(res.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();

        mvc.perform(get("/api/auditoria").header("Authorization", "Bearer " + m.group(1)))
                .andExpect(status().isForbidden());
    }
}
