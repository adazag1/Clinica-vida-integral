package co.edu.ucentral.clinica.ia;

import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.seguridad.Huella;
import co.edu.ucentral.clinica.facturacion.ServicioPrestado;
import co.edu.ucentral.clinica.historia.Consulta;
import co.edu.ucentral.clinica.historia.ConsultaRepository;
import co.edu.ucentral.clinica.historia.DatosPacienteActualizados;
import co.edu.ucentral.clinica.ia.dto.IaDtos.AlertaDto;
import co.edu.ucentral.clinica.ia.dto.IaDtos.ReglaDto;
import co.edu.ucentral.clinica.notificaciones.NotificacionService;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import co.edu.ucentral.clinica.seguridad.Rol;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * US 4.2: evalúa las reglas clínicas sobre el paciente y mantiene sus alertas. Se reevalúa cuando
 * llega información nueva (resultado validado, consulta firmada, signos, dosis administrada) y
 * cada vez que se abre la historia, porque algunas reglas dependen del tiempo transcurrido.
 */
@Service
public class AlertasIaService {

    private static final Logger log = LoggerFactory.getLogger(AlertasIaService.class);

    private final Map<String, ReglaClinica> reglas;
    private final ReglaIaRepository estados;
    private final AlertaIaRepository alertas;
    private final ConstructorContexto constructor;
    private final PacienteService pacientes;
    private final ConsultaRepository consultas;
    private final NotificacionService notificaciones;
    private final AuditorAware<String> auditor;
    private final EntityManager em;
    private final TransactionTemplate nuevaTransaccion;

