package co.edu.ucentral.clinica.historia;

import co.edu.ucentral.clinica.admisiones.Episodio;
import co.edu.ucentral.clinica.admisiones.EpisodioRepository;
import co.edu.ucentral.clinica.admisiones.EstadoEpisodio;
import co.edu.ucentral.clinica.admisiones.NivelTriage;
import co.edu.ucentral.clinica.admisiones.Triage;
import co.edu.ucentral.clinica.admisiones.TriageRepository;
import co.edu.ucentral.clinica.admisiones.dto.SignosVitales;
import co.edu.ucentral.clinica.comun.excepciones.DatosInvalidosException;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.AntecedenteDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.ConsultaHistorialDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.DiagnosticoActivoDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.DiagnosticoDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.MedicamentoActualDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.MedicamentoDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.ResumenClinicoDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.SignosDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaDtos.TriageActualDto;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.AntecedenteRequest;
import co.edu.ucentral.clinica.historia.dto.HistoriaSolicitudes.SignosRequest;
import co.edu.ucentral.clinica.laboratorio.LaboratorioService;
import co.edu.ucentral.clinica.laboratorio.dto.LabDtos.ResultadoRecienteDto;
import co.edu.ucentral.clinica.notificaciones.NotificacionService;
import co.edu.ucentral.clinica.pacientes.Normalizador;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;
import co.edu.ucentral.clinica.seguridad.Rol;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Historia clínica del paciente: resumen consolidado, antecedentes, signos vitales e historial. */
@Service
public class HistoriaService {

    private static final int MAX_BUSQUEDA = 15;
    /** Diagnósticos de consultas recientes que se muestran como activos en el resumen. */
    private static final Duration VENTANA_DIAGNOSTICOS = Duration.ofDays(90);

    private final PacienteService pacienteService;
    private final AntecedenteRepository antecedentes;
    private final RegistroSignosRepository signos;
    private final ConsultaRepository consultas;
    private final DiagnosticoRepository diagnosticos;
    private final MedicamentoRepository medicamentos;
    private final EpisodioRepository episodios;
    private final TriageRepository triages;
    private final EvaluadorSignos evaluador;
    private final NotificacionService notificaciones;
    private final LaboratorioService laboratorio;
    private final AuditorAware<String> auditor;

    public HistoriaService(PacienteService pacienteService, AntecedenteRepository antecedentes,
                           RegistroSignosRepository signos, ConsultaRepository consultas,
                           DiagnosticoRepository diagnosticos, MedicamentoRepository medicamentos,
                           EpisodioRepository episodios, TriageRepository triages, EvaluadorSignos evaluador,
                           NotificacionService notificaciones, LaboratorioService laboratorio,
                           AuditorAware<String> auditor) {
        this.laboratorio = laboratorio;
        this.pacienteService = pacienteService;
        this.antecedentes = antecedentes;
        this.signos = signos;
        this.consultas = consultas;
        this.diagnosticos = diagnosticos;
        this.medicamentos = medicamentos;
        this.episodios = episodios;
        this.triages = triages;
        this.evaluador = evaluador;
        this.notificaciones = notificaciones;
        this.auditor = auditor;
    }

    // ---------- Catálogos (búsqueda sin tildes; son pocos registros y se filtran en memoria) ----------

    @Transactional(readOnly = true)
    public List<DiagnosticoDto> buscarDiagnosticos(String texto) {
        String t = Normalizador.texto(texto);
        return diagnosticos.findAllByOrderByCodigo().stream()
                .filter(Diagnostico::isActivo)
                .filter(d -> t.isEmpty() || Normalizador.texto(d.getCodigo() + " " + d.getNombre()).contains(t)
                        || d.getCodigo().toLowerCase().startsWith(t.replace(" ", ".")))
                .limit(MAX_BUSQUEDA).map(DiagnosticoDto::de).toList();
    }

