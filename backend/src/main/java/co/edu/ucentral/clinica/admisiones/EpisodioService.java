package co.edu.ucentral.clinica.admisiones;

import co.edu.ucentral.clinica.admisiones.dto.CoberturaRequest;
import co.edu.ucentral.clinica.admisiones.dto.ConsentimientoDto;
import co.edu.ucentral.clinica.admisiones.dto.ConsentimientoRequest;
import co.edu.ucentral.clinica.admisiones.dto.EpisodioDto;
import co.edu.ucentral.clinica.admisiones.dto.EpisodioRequest;
import co.edu.ucentral.clinica.admisiones.dto.ListaEsperaDto;
import co.edu.ucentral.clinica.admisiones.dto.Pendiente;
import co.edu.ucentral.clinica.admisiones.dto.SignosVitales;
import co.edu.ucentral.clinica.admisiones.dto.SugerenciaTriage;
import co.edu.ucentral.clinica.admisiones.dto.TriageDto;
import co.edu.ucentral.clinica.admisiones.dto.TriageRequest;
import co.edu.ucentral.clinica.comun.excepciones.DatosInvalidosException;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Flujo de admisión (RFADM-004 a RFADM-013). Todo cambio de un episodio ocurre en una sola
 * transacción junto con sus datos relacionados (RNFADM-001 Integridad transaccional).
 */
@Service
public class EpisodioService {

    private static final Set<EstadoEpisodio> ABIERTOS =
            EnumSet.of(EstadoEpisodio.EN_ADMISION, EstadoEpisodio.EN_ESPERA_CONSULTA, EstadoEpisodio.EN_ATENCION);

    private final EpisodioRepository episodios;
    private final TriageRepository triages;
    private final ConsentimientoRepository consentimientos;
    private final PacienteService pacienteService;
    private final CalculadoraTriage calculadora;
    private final AuditorAware<String> auditor;

    public EpisodioService(EpisodioRepository episodios, TriageRepository triages,
                           ConsentimientoRepository consentimientos, PacienteService pacienteService,
                           CalculadoraTriage calculadora, AuditorAware<String> auditor) {
        this.episodios = episodios;
        this.triages = triages;
        this.consentimientos = consentimientos;
        this.pacienteService = pacienteService;
        this.calculadora = calculadora;
        this.auditor = auditor;
    }

    /** RFADM-004: genera el número de atención y activa el flujo según el tipo de atención. */
    @Transactional
    public EpisodioDto crear(EpisodioRequest request) {
        Paciente paciente = pacienteService.buscarEntidad(request.pacienteId());
        if (paciente.isIdentificacionTemporal() && request.tipoAtencion() != TipoAtencion.URGENCIAS) {
            throw new ReglaNegocioException("IDENTIFICACION_REQUERIDA",
                    "Un paciente sin documento solo puede ingresar por urgencias. Registre primero su documento.");
        }
        if (episodios.existsByPacienteIdAndEstadoIn(paciente.getId(), ABIERTOS)) {
            throw new ReglaNegocioException("EPISODIO_ABIERTO",
                    "El paciente ya tiene una atención en curso. Continúe con esa atención o anúlela.");
        }
        String numero = "AT-%d-%06d".formatted(Year.now().getValue(), episodios.siguienteNumero());
        Episodio episodio = episodios.save(
                new Episodio(numero, paciente, request.tipoAtencion(), request.motivoConsulta().trim()));
        return armar(episodio);
    }

    @Transactional(readOnly = true)
    public EpisodioDto obtener(Long id) {
        return armar(buscar(id));
    }

    @Transactional(readOnly = true)
    public List<EpisodioDto> dePaciente(Long pacienteId) {
        pacienteService.buscarEntidad(pacienteId);
        return armarVarios(episodios.findByPacienteIdOrderByFechaIngresoDesc(pacienteId));
    }

    @Transactional
    public EpisodioDto registrarCobertura(Long id, CoberturaRequest r) {
        Episodio episodio = buscar(id);
        if (r.resultado() == EstadoCobertura.VERIFICADA && (r.aseguradora() == null || r.aseguradora().isBlank())) {
            throw new DatosInvalidosException(Map.of("aseguradora", "Ingrese la aseguradora que autorizó"));
        }
        episodio.registrarCobertura(r.resultado(), limpio(r.aseguradora()), limpio(r.plan()),
                limpio(r.numeroAutorizacion()), limpio(r.observacion()), usuarioActual());
        return armar(episodio);
    }

