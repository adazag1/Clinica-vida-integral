package co.edu.ucentral.clinica.facturacion;

import co.edu.ucentral.clinica.admisiones.Episodio;
import co.edu.ucentral.clinica.admisiones.EpisodioRepository;
import co.edu.ucentral.clinica.admisiones.EstadoCobertura;
import co.edu.ucentral.clinica.admisiones.TipoAtencion;
import co.edu.ucentral.clinica.agenda.CalculadoraDisponibilidad;
import co.edu.ucentral.clinica.catalogos.CatalogoService;
import co.edu.ucentral.clinica.comun.excepciones.DatosInvalidosException;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.AseguradoraCarteraDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.CargoDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.CarteraDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.ContratoDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.CuentaDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.CuentaResumenDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.EventoDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.FacturaCarteraDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.FacturaDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.GlosaDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.LiquidacionDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.PagoDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.RangoCarteraDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.ServicioDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.CargoManualRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.ContratoRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.GlosaRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.PagoRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.PaqueteRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.ReglaRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.ServicioRequest;
import co.edu.ucentral.clinica.historia.ConsultaRepository;
import co.edu.ucentral.clinica.notificaciones.NotificacionService;
import co.edu.ucentral.clinica.pacientes.Normalizador;
import co.edu.ucentral.clinica.pacientes.Paciente;
import co.edu.ucentral.clinica.pacientes.PacienteService;
import co.edu.ucentral.clinica.pacientes.Regimen;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;
import co.edu.ucentral.clinica.seguridad.Rol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AuditorAware;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Ciclo de ingresos: cargos, tarifas del contrato, factura, glosas, pagos y cartera (RFRC-002 a RFRC-014). */
@Service
public class FacturacionService {

    private static final Logger log = LoggerFactory.getLogger(FacturacionService.class);
    private static final String PARTICULAR = "Particular";

    private final ServicioFacturableRepository servicios;
    private final ContratoRepository contratos;
    private final CuentaRepository cuentas;
    private final CargoRepository cargos;
    private final EventoCuentaRepository eventos;
    private final FacturaRepository facturas;
    private final GlosaRepository glosas;
    private final PagoRepository pagos;
    private final AlertaCarteraRepository alertas;
    private final EpisodioRepository episodios;
    private final ConsultaRepository consultas;
    private final PacienteService pacienteService;
    private final CatalogoService catalogos;
    private final GeneradorFacturaElectronica generador;
    private final ProveedorFacturaElectronica proveedor;
    private final NotificacionService notificaciones;
    private final ApplicationEventPublisher publicador;
    private final CalculadoraDisponibilidad calendario;
    private final AuditorAware<String> auditor;
    private final TransactionTemplate nuevaTransaccion;
    private final String prefijo;
    private final int diasRespuestaGlosa;
    private final List<Integer> umbralesCartera;

    public FacturacionService(ServicioFacturableRepository servicios, ContratoRepository contratos, CuentaRepository cuentas,
                              CargoRepository cargos, EventoCuentaRepository eventos, FacturaRepository facturas,
                              GlosaRepository glosas, PagoRepository pagos, AlertaCarteraRepository alertas,
                              EpisodioRepository episodios, ConsultaRepository consultas, PacienteService pacienteService,
                              CatalogoService catalogos, GeneradorFacturaElectronica generador,
                              ProveedorFacturaElectronica proveedor, NotificacionService notificaciones,
                              ApplicationEventPublisher publicador, CalculadoraDisponibilidad calendario,
                              AuditorAware<String> auditor, PlatformTransactionManager tx,
                              @Value("${app.facturacion.prefijo}") String prefijo,
                              @Value("${app.facturacion.dias-respuesta-glosa}") int diasRespuestaGlosa,
                              @Value("${app.cartera.umbrales-dias}") String umbrales) {
        this.servicios = servicios;
        this.contratos = contratos;
        this.cuentas = cuentas;
        this.cargos = cargos;
        this.eventos = eventos;
        this.facturas = facturas;
        this.glosas = glosas;
        this.pagos = pagos;
        this.alertas = alertas;
        this.episodios = episodios;
        this.consultas = consultas;
        this.pacienteService = pacienteService;
        this.catalogos = catalogos;
        this.generador = generador;
        this.proveedor = proveedor;
        this.notificaciones = notificaciones;
        this.publicador = publicador;
        this.calendario = calendario;
        this.auditor = auditor;
        this.nuevaTransaccion = new TransactionTemplate(tx);
        this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.prefijo = prefijo;
        this.diasRespuestaGlosa = diasRespuestaGlosa;
        this.umbralesCartera = Arrays.stream(umbrales.split(",")).map(String::trim).map(Integer::valueOf).sorted().toList();
    }