    @Transactional(readOnly = true)
    public List<MedicamentoDto> buscarMedicamentos(String texto) {
        String t = Normalizador.texto(texto);
        return medicamentos.findAllByOrderByNombre().stream()
                .filter(m -> t.isEmpty() || Normalizador.texto(m.getNombre() + " " + m.getPrincipioActivo() + " "
                        + m.getFamilia() + " " + (m.getSinonimos() == null ? "" : m.getSinonimos())).contains(t))
                .limit(MAX_BUSQUEDA).map(MedicamentoDto::de).toList();
    }

    // ---------- Antecedentes ----------

    @Transactional(readOnly = true)
    public List<AntecedenteDto> antecedentes(Long pacienteId) {
        pacienteService.buscarEntidad(pacienteId);
        return antecedentes.findByPacienteIdOrderByTipoAscCreadoEnDesc(pacienteId).stream()
                .map(AntecedenteDto::de).toList();
    }

    @Transactional
    public AntecedenteDto agregarAntecedente(Long pacienteId, AntecedenteRequest r) {
        pacienteService.buscarEntidad(pacienteId);
        if (r.tipo() == Antecedente.Tipo.ALERGIA && r.severidad() == null) {
            throw new DatosInvalidosException(Map.of("severidad", "Indique la severidad de la alergia"));
        }
        return AntecedenteDto.de(antecedentes.save(new Antecedente(pacienteId, r.tipo(), r.descripcion().trim(),
                r.detalle() == null || r.detalle().isBlank() ? null : r.detalle().trim(), r.severidad())));
    }

    @Transactional
    public AntecedenteDto inactivarAntecedente(Long id, String motivo) {
        Antecedente a = antecedentes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Antecedente", id));
        a.inactivar(motivo.trim());
        return AntecedenteDto.de(a);
    }

    // ---------- Signos vitales (RFMT-010) ----------

    /**
     * Registra una toma de signos y devuelve sus alertas. Si hay un valor crítico y quien lo registra
     * no es el médico de la consulta abierta, se le avisa de inmediato.
     */
    @Transactional
    public SignosDto registrarSignos(Long pacienteId, SignosRequest r) {
        Paciente paciente = pacienteService.buscarEntidad(pacienteId);
        if (Stream.of(r.presionSistolica(), r.presionDiastolica(), r.frecuenciaCardiaca(), r.frecuenciaRespiratoria(),
                r.temperatura(), r.saturacionOxigeno(), r.peso(), r.talla()).allMatch(v -> v == null)) {
            throw new DatosInvalidosException(Map.of("signos", "Ingrese al menos un signo vital"));
        }
        if (r.presionSistolica() != null && r.presionDiastolica() != null && r.presionDiastolica() >= r.presionSistolica()) {
            throw new DatosInvalidosException(Map.of("presionDiastolica", "Debe ser menor que la sistólica"));
        }
        Consulta consulta = null;
        if (r.consultaId() != null) {
            consulta = consultas.findById(r.consultaId())
                    .filter(c -> c.getPacienteId().equals(pacienteId))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Consulta", r.consultaId()));
        }
        String usuario = auditor.getCurrentAuditor().orElse("sistema");
        RegistroSignos s = signos.save(new RegistroSignos(pacienteId, r.consultaId(), r.presionSistolica(),
                r.presionDiastolica(), r.frecuenciaCardiaca(), r.frecuenciaRespiratoria(), r.temperatura(),
                r.saturacionOxigeno(), r.peso(), r.talla(), usuario));
        List<EvaluadorSignos.Alerta> alertas = evaluador.evaluar(s);

        if (EvaluadorSignos.hayCritica(alertas)) {
            String detalle = paciente.getNombreCompleto() + ": " + alertas.stream()
                    .filter(a -> a.nivel() == EvaluadorSignos.Nivel.CRITICO)
                    .map(EvaluadorSignos.Alerta::mensaje).reduce((a, b) -> a + "; " + b).orElse("");
            List<Consulta> abiertas = consultas.findByPacienteIdOrderByInicioDesc(pacienteId).stream()
                    .filter(c -> c.getEstado() == Consulta.Estado.EN_CURSO).toList();
            abiertas.stream().filter(c -> !c.getMedico().equalsIgnoreCase(usuario))
                    .forEach(c -> notificaciones.paraUsuario(c.getMedico(), "Signos vitales críticos", detalle,
                            "/consulta/" + c.getId()));
            if (abiertas.isEmpty()) {
                // Sin consulta abierta (p. ej. paciente en observación): avisa a los médicos de turno
                notificaciones.paraRol(Rol.MEDICO, "Signos vitales críticos", detalle, "/enfermeria/pacientes/" + pacienteId);
            }
        }
        return SignosDto.de(s, alertas);
    }