    @Transactional
    public EpisodioDto registrarExcepcionUrgencia(Long id, String justificacion) {
        Episodio episodio = buscar(id);
        episodio.registrarExcepcionUrgencia(justificacion.trim(), usuarioActual());
        return armar(episodio);
    }

    @Transactional
    public EpisodioDto registrarConsentimiento(Long id, ConsentimientoRequest r) {
        Episodio episodio = buscar(id);
        episodio.exigirEnAdmision();
        if (consentimientos.existsByEpisodioIdAndTipo(id, r.tipo())) {
            throw new ReglaNegocioException("CONSENTIMIENTO_REGISTRADO",
                    "Ya se registró el " + r.tipo().getDescripcion().toLowerCase() + " para esta atención.");
        }
        consentimientos.save(new Consentimiento(id, r.tipo(), r.otorgadoPor().trim(), limpio(r.parentesco()),
                r.aceptado(), limpio(r.observaciones())));
        return armar(episodio);
    }

    public SugerenciaTriage sugerirTriage(SignosVitales signos, Integer edad) {
        return calculadora.sugerir(signos, edad);
    }

    /**
     * RFADM-005 / RFADM-006: guarda los signos, calcula el nivel sugerido y exige justificar
     * si quien clasifica asigna otro nivel. Se puede reclasificar mientras siga en admisión.
     */
    @Transactional
    public EpisodioDto clasificarTriage(Long id, TriageRequest r) {
        Episodio episodio = buscar(id);
        episodio.exigirEnAdmision();
        if (episodio.getTipoAtencion() != TipoAtencion.URGENCIAS) {
            throw new ReglaNegocioException("TRIAGE_SOLO_URGENCIAS", "El triage solo aplica a urgencias.");
        }
        SugerenciaTriage sugerencia = calculadora.sugerir(r.signos(), episodio.getPaciente().getEdad());
        boolean ajustado = r.nivelAsignado() != sugerencia.nivel();
        if (ajustado && (r.justificacionAjuste() == null || r.justificacionAjuste().isBlank())) {
            throw new DatosInvalidosException(Map.of("justificacionAjuste",
                    "Justifique por qué asigna un nivel distinto al sugerido (" + sugerencia.descripcion() + ")"));
        }
        Triage triage = triages.findByEpisodioId(id).orElseGet(() -> new Triage(id));
        triage.clasificar(r.signos(), r.sintomaPrincipal().trim(), sugerencia.nivel(), r.nivelAsignado(),
                limpio(r.justificacionAjuste()));
        triages.save(triage);
        return armar(episodio);
    }

    /**
     * Si falta información crítica el paciente no puede avanzar a consulta (entrevista a admisiones:
     * "hoy el sistema permite continuar con datos incompletos"). Al quedar listo aparece en la lista
     * del médico (RFADM-012 / RFADM-013).
     */
    @Transactional
    public EpisodioDto enviarAConsulta(Long id) {
        Episodio episodio = buscar(id);
        episodio.exigirEnAdmision();
        EpisodioDto actual = armar(episodio);
        if (!actual.pendientes().isEmpty()) {
            throw new ReglaNegocioException("EPISODIO_INCOMPLETO", "No puede pasar a consulta. Falta: "
                    + actual.pendientes().stream().map(Pendiente::mensaje).collect(Collectors.joining("; ")) + ".");
        }
        episodio.marcarListoParaConsulta();
        return armar(episodio);
    }

    @Transactional
    public EpisodioDto anular(Long id, String motivo) {
        Episodio episodio = buscar(id);
        episodio.anular(motivo.trim());
        return armar(episodio);
    }

