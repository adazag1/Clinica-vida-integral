package co.edu.ucentral.clinica.facturacion.dto;

import co.edu.ucentral.clinica.facturacion.Cargo;
import co.edu.ucentral.clinica.facturacion.Contrato;
import co.edu.ucentral.clinica.facturacion.ContratoRegla;
import co.edu.ucentral.clinica.facturacion.EventoCuenta;
import co.edu.ucentral.clinica.facturacion.Factura;
import co.edu.ucentral.clinica.facturacion.FacturaLinea;
import co.edu.ucentral.clinica.facturacion.Glosa;
import co.edu.ucentral.clinica.facturacion.Liquidacion;
import co.edu.ucentral.clinica.facturacion.Pago;
import co.edu.ucentral.clinica.facturacion.Paquete;
import co.edu.ucentral.clinica.facturacion.ServicioFacturable;
import co.edu.ucentral.clinica.pacientes.dto.PacienteResumenDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Respuestas del módulo de facturación. */
public final class FacturacionDtos {

    private FacturacionDtos() {
    }

    public record ServicioDto(Long id, String codigo, String nombre, String tipo, String tipoDescripcion, String claveOrigen,
                              BigDecimal tarifaBase, boolean requiereAutorizacion, boolean activo, String modificadoPor,
                              Instant modificadoEn) {
        public static ServicioDto de(ServicioFacturable s) {
            return new ServicioDto(s.getId(), s.getCodigo(), s.getNombre(), s.getTipo().name(), s.getTipo().getDescripcion(),
                    s.getClaveOrigen(), s.getTarifaBase(), s.isRequiereAutorizacion(), s.isActivo(), s.getModificadoPor(),
                    s.getModificadoEn());
        }
    }

    public record ReglaDto(Long id, Long servicioId, String servicioCodigo, String servicioNombre, String tipo,
                           String tipoDescripcion, BigDecimal valor, Integer cantidadMax) {
        public static ReglaDto de(ContratoRegla r) {
            return new ReglaDto(r.getId(), r.getServicio().getId(), r.getServicio().getCodigo(), r.getServicio().getNombre(),
                    r.getTipo().name(), r.getTipo().getDescripcion(), r.getValor(), r.getCantidadMax());
        }
    }

    public record PaqueteDto(Long id, String codigo, String nombre, BigDecimal precio, List<String> servicios) {
        public static PaqueteDto de(Paquete p) {
            return new PaqueteDto(p.getId(), p.getCodigo(), p.getNombre(), p.getPrecio(),
                    p.getServicios().stream().map(s -> s.getCodigo() + " · " + s.getNombre()).toList());
        }
    }

    public record ContratoDto(Long id, String aseguradora, String nombre, String modalidad, String modalidadDescripcion,
                              BigDecimal factorTarifa, int diasPago, BigDecimal topeCuenta, LocalDate vigenteDesde,
                              LocalDate vigenteHasta, boolean activo, String modificadoPor, Instant modificadoEn,
                              List<ReglaDto> reglas, List<PaqueteDto> paquetes) {
        public static ContratoDto de(Contrato c) {
            return new ContratoDto(c.getId(), c.getAseguradora(), c.getNombre(), c.getModalidad().name(),
                    c.getModalidad().getDescripcion(), c.getFactorTarifa(), c.getDiasPago(), c.getTopeCuenta(),
                    c.getVigenteDesde(), c.getVigenteHasta(), c.isActivo(), c.getModificadoPor(), c.getModificadoEn(),
                    c.getReglas().stream().map(ReglaDto::de).toList(), c.getPaquetes().stream().map(PaqueteDto::de).toList());
        }
    }

    public record CargoDto(Long id, Long servicioId, String servicioCodigo, String servicioNombre, String tipo, int cantidad,
                           BigDecimal valorUnitario, BigDecimal valorTotal, boolean aCargoPaciente, boolean cubiertoCapitacion,
                           boolean requiereAutorizacion, boolean tieneSoporte, String soporte, String soporteEnlace,
                           String origenModulo, Instant fechaServicio, String estado, boolean facturado,
                           String motivoAnulacion, String anuladoPor, String registradoPor) {
        public static CargoDto de(Cargo c) {
            return new CargoDto(c.getId(), c.getServicio().getId(), c.getServicio().getCodigo(), c.getServicio().getNombre(),
                    c.getServicio().getTipo().getDescripcion(), c.getCantidad(), c.getValorUnitario(), c.getValorTotal(),
                    c.isACargoPaciente(), c.isCubiertoCapitacion(), c.isRequiereAutorizacion(), c.isTieneSoporte(),
                    c.getSoporte(), c.getSoporteEnlace(), c.getOrigenModulo(), c.getFechaServicio(), c.getEstado().name(),
                    c.getFacturaId() != null, c.getMotivoAnulacion(), c.getAnuladoPor(), c.getRegistradoPor());
        }
    }

