package co.edu.ucentral.clinica.ia;

import co.edu.ucentral.clinica.enfermeria.OrdenClinica;
import co.edu.ucentral.clinica.enfermeria.OrdenClinicaRepository;
import co.edu.ucentral.clinica.historia.Consulta;
import co.edu.ucentral.clinica.historia.ConsultaRepository;
import co.edu.ucentral.clinica.historia.Medicamento;
import co.edu.ucentral.clinica.historia.Prescripcion;
import co.edu.ucentral.clinica.historia.RegistroSignosRepository;
import co.edu.ucentral.clinica.imagenes.EstudioImagenRepository;
import co.edu.ucentral.clinica.laboratorio.ResultadoLab;
import co.edu.ucentral.clinica.laboratorio.ResultadoLabRepository;
import co.edu.ucentral.clinica.pacientes.Normalizador;
import co.edu.ucentral.clinica.pacientes.Paciente;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Reúne lo que el motor necesita del paciente leyendo los módulos clínicos. Es lo único del motor
 * que toca la base de datos: las reglas trabajan sobre el {@link ContextoClinico} resultante.
 */
@Component
public class ConstructorContexto {

    private static final int MAX_RESULTADOS = 300;
    /** Ventana de diagnósticos y texto clínico que se considera "actual". */
    private static final Duration VENTANA_DIAGNOSTICOS = Duration.ofDays(30);
    private static final Duration VENTANA_TEXTO = Duration.ofHours(72);
    private static final Duration VENTANA_CRITICOS = Duration.ofDays(7);

    private final RegistroSignosRepository signos;
    private final ResultadoLabRepository resultados;
    private final ConsultaRepository consultas;
    private final OrdenClinicaRepository ordenes;
    private final EstudioImagenRepository estudios;

    public ConstructorContexto(RegistroSignosRepository signos, ResultadoLabRepository resultados, ConsultaRepository consultas,
                               OrdenClinicaRepository ordenes, EstudioImagenRepository estudios) {
        this.signos = signos;
        this.resultados = resultados;
        this.consultas = consultas;
        this.ordenes = ordenes;
        this.estudios = estudios;
    }

    /** Texto clínico reciente tal como lo escribió el médico (para el asistente, que lo anonimiza antes). */
    public record TextoClinico(Instant fecha, String estado, String texto) {
    }