    /** RFADM-013. El frontend la consulta periódicamente para mantenerla al día. */
    @Transactional(readOnly = true)
    public ListaEsperaDto listaEspera() {
        List<EpisodioDto> activos = armarVarios(
                episodios.buscarPorEstados(EnumSet.of(EstadoEpisodio.EN_ADMISION, EstadoEpisodio.EN_ESPERA_CONSULTA)));

        List<EpisodioDto> pendientesTriage = new ArrayList<>();
        List<EpisodioDto> enAdmision = new ArrayList<>();
        List<EpisodioDto> esperaConsulta = new ArrayList<>();
        for (EpisodioDto e : activos) {
            if (EstadoEpisodio.EN_ESPERA_CONSULTA.name().equals(e.estado())) {
                esperaConsulta.add(e);
            } else if (TipoAtencion.URGENCIAS.name().equals(e.tipoAtencion()) && e.triage() == null) {
                pendientesTriage.add(e);
            } else {
                enAdmision.add(e);
            }
        }
        Comparator<EpisodioDto> porGravedad = Comparator.comparingInt(
                (EpisodioDto e) -> e.triage() == null ? 6 : e.triage().nivelAsignado());
        esperaConsulta.sort(porGravedad.thenComparing(EpisodioDto::fechaListo));
        enAdmision.sort(porGravedad.thenComparing(EpisodioDto::fechaIngreso));
        return new ListaEsperaDto(pendientesTriage, enAdmision, esperaConsulta);
    }

    /** Requisitos mínimos para pasar a consulta, según la entrevista a admisiones. */
    List<Pendiente> pendientes(Episodio e, Optional<Triage> triage, List<Consentimiento> consentimientosEpisodio) {
        List<Pendiente> pendientes = new ArrayList<>();
        boolean urgencias = e.getTipoAtencion() == TipoAtencion.URGENCIAS;

        if (urgencias && triage.isEmpty()) {
            pendientes.add(new Pendiente("TRIAGE", "clasificar el triage"));
        }
        switch (e.getEstadoCobertura()) {
            case PENDIENTE -> pendientes.add(new Pendiente("COBERTURA", urgencias
                    ? "verificar la cobertura o registrar la atención sin autorización"
                    : "verificar la cobertura con la aseguradora"));
            case NO_CUBIERTA -> pendientes.add(new Pendiente("COBERTURA",
                    "la aseguradora no cubre el servicio: registre pago particular o una nueva verificación"));
            default -> {
            }
        }
        // En triage I la vida del paciente no puede esperar la firma: el consentimiento se exige después.
        boolean emergenciaVital = triage.map(t -> t.getNivelAsignado() == 1).orElse(false);
        Optional<Consentimiento> informado = consentimientosEpisodio.stream()
                .filter(c -> c.getTipo() == TipoConsentimiento.CONSENTIMIENTO_INFORMADO)
                .findFirst();
        if (informado.isEmpty() && !emergenciaVital) {
            pendientes.add(new Pendiente("CONSENTIMIENTO", "registrar el consentimiento informado"));
        } else if (informado.isPresent() && !informado.get().isAceptado()) {
            pendientes.add(new Pendiente("CONSENTIMIENTO_RECHAZADO",
                    "el consentimiento informado fue rechazado: la atención no puede continuar sin él"));
        }
        return pendientes;
    }

    private EpisodioDto armar(Episodio e) {
        Optional<Triage> triage = triages.findByEpisodioId(e.getId());
        List<Consentimiento> lista = consentimientos.findByEpisodioIdOrderByCreadoEn(e.getId());
        return EpisodioDto.de(e, triage.map(TriageDto::de).orElse(null),
                lista.stream().map(ConsentimientoDto::de).toList(), pendientes(e, triage, lista));
    }

    /** Igual que armar() pero con dos consultas para toda la lista en vez de dos por episodio. */
    private List<EpisodioDto> armarVarios(List<Episodio> lista) {
        List<Long> ids = lista.stream().map(Episodio::getId).toList();
        Map<Long, Triage> triagePorEpisodio = triages.findByEpisodioIdIn(ids).stream()
                .collect(Collectors.toMap(Triage::getEpisodioId, Function.identity()));
        Map<Long, List<Consentimiento>> consentimientosPorEpisodio = consentimientos.findByEpisodioIdIn(ids).stream()
                .collect(Collectors.groupingBy(Consentimiento::getEpisodioId));
        return lista.stream().map(e -> {
            Optional<Triage> triage = Optional.ofNullable(triagePorEpisodio.get(e.getId()));
            List<Consentimiento> cs = consentimientosPorEpisodio.getOrDefault(e.getId(), List.of());
            return EpisodioDto.de(e, triage.map(TriageDto::de).orElse(null),
                    cs.stream().map(ConsentimientoDto::de).toList(), pendientes(e, triage, cs));
        }).toList();
    }

    private Episodio buscar(Long id) {
        return episodios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Episodio", id));
    }

    private String usuarioActual() {
        return auditor.getCurrentAuditor().orElse("sistema");
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
