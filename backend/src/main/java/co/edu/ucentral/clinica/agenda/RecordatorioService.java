package co.edu.ucentral.clinica.agenda;

import co.edu.ucentral.clinica.agenda.dto.CitaDto;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

/**
 * RFAGE-009. Cada 10 minutos envía el recordatorio a las citas que empiezan en las próximas
 * {@code app.recordatorios.horas-antes} horas. Con {@code spring.mail.host} configurado llega por
 * correo; sin proveedor queda registrado como SIMULADO (con el texto que se habría enviado), para
 * no afirmar que el paciente recibió algo que no recibió. El SMS requiere contratar un proveedor.
 */
@Service
public class RecordatorioService {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioService.class);

    private final CitaRepository citas;
    private final RecordatorioRepository recordatorios;
    private final CitaService citaService;
    private final ObjectProvider<JavaMailSender> correo;
    private final String remitente;
    private final Duration anticipacion;

    public RecordatorioService(CitaRepository citas, RecordatorioRepository recordatorios, CitaService citaService,
                               ObjectProvider<JavaMailSender> correo,
                               @Value("${spring.mail.username:no-responder@clinicavidaintegral.co}") String remitente,
                               @Value("${app.recordatorios.horas-antes}") int horasAntes) {
        this.citas = citas;
        this.recordatorios = recordatorios;
        this.citaService = citaService;
        this.correo = correo;
        this.remitente = remitente;
        this.anticipacion = Duration.ofHours(horasAntes);
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    @Transactional
    public void enviarPendientes() {
        Instant ahora = Instant.now();
        for (Cita cita : citas.sinRecordatorio(ahora, ahora.plus(anticipacion),
                EnumSet.of(EstadoCita.PROGRAMADA, EstadoCita.CONFIRMADA))) {
            try {
                enviar(cita);
            } catch (RuntimeException e) {
                log.error("No se pudo enviar el recordatorio de la cita {}", cita.getId(), e);
            }
        }
    }

    /** Envío manual desde el panel de la cita (botón "Enviar recordatorio" del mockup). */
    @Transactional
    public CitaDto enviarManual(Long citaId) {
        Cita cita = citaService.buscar(citaId);
        if (!cita.getEstado().isPendiente() || cita.getInicio().isBefore(Instant.now())) {
            throw new ReglaNegocioException("CITA_NO_MODIFICABLE",
                    "Solo se envían recordatorios de citas futuras programadas o confirmadas.");
        }
        enviar(cita);
        return citaService.detalle(cita);
    }

    private void enviar(Cita cita) {
        String texto = mensaje(cita);
        String email = cita.getPaciente().getEmail();
        String telefono = cita.getPaciente().getTelefono();
        JavaMailSender mail = correo.getIfAvailable();
        Recordatorio r;
        if (email != null && mail != null) {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(remitente);
            m.setTo(email);
            m.setSubject("Recordatorio de su cita - Clínica Vida Integral");
            m.setText(texto);
            try {
                mail.send(m);
                r = new Recordatorio(cita.getId(), "EMAIL", email, "ENVIADO", null);
            } catch (RuntimeException e) {
                r = new Recordatorio(cita.getId(), "EMAIL", email, "ERROR", recortar(e.getMessage()));
            }
        } else if (email != null || telefono != null) {
            r = new Recordatorio(cita.getId(), email != null ? "EMAIL" : "SMS", email != null ? email : telefono,
                    "SIMULADO", recortar("Sin proveedor configurado. Mensaje: " + texto));
        } else {
            r = new Recordatorio(cita.getId(), "NINGUNO", null, "SIN_DATOS",
                    "El paciente no tiene correo ni teléfono registrados.");
        }
        recordatorios.save(r);
        cita.registrarRecordatorio();
        citaService.registrar(cita, EventoCita.Accion.RECORDATORIO, r.getCanal() + " · " + r.getResultado());
    }

    private String mensaje(Cita cita) {
        Profesional p = cita.getProfesional();
        StringBuilder sb = new StringBuilder()
                .append("Hola ").append(cita.getPaciente().getNombres().split(" ")[0]).append(", le recordamos su cita de ")
                .append(cita.getTipo().getNombre().toLowerCase()).append(" el ").append(citaService.fecha(cita.getInicio()))
                .append(" con ").append(p.getNombre()).append(" (").append(p.getEspecialidad().getNombre()).append("), ")
                .append(p.getSede().getNombre());
        if (p.getSede().getDireccion() != null) {
            sb.append(", ").append(p.getSede().getDireccion());
        }
        if (p.getConsultorio() != null) {
            sb.append(", ").append(p.getConsultorio());
        }
        sb.append(".");
        if (cita.getTipo().getInstruccionesPreparacion() != null) {
            sb.append(" Preparación: ").append(cita.getTipo().getInstruccionesPreparacion());
        }
        sb.append(" Confirme o cancele aquí: ").append(citaService.enlacePaciente(cita));
        return sb.toString();
    }

    private static String recortar(String s) {
        return s == null ? null : s.length() > 500 ? s.substring(0, 497) + "..." : s;
    }
}