    @Transactional(readOnly = true)
    public List<SignosDto> historialSignos(Long pacienteId) {
        pacienteService.buscarEntidad(pacienteId);
        return signos.findTop20ByPacienteIdOrderByFechaDesc(pacienteId).stream()
                .map(s -> SignosDto.de(s, evaluador.evaluar(s))).toList();
    }

    // ---------- Resumen consolidado (RFMT-002) ----------

    @Transactional(readOnly = true)
    public ResumenClinicoDto resumen(Long pacienteId) {
        Paciente paciente = pacienteService.buscarEntidad(pacienteId);
        List<Antecedente> activos = antecedentes.findByPacienteIdAndActivoTrue(pacienteId);
        Map<Antecedente.Tipo, List<AntecedenteDto>> porTipo = new LinkedHashMap<>();
        activos.forEach(a -> porTipo.computeIfAbsent(a.getTipo(), k -> new ArrayList<>()).add(AntecedenteDto.de(a)));
        List<AntecedenteDto> alergias = porTipo.getOrDefault(Antecedente.Tipo.ALERGIA, List.of()).stream()
                .sorted(Comparator.comparing((AntecedenteDto a) -> !"SEVERA".equals(a.severidad())))
                .toList();

        // Medicamentos: los registrados como antecedente y los formulados en consultas con tratamiento vigente
        Instant ahora = Instant.now();
        List<MedicamentoActualDto> meds = new ArrayList<>();
        porTipo.getOrDefault(Antecedente.Tipo.MEDICAMENTO_ACTUAL, List.of())
                .forEach(a -> meds.add(new MedicamentoActualDto(a.descripcion(), a.detalle(), "Antecedente", a.registradoEn())));
        List<Consulta> recientes = consultas.firmadasDesde(pacienteId, ahora.minus(VENTANA_DIAGNOSTICOS));
        for (Consulta c : recientes) {
            for (Prescripcion p : c.getPrescripciones()) {
                if (c.getFirmadaEn().plus(Duration.ofDays(p.getDuracionDias())).isAfter(ahora)) {
                    meds.add(new MedicamentoActualDto(p.getMedicamento().getNombre(),
                            p.getDosis() + " · " + p.getFrecuencia() + " · " + p.getDuracionDias() + " días",
                            "Fórmula de " + c.getMedicoNombre(), c.getFirmadaEn()));
                }
            }
        }

        List<DiagnosticoActivoDto> dx = new ArrayList<>();
        porTipo.getOrDefault(Antecedente.Tipo.PATOLOGICO, List.of())
                .forEach(a -> dx.add(new DiagnosticoActivoDto(null, a.descripcion(), "Crónico", a.registradoEn())));
        recientes.forEach(c -> c.getDiagnosticos().stream().filter(ConsultaDiagnostico::isPrincipal).forEach(d ->
                dx.add(new DiagnosticoActivoDto(d.getDiagnostico().getCodigo(), d.getDiagnostico().getNombre(),
                        "Consulta con " + c.getMedicoNombre(), c.getFirmadaEn()))));

        List<AntecedenteDto> otros = new ArrayList<>();
        porTipo.forEach((tipo, lista) -> {
            if (tipo == Antecedente.Tipo.FAMILIAR || tipo == Antecedente.Tipo.OTRO) {
                otros.addAll(lista);
            }
        });

        Optional<Triage> triage = triageActual(pacienteId);
        SignosDto ultimos = ultimosSignos(pacienteId, triage);

        List<String> alertas = new ArrayList<>();
        alergias.stream().filter(a -> "SEVERA".equals(a.severidad()))
                .forEach(a -> alertas.add("Alergia severa: " + a.descripcion()));
        if (ultimos != null) {
            ultimos.alertas().stream().filter(a -> a.nivel() == EvaluadorSignos.Nivel.CRITICO)
                    .forEach(a -> alertas.add(a.mensaje()));
        }
        triage.filter(t -> t.getNivelAsignado() <= 2)
                .ifPresent(t -> alertas.add(NivelTriage.descripcion(t.getNivelAsignado())));

        // Resultados críticos de la última semana (RFMT-004): el médico no debe buscarlos
        List<ResultadoRecienteDto> resultados = laboratorio.resultadosRecientes(pacienteId);
        resultados.stream()
                .filter(r -> r.interpretacion().startsWith("CRITICO") && r.fecha().isAfter(ahora.minus(Duration.ofDays(7))))
                .forEach(r -> alertas.add("Resultado crítico: " + r.analito() + " " + r.valor().stripTrailingZeros().toPlainString()
                        .replace('.', ',') + " " + r.unidad()));

        return new ResumenClinicoDto(PacienteResumenDto.de(paciente), alergias, meds, dx,
                porTipo.getOrDefault(Antecedente.Tipo.QUIRURGICO, List.of()), otros, ultimos,
                triage.map(t -> new TriageActualDto(t.getNivelAsignado(), NivelTriage.descripcion(t.getNivelAsignado()),
                        t.getSintomaPrincipal(), t.getModificadoEn())).orElse(null),
                alertas, resultados);
    }

