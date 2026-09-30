package co.edu.ucentral.clinica.facturacion;

import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.CarteraDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.ContratoDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.CuentaDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.CuentaResumenDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.LiquidacionDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionDtos.ServicioDto;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.AutorizacionRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.CargoManualRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.ContratoRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.EnvioAuditoriaRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.GlosaRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.MotivoRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.PagoRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.PaqueteRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.ReglaRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.RespuestaGlosaRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.ServicioRequest;
import co.edu.ucentral.clinica.facturacion.dto.FacturacionSolicitudes.SoporteRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/facturacion")
@Tag(name = "Facturación", description = "Cuentas, cargos, contratos, factura electrónica, glosas, pagos y cartera")
public class FacturacionController {

    static final String FACTURACION = "hasAnyRole('ADMIN','FACTURACION')";
    /** Auditoría revisa las cuentas; dirección ve la cartera. */
    static final String LECTURA = "hasAnyRole('ADMIN','FACTURACION','AUDITORIA','DIRECTIVO')";

    private final FacturacionService service;

    public FacturacionController(FacturacionService service) {
        this.service = service;
    }

    @GetMapping("/cuentas")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Cuentas por estado o búsqueda por número, paciente o aseguradora (RFRC-007)")
    public List<CuentaResumenDto> listar(@RequestParam(required = false) EstadoCuenta estado,
                                         @RequestParam(required = false) String texto) {
        return service.listar(estado, texto);
    }

    public record AperturaRequest(@jakarta.validation.constraints.NotNull(message = "Seleccione el paciente") Long pacienteId, Long episodioId) {
    }