    // ---------- Cargos automáticos (RFRC-002 / RFRC-013) ----------

    /**
     * Se ejecuta después de que el acto clínico quedó guardado, en su propia transacción. Si algo
     * falla se avisa a facturación, pero el acto clínico nunca se deshace por un problema de cobro.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alPrestarseServicio(ServicioPrestado s) {
        try {
            nuevaTransaccion.executeWithoutResult(t -> registrarAutomatico(s));
        } catch (RuntimeException e) {
            log.error("No se pudo generar el cargo de {} {}", s.modulo(), s.referencia(), e);
            nuevaTransaccion.executeWithoutResult(t -> notificaciones.paraRol(Rol.FACTURACION, "No se generó un cargo automático",
                    s.claveOrigen() + " (" + s.modulo() + " " + s.referencia() + "): regístrelo manualmente.", "/facturacion"));
        }
    }

    void registrarAutomatico(ServicioPrestado s) {
        if (cargos.existsByOrigenModuloAndReferenciaOrigenAndEstado(s.modulo(), s.referencia(), Cargo.Estado.ACTIVO)) {
            return; // RFRC-011: el mismo acto ya tiene su cargo
        }
        Optional<ServicioFacturable> servicio = servicios.findByClaveOrigen(s.claveOrigen()).filter(ServicioFacturable::isActivo);
        if (servicio.isEmpty()) {
            notificaciones.paraRol(Rol.FACTURACION, "Servicio sin tarifa", "No hay un servicio facturable activo para "
                    + s.claveOrigen() + ": el cargo no se generó.", "/facturacion/servicios");
            return;
        }
        Long episodioId = s.episodioId() != null ? s.episodioId()
                : s.consultaId() == null ? null : consultas.findById(s.consultaId()).map(c -> c.getEpisodioId()).orElse(null);
        Cuenta cuenta = cuentaAbierta(s.pacienteId(), episodioId);
        Cargo c = new Cargo(cuenta, servicio.get(), s.cantidad(), tarifa(cuenta.getContrato(), servicio.get()), s.soporte(),
                s.enlace(), s.modulo(), s.referencia(), s.fecha(), usuario());
        cuenta.getCargos().add(c);
        cuentas.flush();
        evento(cuenta, "CARGO", servicio.get().getNombre() + " × " + s.cantidad() + " · $" + Liquidacion.dinero(c.getValorTotal())
                + " (automático desde " + s.modulo().toLowerCase() + ")");
    }

    /** La cuenta abierta de la atención; si no hay, se abre con el contrato que corresponde. */
    private Cuenta cuentaAbierta(Long pacienteId, Long episodioId) {
        List<Cuenta> abiertas = cuentas.abiertasParaCargos(pacienteId, episodioId, EstadoCuenta.EDITABLES);
        if (!abiertas.isEmpty()) return abiertas.getFirst();
        Paciente p = pacienteService.buscarEntidad(pacienteId);
        Optional<Episodio> episodio = episodioId == null ? Optional.empty() : episodios.findById(episodioId);
        boolean particular = p.getRegimen() == Regimen.PARTICULAR
                || episodio.map(e -> e.getEstadoCobertura() == EstadoCobertura.PARTICULAR).orElse(false);
        String aseguradora = particular ? PARTICULAR : episodio.map(Episodio::getAseguradora).filter(Objects::nonNull)
                .orElse(Optional.ofNullable(p.getAseguradora()).orElse(PARTICULAR));
        LocalDate hoy = LocalDate.now(calendario.getZona());
        Contrato contrato = contratoVigente(aseguradora, hoy).orElse(null);
        boolean sinContrato = contrato == null;
        if (sinContrato) {
            contrato = contratoVigente(PARTICULAR, hoy)
                    .orElseThrow(() -> new ReglaNegocioException("SIN_CONTRATO", "No hay contrato particular vigente."));
        }
        boolean urgencia = episodio.map(e -> e.getTipoAtencion() == TipoAtencion.URGENCIAS
                || e.getEstadoCobertura() == EstadoCobertura.EXCEPCION_URGENCIA).orElse(false);
        String numero = "CTA-%d-%06d".formatted(Year.now().getValue(), cuentas.siguienteNumero());
        Cuenta c = cuentas.save(new Cuenta(numero, pacienteId, episodioId, contrato, aseguradora,
                episodio.map(Episodio::getNumeroAutorizacion).orElse(null), urgencia));
        evento(c, "ABIERTA", "Contrato: " + contrato.getNombre());
        if (sinContrato && !PARTICULAR.equals(aseguradora)) {
            evento(c, "SIN_CONTRATO", "No hay contrato vigente con " + aseguradora + ": se aplicó la tarifa particular.");
            notificaciones.paraRol(Rol.FACTURACION, "Aseguradora sin contrato", aseguradora + " (" + p.getNombreCompleto()
                    + "): la cuenta " + numero + " usa la tarifa particular. Revísela.", "/facturacion/cuentas/" + c.getId());
        }
        return c;
    }

