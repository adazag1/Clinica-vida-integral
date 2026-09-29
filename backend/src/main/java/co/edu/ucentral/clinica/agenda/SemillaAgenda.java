package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.seguridad.Usuario;
import co.edu.ucentral.clinica.seguridad.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;

/**
 * Profesionales de prueba con horario (como en el mockup de agendamiento). Solo con
 * app.seed.habilitado=true y si aún no hay profesionales. Nombres ficticios.
 * "Dr. Juan Pérez" queda enlazado al usuario "medico" para probar "Mi agenda".
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.seed.habilitado", havingValue = "true")
public class SemillaAgenda implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SemillaAgenda.class);
    private static final EnumSet<DayOfWeek> ENTRE_SEMANA = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    private final ProfesionalRepository profesionales;
    private final HorarioRepository horarios;
    private final EspecialidadRepository especialidades;
    private final SedeRepository sedes;
    private final UsuarioRepository usuarios;

    public SemillaAgenda(ProfesionalRepository profesionales, HorarioRepository horarios,
                         EspecialidadRepository especialidades, SedeRepository sedes, UsuarioRepository usuarios) {
        this.profesionales = profesionales;
        this.horarios = horarios;
        this.especialidades = especialidades;
        this.sedes = sedes;
        this.usuarios = usuarios;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (profesionales.count() > 0) {
            return;
        }
        Long usuarioMedico = usuarios.findByUsernameIgnoreCase("medico").map(Usuario::getId).orElse(null);
        Profesional perez = crear("Dr. Juan Pérez", "Medicina Interna", "Sede Principal", "Consultorio 101", usuarioMedico);
        Profesional ramirez = crear("Dra. Laura Ramírez", "Pediatría", "Sede Principal", "Consultorio 102", null);
        Profesional rojas = crear("Dr. Felipe Rojas", "Cardiología", "Sede Principal", "Consultorio 103", null);
        Profesional ortiz = crear("Dra. Natalia Ortiz", "Ginecología", "Sede Norte", "Consultorio 204", null);

        for (Profesional p : new Profesional[]{perez, ramirez, rojas, ortiz}) {
            for (DayOfWeek dia : ENTRE_SEMANA) {
                franja(p, dia, 8, 12, HorarioProfesional.Tipo.NORMAL);
                franja(p, dia, 14, 16, HorarioProfesional.Tipo.NORMAL);
                // RFAGE-006: la última hora de la tarde queda para prioridades y urgencias
                franja(p, dia, 16, 17, HorarioProfesional.Tipo.RESERVADA_PRIORIDAD);
            }
        }
        franja(perez, DayOfWeek.SATURDAY, 8, 12, HorarioProfesional.Tipo.NORMAL);
        log.info("Profesionales de prueba con horario creados: 4");
    }

    private Profesional crear(String nombre, String especialidad, String sede, String consultorio, Long usuarioId) {
        return profesionales.save(new Profesional(nombre,
                especialidades.findByNombre(especialidad).orElseThrow(),
                sedes.findByNombre(sede).orElseThrow(), consultorio, usuarioId));
    }

    private void franja(Profesional p, DayOfWeek dia, int desde, int hasta, HorarioProfesional.Tipo tipo) {
        horarios.save(new HorarioProfesional(p.getId(), dia, LocalTime.of(desde, 0), LocalTime.of(hasta, 0), tipo));
    }
}
