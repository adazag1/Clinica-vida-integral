package co.edu.ucentral.clinica.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Crea un usuario de prueba por rol (usuario = nombre del rol en minúscula, p. ej. "medico").
 * Solo se activa con app.seed.habilitado=true. Nombres ficticios, sin datos reales.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.seed.habilitado", havingValue = "true")
public class SemillaUsuarios implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SemillaUsuarios.class);

    private static final Map<Rol, String[]> USUARIOS = Map.ofEntries(
            Map.entry(Rol.ADMIN, new String[]{"Administrador General", "Administrador del sistema"}),
            Map.entry(Rol.MEDICO, new String[]{"Dr. Juan Pérez", "Médico General"}),
            Map.entry(Rol.ENFERMERIA, new String[]{"Laura Gómez", "Jefe de Enfermería"}),
            Map.entry(Rol.LABORATORIO, new String[]{"Carlos Ruiz", "Bacteriólogo Validador"}),
            Map.entry(Rol.RADIOLOGIA, new String[]{"Dra. Ana Torres", "Médica Radióloga"}),
            Map.entry(Rol.PATOLOGIA, new String[]{"Dr. Andrés Morales", "Médico Patólogo"}),
            Map.entry(Rol.ADMISIONES, new String[]{"Sofía Herrera", "Auxiliar de Admisiones"}),
            Map.entry(Rol.AGENDAMIENTO, new String[]{"María Gómez", "Coordinadora de Agendamiento"}),
            Map.entry(Rol.FACTURACION, new String[]{"Julián Bautista", "Analista de Facturación"}),
            Map.entry(Rol.AUDITORIA, new String[]{"Paula Castro", "Auditora Médica"}),
            Map.entry(Rol.ARCHIVO, new String[]{"Ricardo Peña", "Gestor Documental"}),
            Map.entry(Rol.DIRECTIVO, new String[]{"Elena Vargas", "Directora Ejecutiva"}),
            Map.entry(Rol.PACIENTE, new String[]{"Carlos Martínez", "Paciente"}));

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String passwordPrueba;

    public SemillaUsuarios(UsuarioRepository usuarios, PasswordEncoder encoder,
                           @Value("${app.seed.password-prueba}") String passwordPrueba) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.passwordPrueba = passwordPrueba;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (passwordPrueba == null || passwordPrueba.isBlank()) {
            log.warn("app.seed.password-prueba vacío: no se crean usuarios de prueba");
            return;
        }
        String hash = encoder.encode(passwordPrueba);
        USUARIOS.forEach((rol, datos) -> {
            String username = rol.name().toLowerCase();
            if (!usuarios.existsByUsernameIgnoreCase(username)) {
                usuarios.save(new Usuario(username, username + "@clinicavidaintegral.test", hash,
                        datos[0], rol, datos[1]));
                log.info("Usuario de prueba creado: {}", username);
            }
        });
    }
}