    private Optional<Contrato> contratoVigente(String aseguradora, LocalDate fecha) {
        String buscada = Normalizador.texto(aseguradora);
        return contratos.findAllByOrderByAseguradoraAscVigenteDesdeDesc().stream()
                .filter(c -> Normalizador.texto(c.getAseguradora()).equals(buscada) && c.vigente(fecha)).findFirst();
    }

    /** RFRC-005: tarifa del servicio según el contrato. */
    static Cargo.Tarifa tarifa(Contrato contrato, ServicioFacturable s) {
        boolean autorizacion = s.isRequiereAutorizacion() || contrato.regla(s, ContratoRegla.Tipo.AUTORIZACION).isPresent();
        if (contrato.regla(s, ContratoRegla.Tipo.EXCLUSION).isPresent()) {
            return new Cargo.Tarifa(s.getTarifaBase().setScale(2, RoundingMode.HALF_UP), true, false, false);
        }
        BigDecimal valor = contrato.regla(s, ContratoRegla.Tipo.TARIFA_PACTADA).map(ContratoRegla::getValor)
                .orElse(s.getTarifaBase().multiply(contrato.getFactorTarifa()).setScale(0, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
        return new Cargo.Tarifa(valor, false, contrato.getModalidad() == Contrato.Modalidad.CAPITACION, autorizacion);
    }

    // ---------- Cargos manuales y correcciones ----------

    /** Abre (o devuelve la ya abierta) la cuenta de una atención, p. ej. para un paciente particular. */
    @Transactional
    public CuentaDto abrirCuenta(Long pacienteId, Long episodioId) {
        pacienteService.buscarEntidad(pacienteId);
        if (episodioId != null) {
            episodios.findById(episodioId).filter(e -> e.getPaciente().getId().equals(pacienteId))
                    .orElseThrow(() -> new DatosInvalidosException(Map.of("episodioId", "La atención no es de este paciente")));
        }
        return armar(cuentaAbierta(pacienteId, episodioId));
    }

    @Transactional
    public CuentaDto agregarCargo(Long cuentaId, CargoManualRequest r) {
        Cuenta c = buscar(cuentaId);
        c.exigirEditable();
        ServicioFacturable s = servicios.findById(r.servicioId()).filter(ServicioFacturable::isActivo)
                .orElseThrow(() -> new DatosInvalidosException(Map.of("servicioId", "Seleccione un servicio activo")));
        Instant fecha = r.fechaServicio() != null ? r.fechaServicio() : Instant.now();
        String soporte = limpio(r.soporte());
        // RFRC-011: el mismo servicio con el mismo soporte el mismo día es un duplicado
        LocalDate dia = LocalDate.ofInstant(fecha, calendario.getZona());
        boolean duplicado = c.activos().stream().anyMatch(x -> x.getServicio().getId().equals(s.getId())
                && soporte != null && soporte.equalsIgnoreCase(x.getSoporte())
                && LocalDate.ofInstant(x.getFechaServicio(), calendario.getZona()).equals(dia));
        if (duplicado) {
            throw new ReglaNegocioException("CARGO_DUPLICADO", s.getNombre() + " ya está cargado con ese soporte ese día.");
        }
        Cargo cargo = new Cargo(c, s, r.cantidad(), tarifa(c.getContrato(), s), soporte, limpio(r.soporteEnlace()), null, null,
                fecha, usuario());
        c.getCargos().add(cargo);
        cuentas.flush();
        evento(c, "CARGO", s.getNombre() + " × " + r.cantidad() + " (manual)" + (soporte == null ? " · sin soporte" : ""));
        return armar(c);
    }

    @Transactional
    public CuentaDto registrarSoporte(Long cuentaId, Long cargoId, String soporte, String enlace) {
        Cuenta c = buscar(cuentaId);
        c.exigirEditable();
        Cargo cargo = cargoDe(c, cargoId);
        cargo.registrarSoporte(soporte.trim(), limpio(enlace));
        evento(c, "SOPORTE", cargo.getServicio().getNombre() + ": " + soporte.trim());
        return armar(c);
    }

    /** RFAU-008: un cargo no se borra; se anula con motivo y queda en el historial. */
    @Transactional
    public CuentaDto anularCargo(Long cuentaId, Long cargoId, String motivo) {
        Cuenta c = buscar(cuentaId);
        c.exigirEditable();
        Cargo cargo = cargoDe(c, cargoId);
        cargo.anular(motivo.trim(), usuario());
        evento(c, "CARGO_ANULADO", cargo.getServicio().getNombre() + ": " + motivo.trim());
        return armar(c);
    }

    @Transactional
    public CuentaDto registrarAutorizacion(Long cuentaId, String numero) {
        Cuenta c = buscar(cuentaId);
        c.registrarAutorizacion(numero.trim());
        evento(c, "AUTORIZACION", "Autorización " + numero.trim());
        return armar(c);
    }

    // ---------- Auditoría ----------

    @Transactional
    public CuentaDto enviarAAuditoria(Long cuentaId, String respuesta) {
        Cuenta c = buscar(cuentaId);
        boolean reenvio = c.getEstado() == EstadoCuenta.DEVUELTA;
        c.enviarAAuditoria();
        evento(c, reenvio ? "REENVIADA_AUDITORIA" : "ENVIADA_AUDITORIA", limpio(respuesta));
        if (reenvio) publicador.publishEvent(new CuentaCorregida(c.getId(), limpio(respuesta), usuario()));
        notificaciones.paraRol(Rol.AUDITORIA, (reenvio ? "Cuenta corregida " : "Cuenta para auditar ") + c.getNumero(),
                pacienteService.buscarEntidad(c.getPacienteId()).getNombreCompleto() + " · " + c.getAseguradora(),
                "/auditoria-clinica/cuentas/" + c.getId());
        return armar(c);
    }

    /** Aviso para auditoría: la cuenta devuelta volvió con correcciones (cierra las devoluciones abiertas). */
    public record CuentaCorregida(Long cuentaId, String respuesta, String usuario) {
    }

    // ---------- Simulación y factura (RFRC-003 / 004 / 009 / 011 / 012) ----------

    @Transactional(readOnly = true)
    public LiquidacionDto simular(Long cuentaId) {
        return LiquidacionDto.de(Liquidacion.calcular(buscar(cuentaId)));
    }

    @Transactional
    public CuentaDto facturar(Long cuentaId) {
        Cuenta c = buscar(cuentaId);
        if (c.getEstado() != EstadoCuenta.AUDITADA) {
            throw new ReglaNegocioException("CUENTA_ESTADO_INVALIDO", "Solo se factura una cuenta aprobada por auditoría (estado actual: "
                    + c.getEstado().getDescripcion().toLowerCase() + ").");
        }
        Liquidacion.Resultado r = Liquidacion.calcular(c);
        if (!r.facturable()) {
            throw new ReglaNegocioException("FACTURA_BLOQUEADA", "No se puede facturar: " + String.join("; ", r.bloqueos()) + ".");
        }
        c.facturar();
        Instant ahora = Instant.now();
        LocalDate vence = LocalDate.ofInstant(ahora, calendario.getZona()).plusDays(c.getContrato().getDiasPago());
        String numero = prefijo + facturas.siguienteNumero();
        Factura f = facturas.save(new Factura(numero, c, ahora, vence, r.subtotal(), r.ajustes(), r.total(), r.valorPaciente(), usuario()));
        int orden = 0;
        for (Liquidacion.Linea l : r.lineas()) f.getLineas().add(new FacturaLinea(f, ++orden, l));
        // RFRC-011: cada cargo queda marcado con su factura; volver a facturarlo falla
        c.activos().stream().filter(x -> x.getFacturaId() == null).forEach(x -> x.facturar(f.getId()));
        facturas.flush();
        GeneradorFacturaElectronica.Documento doc = generador.generar(f, pacienteService.buscarEntidad(c.getPacienteId()));
        ProveedorFacturaElectronica.Resultado envio = proveedor.enviar(f.getNumero(), doc.cufe(), doc.xml());
        f.firmarElectronicamente(doc.cufe(), doc.xml(), envio.estado());
        evento(c, "FACTURADA", "Factura " + numero + " por $" + Liquidacion.dinero(r.total()) + " · " + envio.mensaje());
        if (f.isSaldada()) {
            c.marcarPagada();
            evento(c, "PAGADA", c.getContrato().getModalidad() == Contrato.Modalidad.CAPITACION
                    ? "Cubierta por la cápita: no hay saldo por cobrar" : "Sin saldo por cobrar");
        }
        return armar(c);
    }

    @Transactional(readOnly = true)
    public String xml(Long cuentaId) {
        return facturas.findFirstByCuentaIdOrderByFechaEmisionDesc(cuentaId).map(Factura::getXml)
                .orElseThrow(() -> new RecursoNoEncontradoException("Factura de la cuenta", cuentaId));
    }

    // ---------- Glosas y pagos (RFRC-008) ----------

    @Transactional
    public CuentaDto registrarGlosa(Long cuentaId, GlosaRequest r) {
        Cuenta c = buscar(cuentaId);
        Factura f = facturaDe(c);
        if (!r.codigo().startsWith(String.valueOf(r.concepto().getCodigo()))) {
            throw new DatosInvalidosException(Map.of("codigo", "Los códigos de " + r.concepto().getDescripcion().toLowerCase()
                    + " empiezan por " + r.concepto().getCodigo()));
        }
        if (r.valor().compareTo(f.getSaldo()) > 0) {
            throw new DatosInvalidosException(Map.of("valor", "Supera el saldo de la factura ($" + Liquidacion.dinero(f.getSaldo()) + ")"));
        }
        if (r.cargoId() != null) cargoDe(c, r.cargoId());
        c.glosar();
        Glosa g = glosas.save(new Glosa(f, r.cargoId(), r.concepto(), r.codigo(), r.descripcion().trim(), r.valor(),
                r.recibidaEn(), sumarDiasHabiles(r.recibidaEn(), diasRespuestaGlosa), usuario()));
        evento(c, "GLOSA", r.concepto().getDescripcion() + " " + r.codigo() + " por $" + Liquidacion.dinero(r.valor())
                + " · responder antes del " + g.getLimiteRespuesta());
        if (r.concepto().isClinica()) {
            notificaciones.paraRol(Rol.AUDITORIA, "Glosa por causa clínica " + f.getNumero(), r.descripcion().trim(),
                    "/facturacion/cuentas/" + c.getId());
        }
        return armar(c);
    }

    @Transactional
    public CuentaDto responderGlosa(Long cuentaId, Long glosaId, BigDecimal aceptado, String respuesta) {
        Cuenta c = buscar(cuentaId);
        Factura f = facturaDe(c);
        Glosa g = glosas.findById(glosaId).filter(x -> x.getFactura().getId().equals(f.getId()))
                .orElseThrow(() -> new RecursoNoEncontradoException("Glosa", glosaId));
        BigDecimal valor = g.responder(aceptado, respuesta.trim(), usuario());
        if (valor.signum() > 0) f.abonar(valor, "valor aceptado");
        evento(c, "GLOSA_RESPONDIDA", g.getCodigo() + ": " + g.getEstado().getDescripcion().toLowerCase()
                + (valor.signum() > 0 ? " ($" + Liquidacion.dinero(valor) + " aceptados)" : ""));
        saldarSiCorresponde(c, f);
        return armar(c);
    }

    @Transactional
    public CuentaDto registrarPago(Long cuentaId, PagoRequest r) {
        Cuenta c = buscar(cuentaId);
        Factura f = facturaDe(c);
        f.abonar(r.valor(), "pago");
        pagos.save(new Pago(f.getId(), r.valor(), r.fecha(), r.medio().trim(), limpio(r.referencia()), usuario()));
        evento(c, "PAGO", "$" + Liquidacion.dinero(r.valor()) + " · " + r.medio().trim() + (limpio(r.referencia()) == null ? "" : " · " + r.referencia().trim()));
        saldarSiCorresponde(c, f);
        return armar(c);
    }

    private void saldarSiCorresponde(Cuenta c, Factura f) {
        if (f.isSaldada() && c.getEstado() != EstadoCuenta.PAGADA && c.getEstado() != EstadoCuenta.CERRADA) {
            c.marcarPagada();
            evento(c, "PAGADA", "Saldo en cero");
        }
    }

    @Transactional
    public CuentaDto cerrar(Long cuentaId) {
        Cuenta c = buscar(cuentaId);
        Optional<Factura> f = facturas.findFirstByCuentaIdOrderByFechaEmisionDesc(cuentaId);
        if (f.isPresent() && glosas.findByFacturaIdOrderByRecibidaEn(f.get().getId()).stream().anyMatch(g -> g.getEstado() == Glosa.Estado.RADICADA)) {
            throw new ReglaNegocioException("GLOSAS_PENDIENTES", "Hay glosas sin responder.");
        }
        c.cerrar();
        evento(c, "CERRADA", "Cierre financiero");
        return armar(c);
    }

    // ---------- Consultas ----------

    @Transactional(readOnly = true)
    public List<CuentaResumenDto> listar(EstadoCuenta estado, String texto) {
        Set<EstadoCuenta> estados = estado != null ? EnumSet.of(estado) : EnumSet.complementOf(EnumSet.of(EstadoCuenta.CERRADA));
        String t = Normalizador.texto(texto);
        return cuentas.findByEstadoInOrderByAbiertaEnDesc(estados).stream().map(this::resumen)
                .filter(r -> t.isEmpty() || Normalizador.texto(r.numero() + " " + r.paciente().nombreCompleto() + " "
                        + r.paciente().numeroDocumento() + " " + r.aseguradora()).contains(t))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CuentaResumenDto> dePaciente(Long pacienteId) {
        return cuentas.findByPacienteIdOrderByAbiertaEnDesc(pacienteId).stream().map(this::resumen).toList();
    }

    @Transactional(readOnly = true)
    public CuentaDto obtener(Long id) {
        return armar(buscar(id));
    }

    // ---------- Cartera (RFRC-010) ----------

    @Transactional(readOnly = true)
    public CarteraDto cartera() {
        LocalDate hoy = LocalDate.now(calendario.getZona());
        List<Factura> abiertas = facturas.findBySaldoGreaterThanOrderByFechaVencimiento(BigDecimal.ZERO);
        Map<String, BigDecimal> rangos = new LinkedHashMap<>();
        Map<String, Integer> conteos = new LinkedHashMap<>();
        List.of("Por vencer", "1 a 30 días", "31 a 60 días", "61 a 90 días", "Más de 90 días").forEach(r -> {
            rangos.put(r, BigDecimal.ZERO);
            conteos.put(r, 0);
        });
        Map<String, BigDecimal[]> porAseguradora = new LinkedHashMap<>();
        List<FacturaCarteraDto> filas = new ArrayList<>();
        for (Factura f : abiertas) {
            long dias = Math.max(0, ChronoUnit.DAYS.between(f.getFechaVencimiento(), hoy));
            String rango = dias == 0 ? "Por vencer" : dias <= 30 ? "1 a 30 días" : dias <= 60 ? "31 a 60 días" : dias <= 90 ? "61 a 90 días" : "Más de 90 días";
            rangos.merge(rango, f.getSaldo(), BigDecimal::add);
            conteos.merge(rango, 1, Integer::sum);
            BigDecimal[] acum = porAseguradora.computeIfAbsent(f.getAseguradora(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            acum[0] = acum[0].add(f.getSaldo());
            if (dias > 0) acum[1] = acum[1].add(f.getSaldo());
            acum[2] = acum[2].add(BigDecimal.ONE);
            long pendientes = glosas.findByFacturaIdOrderByRecibidaEn(f.getId()).stream().filter(g -> g.getEstado() == Glosa.Estado.RADICADA).count();
            filas.add(new FacturaCarteraDto(f.getId(), f.getNumero(), f.getCuenta().getId(), f.getAseguradora(),
                    pacienteService.buscarEntidad(f.getCuenta().getPacienteId()).getNombreCompleto(), f.getFechaEmision(),
                    f.getFechaVencimiento(), f.getTotal(), f.getSaldo(), dias, pendientes));
        }
        filas.sort(Comparator.comparing(FacturaCarteraDto::diasVencida).reversed());
        BigDecimal total = abiertas.stream().map(Factura::getSaldo).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vencido = filas.stream().filter(x -> x.diasVencida() > 0).map(FacturaCarteraDto::saldo).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CarteraDto(total, vencido,
                rangos.entrySet().stream().map(e -> new RangoCarteraDto(e.getKey(), e.getValue(), conteos.get(e.getKey()))).toList(),
                porAseguradora.entrySet().stream().map(e -> new AseguradoraCarteraDto(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2].intValue())).sorted(Comparator.comparing(AseguradoraCarteraDto::saldo).reversed()).toList(),
                filas);
    }

    /** RFRC-010: una alerta por factura cada vez que cruza un umbral de mora. */
    @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT4M")
    @Transactional
    public void alertarCartera() {
        LocalDate hoy = LocalDate.now(calendario.getZona());
        for (Factura f : facturas.findBySaldoGreaterThanOrderByFechaVencimiento(BigDecimal.ZERO)) {
            long dias = ChronoUnit.DAYS.between(f.getFechaVencimiento(), hoy);
            umbralesCartera.stream().filter(u -> dias >= u)
                    .filter(u -> !alertas.existsById(new AlertaCartera.Clave(f.getId(), u)))
                    .reduce((a, b) -> b)
                    .ifPresent(u -> {
                        umbralesCartera.stream().filter(x -> x <= u).forEach(x -> {
                            if (!alertas.existsById(new AlertaCartera.Clave(f.getId(), x))) alertas.save(new AlertaCartera(f.getId(), x));
                        });
                        notificaciones.paraRol(Rol.FACTURACION, "Cartera con más de " + u + " días · " + f.getAseguradora(),
                                "Factura " + f.getNumero() + ": saldo $" + Liquidacion.dinero(f.getSaldo()) + ", vencida hace " + dias + " días.",
                                "/facturacion/cuentas/" + f.getCuenta().getId());
                    });
        }
    }

    // ---------- Catálogo de servicios y contratos (RFRC-005 / 006) ----------

    @Transactional(readOnly = true)
    public List<ServicioDto> servicios() {
        return servicios.findAllByOrderByTipoAscNombreAsc().stream().map(ServicioDto::de).toList();
    }

    @Transactional
    public ServicioDto guardarServicio(Long id, ServicioRequest r) {
        boolean requiere = Boolean.TRUE.equals(r.requiereAutorizacion());
        boolean activo = r.activo() == null || r.activo();
        ServicioFacturable s;
        String accion;
        if (id == null) {
            if (servicios.findByCodigo(r.codigo().trim()).isPresent()) {
                throw new DatosInvalidosException(Map.of("codigo", "Ya existe un servicio con ese código"));
            }
            s = servicios.save(new ServicioFacturable(r.codigo().trim(), r.nombre().trim(), r.tipo(), r.tarifaBase(), requiere, usuario()));
            accion = "CREADO";
        } else {
            s = servicios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Servicio", id));
            accion = activo == s.isActivo() ? "MODIFICADO" : activo ? "REACTIVADO" : "INACTIVADO";
            s.actualizar(r.nombre().trim(), r.tarifaBase(), requiere, activo, usuario());
        }
        catalogos.registrarCambio(CatalogoService.SERVICIOS, s.getCodigo(), accion,
                s.getNombre() + " · tarifa $" + Liquidacion.dinero(s.getTarifaBase()) + (requiere ? " · exige autorización" : ""));
        return ServicioDto.de(s);
    }

    @Transactional(readOnly = true)
    public List<ContratoDto> contratos() {
        return contratos.findAllByOrderByAseguradoraAscVigenteDesdeDesc().stream().map(ContratoDto::de).toList();
    }

    @Transactional
    public ContratoDto guardarContrato(Long id, ContratoRequest r) {
        if (r.vigenteHasta() != null && r.vigenteHasta().isBefore(r.vigenteDesde())) {
            throw new DatosInvalidosException(Map.of("vigenteHasta", "Debe ser posterior al inicio"));
        }
        Contrato c;
        if (id == null) {
            c = contratos.save(new Contrato(r.aseguradora().trim(), r.nombre().trim(), r.modalidad(), r.factorTarifa(),
                    r.diasPago(), r.topeCuenta(), r.vigenteDesde(), r.vigenteHasta(), usuario()));
        } else {
            c = buscarContrato(id);
            c.configurar(r.nombre().trim(), r.modalidad(), r.factorTarifa(), r.diasPago(), r.topeCuenta(), r.vigenteDesde(),
                    r.vigenteHasta(), r.activo() == null || r.activo(), usuario());
        }
        return ContratoDto.de(c);
    }

    @Transactional
    public ContratoDto agregarRegla(Long contratoId, ReglaRequest r) {
        Contrato c = buscarContrato(contratoId);
        ServicioFacturable s = servicios.findById(r.servicioId()).orElseThrow(() -> new RecursoNoEncontradoException("Servicio", r.servicioId()));
        if (r.tipo() == ContratoRegla.Tipo.TARIFA_PACTADA && r.valor() == null) {
            throw new DatosInvalidosException(Map.of("valor", "Indique la tarifa pactada"));
        }
        if (r.tipo() == ContratoRegla.Tipo.TOPE_CANTIDAD && r.cantidadMax() == null) {
            throw new DatosInvalidosException(Map.of("cantidadMax", "Indique la cantidad máxima"));
        }
        if (c.regla(s, r.tipo()).isPresent()) {
            throw new ReglaNegocioException("REGLA_EXISTENTE", "Ese servicio ya tiene una regla de ese tipo en el contrato.");
        }
        c.getReglas().add(new ContratoRegla(c, s, r.tipo(), r.valor(), r.cantidadMax()));
        contratos.flush();
        return ContratoDto.de(c);
    }

    @Transactional
    public ContratoDto quitarRegla(Long contratoId, Long reglaId) {
        Contrato c = buscarContrato(contratoId);
        if (!c.getReglas().removeIf(r -> r.getId().equals(reglaId))) throw new RecursoNoEncontradoException("Regla", reglaId);
        return ContratoDto.de(c);
    }

    @Transactional
    public ContratoDto agregarPaquete(Long contratoId, PaqueteRequest r) {
        Contrato c = buscarContrato(contratoId);
        Set<ServicioFacturable> incluidos = new LinkedHashSet<>(servicios.findAllById(r.servicioIds()));
        if (incluidos.size() != new LinkedHashSet<>(r.servicioIds()).size()) {
            throw new DatosInvalidosException(Map.of("servicioIds", "Algún servicio no existe"));
        }
        if (c.getPaquetes().stream().anyMatch(p -> p.getCodigo().equalsIgnoreCase(r.codigo().trim()))) {
            throw new DatosInvalidosException(Map.of("codigo", "Ya hay un paquete con ese código"));
        }
        c.getPaquetes().add(new Paquete(c, r.codigo().trim(), r.nombre().trim(), r.precio(), incluidos));
        contratos.flush();
        return ContratoDto.de(c);
    }

    @Transactional
    public ContratoDto quitarPaquete(Long contratoId, Long paqueteId) {
        Contrato c = buscarContrato(contratoId);
        if (!c.getPaquetes().removeIf(p -> p.getId().equals(paqueteId))) throw new RecursoNoEncontradoException("Paquete", paqueteId);
        return ContratoDto.de(c);
    }

    // ---------- armado y utilidades ----------

    public CuentaResumenDto resumen(Cuenta c) {
        Optional<Factura> f = facturas.findFirstByCuentaIdOrderByFechaEmisionDesc(c.getId());
        List<Cargo> activos = c.activos();
        return new CuentaResumenDto(c.getId(), c.getNumero(), PacienteResumenDto.de(pacienteService.buscarEntidad(c.getPacienteId())),
                c.getAseguradora(), c.getContrato().getNombre(), c.getContrato().getModalidad().getDescripcion(), c.getEstado().name(),
                c.getEstado().getDescripcion(), c.getAbiertaEn(), activos.size(),
                activos.stream().filter(x -> !x.isACargoPaciente()).map(Cargo::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                (int) activos.stream().filter(x -> !x.isTieneSoporte()).count(), f.map(Factura::getNumero).orElse(null),
                f.map(Factura::getSaldo).orElse(null));
    }

    private CuentaDto armar(Cuenta c) {
        LocalDate hoy = LocalDate.now(calendario.getZona());
        Optional<Factura> f = facturas.findFirstByCuentaIdOrderByFechaEmisionDesc(c.getId());
        boolean pendiente = c.activos().stream().anyMatch(x -> x.getFacturaId() == null);
        return new CuentaDto(resumen(c), c.getEpisodioId(), c.getNumeroAutorizacion(), c.isUrgencia(), c.getContrato().getId(),
                c.getCargos().stream().map(CargoDto::de).toList(),
                pendiente ? LiquidacionDto.de(Liquidacion.calcular(c)) : null,
                f.map(FacturaDto::de).orElse(null),
                f.map(x -> glosas.findByFacturaIdOrderByRecibidaEn(x.getId()).stream().map(g -> GlosaDto.de(g, hoy)).toList()).orElse(List.of()),
                f.map(x -> pagos.findByFacturaIdOrderByFecha(x.getId()).stream().map(PagoDto::de).toList()).orElse(List.of()),
                eventos.findByCuentaIdOrderByFechaAscIdAsc(c.getId()).stream().map(EventoDto::de).toList());
    }

    /** Días hábiles (lunes a viernes); los festivos no se descuentan. */
    static LocalDate sumarDiasHabiles(LocalDate desde, int dias) {
        LocalDate d = desde;
        int contados = 0;
        while (contados < dias) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) contados++;
        }
        return d;
    }

    public Cuenta buscar(Long id) {
        return cuentas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cuenta", id));
    }

    private Contrato buscarContrato(Long id) {
        return contratos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Contrato", id));
    }

    private Factura facturaDe(Cuenta c) {
        return facturas.findFirstByCuentaIdOrderByFechaEmisionDesc(c.getId())
                .orElseThrow(() -> new ReglaNegocioException("SIN_FACTURA", "La cuenta aún no tiene factura."));
    }

    private static Cargo cargoDe(Cuenta c, Long cargoId) {
        return c.getCargos().stream().filter(x -> x.getId().equals(cargoId)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Cargo", cargoId));
    }

    public void evento(Cuenta c, String accion, String detalle) {
        eventos.save(new EventoCuenta(c.getId(), accion, detalle, usuario()));
    }

    private String usuario() {
        return auditor.getCurrentAuditor().orElse("sistema");
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