    /** Triage del episodio abierto del paciente, si lo tiene. */
    public Optional<Triage> triageActual(Long pacienteId) {
        return episodios.abiertosDePaciente(pacienteId,
                        EnumSet.of(EstadoEpisodio.EN_ADMISION, EstadoEpisodio.EN_ESPERA_CONSULTA, EstadoEpisodio.EN_ATENCION))
                .stream().map(Episodio::getId).findFirst().flatMap(triages::findByEpisodioId);
    }

    /** La toma más reciente entre la de triage y las registradas en consulta o enfermería. */
    public SignosDto ultimosSignos(Long pacienteId, Optional<Triage> triage) {
        Optional<RegistroSignos> registro = signos.findFirstByPacienteIdOrderByFechaDesc(pacienteId);
        if (triage.isPresent() && (registro.isEmpty() || triage.get().getModificadoEn().isAfter(registro.get().getFecha()))) {
            Triage t = triage.get();
            SignosVitales v = t.getSignosVitales();
            RegistroSignos temporal = new RegistroSignos(pacienteId, null, v.presionSistolica(), v.presionDiastolica(),
                    v.frecuenciaCardiaca(), v.frecuenciaRespiratoria(), v.temperatura(), v.saturacionOxigeno(), null, null,
                    t.getModificadoPor());
            return new SignosDto(null, "TRIAGE", v.presionSistolica(), v.presionDiastolica(), v.frecuenciaCardiaca(),
                    v.frecuenciaRespiratoria(), v.temperatura(), v.saturacionOxigeno(), null, null, t.getModificadoPor(),
                    t.getModificadoEn(), evaluador.evaluar(temporal));
        }
        return registro.map(s -> SignosDto.de(s, evaluador.evaluar(s))).orElse(null);
    }

    // ---------- Historial (RFMT-009) ----------

    @Transactional(readOnly = true)
    public List<ConsultaHistorialDto> historial(Long pacienteId) {
        pacienteService.buscarEntidad(pacienteId);
        return consultas.findByPacienteIdOrderByInicioDesc(pacienteId).stream().map(ConsultaHistorialDto::de).toList();
    }
}