    public AlertasIaService(List<ReglaClinica> reglas, ReglaIaRepository estados, AlertaIaRepository alertas,
                            ConstructorContexto constructor, PacienteService pacientes, ConsultaRepository consultas,
                            NotificacionService notificaciones, AuditorAware<String> auditor, EntityManager em,
                            PlatformTransactionManager tx) {
        this.reglas = reglas.stream().collect(Collectors.toMap(ReglaClinica::codigo, Function.identity(),
                (a, b) -> a, java.util.LinkedHashMap::new));
        this.estados = estados;
        this.alertas = alertas;
        this.constructor = constructor;
        this.pacientes = pacientes;
        this.consultas = consultas;
        this.notificaciones = notificaciones;
        this.auditor = auditor;
        this.em = em;
        this.nuevaTransaccion = new TransactionTemplate(tx);
        this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---------- Reevaluación automática ----------

    /** Después de que el acto clínico quedó guardado; si el motor falla, el acto no se afecta. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alPrestarseServicio(ServicioPrestado s) {
        reevaluarAparte(s.pacienteId(), s.modulo());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alActualizarseDatos(DatosPacienteActualizados e) {
        reevaluarAparte(e.pacienteId(), e.origen());
    }

    private void reevaluarAparte(Long pacienteId, String origen) {
        try {
            nuevaTransaccion.executeWithoutResult(t -> evaluar(pacienteId));
        } catch (RuntimeException ex) {
            log.error("No se pudieron reevaluar las alertas del paciente {} tras {}", pacienteId, origen, ex);
        }
    }

    // ---------- Evaluación ----------

    /**
     * Corre las reglas activas y concilia con las alertas vigentes: la misma regla con la misma
     * severidad y título se actualiza (conserva la opinión del médico); si cambió, se cierra la
     * anterior y se abre otra; si la regla dejó de disparar, se cierra.
     */
    @Transactional
    public List<AlertaIa> evaluar(Long pacienteId) {
        // Dos eventos simultáneos del mismo paciente no deben crear la misma alerta dos veces
        em.createNativeQuery("select pg_advisory_xact_lock(:clave)").setParameter("clave", 9_000_000_000L + pacienteId).getSingleResult();
        Paciente paciente = pacientes.buscarEntidad(pacienteId);
        ContextoClinico contexto = constructor.construir(paciente);
        Map<String, Boolean> activas = estados.findAll().stream().collect(Collectors.toMap(ReglaIa::getCodigo, ReglaIa::isActiva));
        Map<String, AlertaIa> vigentes = new HashMap<>();
        alertas.findByPacienteIdAndVigenteTrue(pacienteId).forEach(a -> vigentes.put(a.getRegla(), a));

        for (ReglaClinica regla : reglas.values()) {
            AlertaIa actual = vigentes.get(regla.codigo());
            Optional<Hallazgo> hallazgo = Boolean.FALSE.equals(activas.get(regla.codigo())) ? Optional.empty() : evaluarSeguro(regla, contexto);
            if (hallazgo.isEmpty()) {
                if (actual != null) actual.cerrar();
                continue;
            }
            Hallazgo h = hallazgo.get();
            String huella = Huella.de(regla.codigo(), h.severidad(), h.titulo());
            if (actual != null && actual.getHuella().equals(huella)) {
                actual.actualizar(h);
                continue;
            }
            if (actual != null) actual.cerrar();
            if (!activas.containsKey(regla.codigo())) estados.save(new ReglaIa(regla.codigo()));
            AlertaIa nueva = alertas.save(new AlertaIa(pacienteId, regla.codigo(), h, huella));
            if (h.severidad() == Hallazgo.Severidad.CRITICA) avisar(paciente, nueva);
        }
        return alertas.findByPacienteIdAndVigenteTrue(pacienteId);
    }

    /** Una regla con un error no debe impedir que las demás se evalúen. */
    private Optional<Hallazgo> evaluarSeguro(ReglaClinica regla, ContextoClinico contexto) {
        try {
            return regla.evaluar(contexto);
        } catch (RuntimeException e) {
            log.error("La regla {} falló con el paciente {}", regla.codigo(), contexto.pacienteId(), e);
            return Optional.empty();
        }
    }

    /** Al médico con consulta abierta; si no hay, a los médicos de turno. */
    private void avisar(Paciente paciente, AlertaIa a) {
        String mensaje = paciente.getNombreCompleto() + ": " + a.getTitulo() + ". " + a.getDetalle();
        List<Consulta> abiertas = consultas.findByPacienteIdOrderByInicioDesc(paciente.getId()).stream()
                .filter(c -> c.getEstado() == Consulta.Estado.EN_CURSO).toList();
        if (abiertas.isEmpty()) {
            notificaciones.paraRol(Rol.MEDICO, "Alerta clínica crítica", mensaje, "/historia/" + paciente.getId());
        } else {
            abiertas.forEach(c -> notificaciones.paraUsuario(c.getMedico(), "Alerta clínica crítica", mensaje, "/consulta/" + c.getId()));
        }
    }

    // ---------- Consulta y retroalimentación ----------

    /** Reevalúa (las reglas dependen del tiempo) y devuelve las vigentes primero, las críticas arriba. */
    @Transactional
    public List<AlertaDto> alertas(Long pacienteId, boolean incluirCerradas) {
        evaluar(pacienteId);
        return alertas.findByPacienteIdOrderByGeneradaEnDesc(pacienteId).stream()
                .filter(a -> incluirCerradas || a.isVigente())
                .sorted(Comparator.comparing(AlertaIa::isVigente).reversed()
                        .thenComparing(a -> a.getSeveridad().ordinal(), Comparator.reverseOrder()))
                .limit(incluirCerradas ? 50 : 20)
                .map(a -> AlertaDto.de(a, reglas.get(a.getRegla()))).toList();
    }

    @Transactional
    public AlertaDto retroalimentar(Long alertaId, AlertaIa.Retroalimentacion valor, String comentario) {
        AlertaIa a = alertas.findById(alertaId).orElseThrow(() -> new RecursoNoEncontradoException("Alerta", alertaId));
        a.retroalimentar(valor, comentario, auditor.getCurrentAuditor().orElse("sistema"));
        return AlertaDto.de(a, reglas.get(a.getRegla()));
    }

    // ---------- Reglas y precisión ----------

    @Transactional(readOnly = true)
    public List<ReglaDto> reglas() {
        Map<String, ReglaIa> estado = estados.findAll().stream().collect(Collectors.toMap(ReglaIa::getCodigo, Function.identity()));
        Map<String, long[]> conteos = new HashMap<>();
        for (Object[] fila : alertas.precisionPorRegla()) {
            conteos.put((String) fila[0], new long[]{num(fila[1]), num(fila[2]), num(fila[3])});
        }
        return reglas.values().stream().map(r -> {
            ReglaIa e = estado.get(r.codigo());
            long[] c = conteos.getOrDefault(r.codigo(), new long[3]);
            BigDecimal precision = c[1] + c[2] == 0 ? null
                    : BigDecimal.valueOf(c[1] * 100.0 / (c[1] + c[2])).setScale(1, RoundingMode.HALF_UP);
            List<String> comentarios = alertas.findTop20ByReglaAndRetroalimentacionOrderByRetroEnDesc(r.codigo(), AlertaIa.Retroalimentacion.NO_UTIL)
                    .stream().map(AlertaIa::getComentario).filter(x -> x != null && !x.isBlank()).limit(5).toList();
            return new ReglaDto(r.codigo(), r.nombre(), r.descripcion(), r.referencia(), e == null || e.isActiva(),
                    e == null ? null : e.getModificadoPor(), e == null ? null : e.getModificadoEn(), c[0], c[1], c[2], precision, comentarios);
        }).toList();
    }

    @Transactional
    public ReglaDto cambiarRegla(String codigo, boolean activa) {
        if (!reglas.containsKey(codigo)) throw new RecursoNoEncontradoException("Regla", codigo);
        ReglaIa e = estados.findById(codigo).orElseGet(() -> estados.save(new ReglaIa(codigo)));
        e.cambiarActiva(activa, auditor.getCurrentAuditor().orElse("sistema"));
        return reglas().stream().filter(r -> r.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    public int totalReglas() {
        return reglas.size();
    }

    ReglaClinica regla(String codigo) {
        return reglas.get(codigo);
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