    /** RFRC-012: la simulación es exactamente lo que se emitiría. */
    public record LiquidacionDto(List<Liquidacion.Linea> lineas, List<Liquidacion.Linea> lineasPaciente, BigDecimal subtotal,
                                 BigDecimal ajustes, BigDecimal total, BigDecimal valorPaciente, List<String> advertencias,
                                 List<String> bloqueos, boolean facturable) {
        public static LiquidacionDto de(Liquidacion.Resultado r) {
            return new LiquidacionDto(r.lineas(), r.lineasPaciente(), r.subtotal(), r.ajustes(), r.total(), r.valorPaciente(),
                    r.advertencias(), r.bloqueos(), r.facturable());
        }
    }

    public record CuentaResumenDto(Long id, String numero, PacienteResumenDto paciente, String aseguradora, String contrato,
                                   String modalidad, String estado, String estadoDescripcion, Instant abiertaEn, int cargos,
                                   BigDecimal valorCargos, int sinSoporte, String factura, BigDecimal saldo) {
    }

    public record LineaFacturaDto(int orden, String codigo, String descripcion, int cantidad, BigDecimal valorUnitario,
                                  BigDecimal valorTotal, String nota) {
        public static LineaFacturaDto de(FacturaLinea l) {
            return new LineaFacturaDto(l.getOrden(), l.getCodigo(), l.getDescripcion(), l.getCantidad(), l.getValorUnitario(),
                    l.getValorTotal(), l.getNota());
        }
    }

    public record FacturaDto(Long id, String numero, Instant fechaEmision, LocalDate fechaVencimiento, BigDecimal subtotal,
                             BigDecimal ajustes, BigDecimal total, BigDecimal valorPaciente, BigDecimal saldo, String cufe,
                             String estadoDian, String emitidaPor, List<LineaFacturaDto> lineas) {
        public static FacturaDto de(Factura f) {
            return new FacturaDto(f.getId(), f.getNumero(), f.getFechaEmision(), f.getFechaVencimiento(), f.getSubtotal(),
                    f.getAjustes(), f.getTotal(), f.getValorPaciente(), f.getSaldo(), f.getCufe(), f.getEstadoDian(),
                    f.getEmitidaPor(), f.getLineas().stream().map(LineaFacturaDto::de).toList());
        }
    }

    public record GlosaDto(Long id, Long cargoId, String concepto, String conceptoDescripcion, boolean causaClinica,
                           String codigo, String descripcion, BigDecimal valor, String estado, String estadoDescripcion,
                           BigDecimal valorAceptado, String respuesta, LocalDate recibidaEn, LocalDate limiteRespuesta,
                           boolean vencida, Instant respondidaEn, String respondidaPor, String registradaPor) {
        public static GlosaDto de(Glosa g, LocalDate hoy) {
            return new GlosaDto(g.getId(), g.getCargoId(), g.getConcepto().name(), g.getConcepto().getDescripcion(),
                    g.getConcepto().isClinica(), g.getCodigo(), g.getDescripcion(), g.getValor(), g.getEstado().name(),
                    g.getEstado().getDescripcion(), g.getValorAceptado(), g.getRespuesta(), g.getRecibidaEn(),
                    g.getLimiteRespuesta(), g.getEstado() == Glosa.Estado.RADICADA && hoy.isAfter(g.getLimiteRespuesta()),
                    g.getRespondidaEn(), g.getRespondidaPor(), g.getRegistradaPor());
        }
    }

    public record PagoDto(Long id, BigDecimal valor, LocalDate fecha, String medio, String referencia, String registradoPor,
                          Instant registradoEn) {
        public static PagoDto de(Pago p) {
            return new PagoDto(p.getId(), p.getValor(), p.getFecha(), p.getMedio(), p.getReferencia(), p.getRegistradoPor(),
                    p.getRegistradoEn());
        }
    }

    public record EventoDto(String accion, String detalle, String usuario, Instant fecha) {
        public static EventoDto de(EventoCuenta e) {
            return new EventoDto(e.getAccion(), e.getDetalle(), e.getUsuario(), e.getFecha());
        }
    }

    /**
     * @param liquidacion simulación de lo pendiente por facturar; nula si ya no hay nada que facturar
     */
    public record CuentaDto(CuentaResumenDto resumen, Long episodioId, String numeroAutorizacion, boolean urgencia,
                            Long contratoId, List<CargoDto> cargos, LiquidacionDto liquidacion, FacturaDto factura,
                            List<GlosaDto> glosas, List<PagoDto> pagos, List<EventoDto> eventos) {
    }

    public record RangoCarteraDto(String rango, BigDecimal saldo, int facturas) {
    }

    public record AseguradoraCarteraDto(String aseguradora, BigDecimal saldo, BigDecimal vencido, int facturas) {
    }

    public record FacturaCarteraDto(Long id, String numero, Long cuentaId, String aseguradora, String paciente,
                                    Instant emitida, LocalDate vence, BigDecimal total, BigDecimal saldo, long diasVencida,
                                    long glosasPendientes) {
    }

    /** RFRC-010: cartera por edades. */
    public record CarteraDto(BigDecimal porCobrar, BigDecimal vencido, List<RangoCarteraDto> rangos,
                             List<AseguradoraCarteraDto> porAseguradora, List<FacturaCarteraDto> facturas) {
    }
}