    public ContextoClinico construir(Paciente paciente) {
        Instant ahora = Instant.now();
        Long id = paciente.getId();

        List<ContextoClinico.Signo> listaSignos = signos.findTop20ByPacienteIdOrderByFechaDesc(id).stream()
                .map(s -> new ContextoClinico.Signo(s.getFecha(), s.getPresionSistolica(), s.getPresionDiastolica(),
                        s.getFrecuenciaCardiaca(), s.getFrecuenciaRespiratoria(), s.getTemperatura(), s.getSaturacionOxigeno()))
                .toList();

        List<ResultadoLab> validados = resultados.validadosDePaciente(id, PageRequest.of(0, MAX_RESULTADOS));
        Map<String, List<ContextoClinico.Resultado>> porAnalito = new LinkedHashMap<>();
        List<ContextoClinico.Critico> criticos = new ArrayList<>();
        for (ResultadoLab r : validados) {
            Instant fecha = r.getOrden().getValidadaEn();
            porAnalito.computeIfAbsent(r.getAnalito().getCodigo(), k -> new ArrayList<>())
                    .add(new ContextoClinico.Resultado(fecha, r.getValor(), r.getRefMin(), r.getRefMax(),
                            r.getInterpretacion().name(), r.getAnalito().getUnidad()));
            if (r.getInterpretacion().isCritico() && fecha.isAfter(ahora.minus(VENTANA_CRITICOS))) {
                criticos.add(new ContextoClinico.Critico("Laboratorio " + r.getOrden().getNumero(),
                        r.getAnalito().getNombre() + " " + r.getValor().stripTrailingZeros().toPlainString().replace('.', ',')
                                + " " + r.getAnalito().getUnidad() + " (" + r.getInterpretacion().getDescripcion().toLowerCase() + ")",
                        fecha));
            }
        }
        estudios.findByPacienteIdOrderByOrdenadoEnDesc(id).stream()
                .filter(e -> e.isCritico() && e.getCriticoNotificadoEn() != null && e.getCriticoNotificadoEn().isAfter(ahora.minus(VENTANA_CRITICOS)))
                .forEach(e -> criticos.add(new ContextoClinico.Critico("Imágenes " + e.getNumero(),
                        e.getTipoEstudio().getNombre() + ": hallazgo crítico en el informe", e.getCriticoNotificadoEn())));

        List<Consulta> historial = consultas.findByPacienteIdOrderByInicioDesc(id);
        List<ContextoClinico.Medicamento> medicamentos = new ArrayList<>();
        historial.stream()
                .filter(c -> c.getEstado() == Consulta.Estado.EN_CURSO || c.getFirmadaEn().isAfter(ahora.minus(Duration.ofDays(365))))
                .flatMap(c -> c.getPrescripciones().stream().filter(p -> vigente(c, p, ahora)))
                .forEach(p -> medicamentos.add(medicamento(p.getMedicamento())));
        ordenes.recientesDePaciente(id, ahora).stream()
                .filter(o -> o.getTipo() == OrdenClinica.Tipo.MEDICAMENTO && o.isVigente(ahora) && o.getMedicamento() != null)
                .forEach(o -> medicamentos.add(medicamento(o.getMedicamento())));

        List<String> diagnosticos = historial.stream()
                .filter(c -> c.getInicio().isAfter(ahora.minus(VENTANA_DIAGNOSTICOS)))
                .flatMap(c -> c.getDiagnosticos().stream()).map(d -> d.getDiagnostico().getCodigo()).distinct().toList();

        String texto = textoReciente(historial, ahora).stream().map(t -> Normalizador.texto(t.texto()))
                .reduce("", (a, b) -> a + " " + b);

        Instant ultimaFirma = historial.stream().map(Consulta::getFirmadaEn).filter(Objects::nonNull)
                .max(Instant::compareTo).orElse(null);

        return new ContextoClinico(id, paciente.getEdad(), paciente.getSexo() == null ? null : paciente.getSexo().name(), ahora,
                listaSignos, porAnalito, medicamentos, diagnosticos, texto, criticos, ultimaFirma);
    }

    /** Motivo, enfermedad actual, examen y plan de las consultas de las últimas 72 horas. */
    public List<TextoClinico> textoReciente(List<Consulta> historial, Instant ahora) {
        return historial.stream().filter(c -> c.getInicio().isAfter(ahora.minus(VENTANA_TEXTO)))
                .map(c -> new TextoClinico(c.getInicio(), c.getEstado().name(), String.join("\n", Stream.of(
                                etiqueta("Motivo", c.getMotivoConsulta()), etiqueta("Enfermedad actual", c.getEnfermedadActual()),
                                etiqueta("Examen físico", c.getExamenFisico()), etiqueta("Plan", c.getPlan()))
                        .filter(Objects::nonNull).toList())))
                .toList();
    }

    public List<TextoClinico> textoReciente(Long pacienteId) {
        return textoReciente(consultas.findByPacienteIdOrderByInicioDesc(pacienteId), Instant.now());
    }

    private static String etiqueta(String nombre, String valor) {
        return valor == null || valor.isBlank() ? null : nombre + ": " + valor.trim();
    }

    /** En curso: el médico la está formulando ahora. Firmada: vigente durante los días indicados. */
    private static boolean vigente(Consulta c, Prescripcion p, Instant ahora) {
        return c.getEstado() == Consulta.Estado.EN_CURSO || c.getFirmadaEn().plus(Duration.ofDays(p.getDuracionDias())).isAfter(ahora);
    }

    private static ContextoClinico.Medicamento medicamento(Medicamento m) {
        return new ContextoClinico.Medicamento(m.getNombre(), Normalizador.texto(m.getPrincipioActivo()), Normalizador.texto(m.getFamilia()));
    }
}