    @PostMapping("/cuentas")
    @PreAuthorize(FACTURACION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abre la cuenta de una atención con el contrato de la aseguradora (o devuelve la que ya está abierta)")
    public CuentaDto abrir(@Valid @RequestBody AperturaRequest request) {
        return service.abrirCuenta(request.pacienteId(), request.episodioId());
    }

    @GetMapping("/cuentas/pacientes/{pacienteId}")
    @PreAuthorize(LECTURA)
    public List<CuentaResumenDto> dePaciente(@PathVariable Long pacienteId) {
        return service.dePaciente(pacienteId);
    }

    @GetMapping("/cuentas/{id}")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Cuenta con cargos, simulación, factura, glosas, pagos e historial (RFRC-014)")
    public CuentaDto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping("/cuentas/{id}/cargos")
    @PreAuthorize(FACTURACION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cargo manual (insumos, procedimientos, estancia); sin soporte bloquea la factura")
    public CuentaDto agregarCargo(@PathVariable Long id, @Valid @RequestBody CargoManualRequest request) {
        return service.agregarCargo(id, request);
    }

    @PutMapping("/cuentas/{id}/cargos/{cargoId}/soporte")
    @PreAuthorize(FACTURACION)
    public CuentaDto registrarSoporte(@PathVariable Long id, @PathVariable Long cargoId, @Valid @RequestBody SoporteRequest request) {
        return service.registrarSoporte(id, cargoId, request.soporte(), request.enlace());
    }

    @PostMapping("/cuentas/{id}/cargos/{cargoId}/anular")
    @PreAuthorize(FACTURACION)
    @Operation(summary = "Anula un cargo con motivo; nunca se borra (RFAU-008)")
    public CuentaDto anularCargo(@PathVariable Long id, @PathVariable Long cargoId, @Valid @RequestBody MotivoRequest request) {
        return service.anularCargo(id, cargoId, request.motivo());
    }

    @PutMapping("/cuentas/{id}/autorizacion")
    @PreAuthorize("hasAnyRole('ADMIN','FACTURACION','ADMISIONES')")
    @Operation(summary = "Registra el número de autorización de la aseguradora (RFRC-004)")
    public CuentaDto registrarAutorizacion(@PathVariable Long id, @Valid @RequestBody AutorizacionRequest request) {
        return service.registrarAutorizacion(id, request.numero());
    }

    @PostMapping("/cuentas/{id}/enviar-auditoria")
    @PreAuthorize(FACTURACION)
    public CuentaDto enviarAAuditoria(@PathVariable Long id, @Valid @RequestBody(required = false) EnvioAuditoriaRequest request) {
        return service.enviarAAuditoria(id, request == null ? null : request.respuesta());
    }

    @GetMapping("/cuentas/{id}/simulacion")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Simula la factura con las reglas del contrato, sin emitirla (RFRC-012)")
    public LiquidacionDto simular(@PathVariable Long id) {
        return service.simular(id);
    }

    @PostMapping("/cuentas/{id}/facturar")
    @PreAuthorize(FACTURACION)
    @Operation(summary = "Emite la factura: exige soportes y autorizaciones y no factura dos veces un cargo (RFRC-003/004/009/011)")
    public CuentaDto facturar(@PathVariable Long id) {
        return service.facturar(id);
    }

    @GetMapping(value = "/cuentas/{id}/factura.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize(LECTURA)
    public ResponseEntity<String> xml(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("factura-" + id + ".xml").build().toString())
                .contentType(MediaType.APPLICATION_XML).body(service.xml(id));
    }

    @PostMapping("/cuentas/{id}/glosas")
    @PreAuthorize(FACTURACION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una glosa con concepto y código del Manual Único de Glosas (RFRC-008)")
    public CuentaDto registrarGlosa(@PathVariable Long id, @Valid @RequestBody GlosaRequest request) {
        return service.registrarGlosa(id, request);
    }

    @PostMapping("/cuentas/{id}/glosas/{glosaId}/respuesta")
    @PreAuthorize(FACTURACION)
    public CuentaDto responderGlosa(@PathVariable Long id, @PathVariable Long glosaId, @Valid @RequestBody RespuestaGlosaRequest request) {
        return service.responderGlosa(id, glosaId, request.valorAceptado(), request.respuesta());
    }

    @PostMapping("/cuentas/{id}/pagos")
    @PreAuthorize(FACTURACION)
    @ResponseStatus(HttpStatus.CREATED)
    public CuentaDto registrarPago(@PathVariable Long id, @Valid @RequestBody PagoRequest request) {
        return service.registrarPago(id, request);
    }

    @PostMapping("/cuentas/{id}/cerrar")
    @PreAuthorize(FACTURACION)
    public CuentaDto cerrar(@PathVariable Long id) {
        return service.cerrar(id);
    }

    @GetMapping("/cartera")
    @PreAuthorize("hasAnyRole('ADMIN','FACTURACION','DIRECTIVO')")
    @Operation(summary = "Cartera por edades y por aseguradora (RFRC-010)")
    public CarteraDto cartera() {
        return service.cartera();
    }

    // ---------- Catálogo y contratos ----------

    @GetMapping("/servicios")
    @PreAuthorize(LECTURA)
    public List<ServicioDto> servicios() {
        return service.servicios();
    }

    @PostMapping("/servicios")
    @PreAuthorize(FACTURACION)
    @ResponseStatus(HttpStatus.CREATED)
    public ServicioDto crearServicio(@Valid @RequestBody ServicioRequest request) {
        return service.guardarServicio(null, request);
    }

    @PutMapping("/servicios/{id}")
    @PreAuthorize(FACTURACION)
    @Operation(summary = "Cambia nombre, tarifa, autorización o estado; queda en el historial del catálogo (RFAU-005)")
    public ServicioDto actualizarServicio(@PathVariable Long id, @Valid @RequestBody ServicioRequest request) {
        return service.guardarServicio(id, request);
    }

    @GetMapping("/contratos")
    @PreAuthorize(LECTURA)
    public List<ContratoDto> contratos() {
        return service.contratos();
    }

    @PostMapping("/contratos")
    @PreAuthorize(FACTURACION)
    @ResponseStatus(HttpStatus.CREATED)
    public ContratoDto crearContrato(@Valid @RequestBody ContratoRequest request) {
        return service.guardarContrato(null, request);
    }

    @PutMapping("/contratos/{id}")
    @PreAuthorize(FACTURACION)
    @Operation(summary = "Modalidad, factor sobre la tarifa, plazo de pago, tope y vigencia (RFRC-005/006)")
    public ContratoDto actualizarContrato(@PathVariable Long id, @Valid @RequestBody ContratoRequest request) {
        return service.guardarContrato(id, request);
    }

    @PostMapping("/contratos/{id}/reglas")
    @PreAuthorize(FACTURACION)
    @Operation(summary = "Tarifa pactada, exclusión, tope de cantidad o autorización obligatoria para un servicio")
    public ContratoDto agregarRegla(@PathVariable Long id, @Valid @RequestBody ReglaRequest request) {
        return service.agregarRegla(id, request);
    }

    @DeleteMapping("/contratos/{id}/reglas/{reglaId}")
    @PreAuthorize(FACTURACION)
    public ContratoDto quitarRegla(@PathVariable Long id, @PathVariable Long reglaId) {
        return service.quitarRegla(id, reglaId);
    }

    @PostMapping("/contratos/{id}/paquetes")
    @PreAuthorize(FACTURACION)
    public ContratoDto agregarPaquete(@PathVariable Long id, @Valid @RequestBody PaqueteRequest request) {
        return service.agregarPaquete(id, request);
    }

    @DeleteMapping("/contratos/{id}/paquetes/{paqueteId}")
    @PreAuthorize(FACTURACION)
    public ContratoDto quitarPaquete(@PathVariable Long id, @PathVariable Long paqueteId) {
        return service.quitarPaquete(id, paqueteId);
    }
}
