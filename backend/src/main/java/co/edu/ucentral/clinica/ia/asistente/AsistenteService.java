package co.edu.ucentral.clinica.ia.asistente;

import co.edu.ucentral.clinica.comun.auditoria.AuditoriaService;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.ia.AlertasIaService;
import co.edu.ucentral.clinica.ia.AnonimizadorClinico;
import co.edu.ucentral.clinica.ia.ConstructorContexto;
import co.edu.ucentral.clinica.ia.ConsultaIa;
import co.edu.ucentral.clinica.ia.ConsultaIaRepository;
import co.edu.ucentral.clinica.ia.ContextoClinico;
import co.edu.ucentral.clinica.ia.dto.IaDtos.EstadoIaDto;
import co.edu.ucentral.clinica.ia.dto.IaDtos.RespuestaAsistenteDto;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Asistente de lenguaje sobre la historia clínica (resumen y diagnóstico diferencial). Envía solo
 * texto anonimizado, registra cada uso con tokens y costo, y nunca escribe en la historia: el
 * médico decide qué usar. Si está apagado responde 422 IA_DESHABILITADA.
 */
@Service
public class AsistenteService {

    public enum Tipo {
        RESUMEN("Resumen clínico", """
                Eres un asistente para médicos de una clínica en Colombia. Con los datos del paciente, escribe en español
                un resumen clínico de máximo 150 palabras: problema principal, hallazgos relevantes (signos, laboratorio)
                y tratamiento actual. Usa solo los datos entregados; si falta información importante, dilo. No des
                órdenes ni dosis nuevas. Los marcadores entre corchetes reemplazan datos personales: no intentes deducirlos."""),
        DIFERENCIAL("Diagnóstico diferencial", """
                Eres un asistente para médicos de una clínica en Colombia. Con los datos del paciente, propone en español
                hasta 5 diagnósticos diferenciales ordenados por probabilidad. Para cada uno: una línea de justificación
                basada solo en los datos entregados y qué examen o dato ayudaría a confirmarlo o descartarlo. Señala
                primero cualquier condición que amenace la vida. Termina recordando que es apoyo y que la decisión es del
                médico tratante. Los marcadores entre corchetes reemplazan datos personales: no intentes deducirlos.""");

        private final String descripcion;
        private final String instrucciones;

        Tipo(String descripcion, String instrucciones) {
            this.descripcion = descripcion;
            this.instrucciones = instrucciones;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    private static final String ADVERTENCIA =
            "Texto generado por un modelo de lenguaje a partir de datos anonimizados. Puede contener errores: verifíquelo antes de usarlo.";

    private final AsistenteLenguaje asistente;
    private final ConstructorContexto constructor;
    private final PacienteService pacientes;
    private final ConsultaIaRepository consultas;
    private final AlertasIaService alertas;
    private final AuditoriaService auditoria;
    private final AuditorAware<String> auditor;
    private final TransactionTemplate nuevaTransaccion;
    private final BigDecimal precioEntrada;
    private final BigDecimal precioSalida;

    public AsistenteService(AsistenteLenguaje asistente, ConstructorContexto constructor, PacienteService pacientes,
                            ConsultaIaRepository consultas, AlertasIaService alertas, AuditoriaService auditoria,
                            AuditorAware<String> auditor, PlatformTransactionManager tx,
                            @Value("${app.ia.llm.precio-entrada-1k:0}") BigDecimal precioEntrada,
                            @Value("${app.ia.llm.precio-salida-1k:0}") BigDecimal precioSalida) {
        this.asistente = asistente;
        this.constructor = constructor;
        this.pacientes = pacientes;
        this.consultas = consultas;
        this.alertas = alertas;
        this.auditoria = auditoria;
        this.auditor = auditor;
        this.nuevaTransaccion = new TransactionTemplate(tx);
        this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.precioEntrada = precioEntrada;
        this.precioSalida = precioSalida;
    }

    @Transactional(readOnly = true)
    public EstadoIaDto estado() {
        Instant hace30 = Instant.now().minus(Duration.ofDays(30));
        String motivo = asistente.disponible() ? null
                : !asistente.habilitado() ? "El asistente de lenguaje está apagado. Lo enciende el administrador cuando la clínica apruebe "
                + "el costo y el tratamiento de datos en Azure OpenAI (IA_LLM_HABILITADO=true)."
                : "El asistente está encendido pero falta configurar el endpoint, el despliegue o la llave de Azure OpenAI.";
        return new EstadoIaDto(true, alertas.totalReglas(), asistente.habilitado(), asistente.configurado(), asistente.modelo(), motivo,
                consultas.countByFechaAfter(hace30), consultas.costoDesde(hace30));
    }

    /** Exactamente lo que se enviaría al modelo, para que el médico o el oficial de datos lo revisen. */
    @Transactional(readOnly = true)
    public String vistaPrevia(Long pacienteId) {
        Paciente p = pacientes.buscarEntidad(pacienteId);
        ContextoClinico c = constructor.construir(p);
        return AnonimizadorClinico.resumen(c, constructor.textoReciente(pacienteId), p);
    }

    public RespuestaAsistenteDto consultar(Long pacienteId, Tipo tipo, String ip) {
        if (!asistente.disponible()) {
            throw new ReglaNegocioException("IA_DESHABILITADA", estado().motivo());
        }
        String usuario = auditor.getCurrentAuditor().orElse("sistema");
        String contenido = vistaPrevia(pacienteId);
        try {
            AsistenteLenguaje.Respuesta r = asistente.completar(tipo.instrucciones, contenido);
            BigDecimal costo = costo(r.tokensEntrada(), r.tokensSalida());
            registrar(new ConsultaIa(pacienteId, tipo.name(), usuario, r.modelo(), r.tokensEntrada(), r.tokensSalida(), costo, true, null));
            auditoria.registrarEvento(usuario, "IA_ASISTENTE", tipo.name() + " paciente " + pacienteId + " (" + r.modelo() + ")", ip);
            return new RespuestaAsistenteDto(tipo.name(), r.texto(), r.modelo(), r.tokensEntrada(), r.tokensSalida(), costo, ADVERTENCIA, contenido);
        } catch (RuntimeException e) {
            registrar(new ConsultaIa(pacienteId, tipo.name(), usuario, asistente.modelo(), null, null, null, false, e.getMessage()));
            throw e;
        }
    }

    private void registrar(ConsultaIa c) {
        nuevaTransaccion.executeWithoutResult(t -> consultas.save(c));
    }

    private BigDecimal costo(Integer entrada, Integer salida) {
        if (entrada == null && salida == null) return null;
        BigDecimal e = precioEntrada.multiply(BigDecimal.valueOf(entrada == null ? 0 : entrada));
        BigDecimal s = precioSalida.multiply(BigDecimal.valueOf(salida == null ? 0 : salida));
        return e.add(s).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }
}
