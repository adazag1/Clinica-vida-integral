package co.edu.ucentral.clinica.reportes;

import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.CategoriaDto;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.CostoDto;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.ParametroDto;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.TableroClinicoDto;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.TableroFinancieroDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/reportes")
@Tag(name = "Indicadores de directivos", description = "Tablero clínico del CEO y reporte financiero y de ROI del CFO")
public class ReportesController {

    static final String DIRECCION = "hasAnyRole('ADMIN','DIRECTIVO')";

    public record ParametroRequest(@NotBlank(message = "Ingrese el valor") @Size(max = 300) String valor) {
    }

    public record CostoRequest(
            @NotBlank(message = "Indique el mes") @Pattern(regexp = "\\d{4}-\\d{2}", message = "Use el formato AAAA-MM") String mes,
            @NotNull(message = "Seleccione la categoría") CostoOperativo.Categoria categoria,
            @NotNull(message = "Ingrese el valor") @DecimalMin(value = "0", message = "No puede ser negativo") BigDecimal valor,
            @Size(max = 300, message = "Máximo 300 caracteres") String nota) {
    }

    private final IndicadoresService service;

    public ReportesController(IndicadoresService service) {
        this.service = service;
    }

    @GetMapping("/clinico")
    @PreAuthorize(DIRECCION)
    @Operation(summary = "US 5.1: tiempo de diagnóstico de enfermedades críticas frente a la línea base y la meta")
    public TableroClinicoDto clinico() {
        return service.tableroClinico();
    }

    @GetMapping("/financiero")
    @PreAuthorize(DIRECCION)
    @Operation(summary = "US 5.2: costos operativos contra capacidad de atención, por mes, y proyección del ROI")
    public TableroFinancieroDto financiero() {
        return service.tableroFinanciero();
    }

    @GetMapping("/financiero.csv")
    @PreAuthorize(DIRECCION)
    @Operation(summary = "US 5.2: reporte mensual exportable (CSV para Excel)")
    public ResponseEntity<byte[]> financieroCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-financiero-" + LocalDate.now() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(service.csvFinanciero().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/parametros")
    @PreAuthorize(DIRECCION)
    public List<ParametroDto> parametros() {
        return service.parametros();
    }

    @PutMapping("/parametros/{clave}")
    @PreAuthorize(DIRECCION)
    @Operation(summary = "Cambia una meta o línea base; deja de marcarse como valor de prueba")
    public ParametroDto cambiarParametro(@PathVariable String clave, @Valid @RequestBody ParametroRequest request) {
        return service.cambiarParametro(clave, request.valor());
    }

    @GetMapping("/costos")
    @PreAuthorize(DIRECCION)
    public List<CostoDto> costos(@RequestParam String desde, @RequestParam String hasta) {
        return service.costos(YearMonth.parse(desde), YearMonth.parse(hasta));
    }

    @PutMapping("/costos")
    @PreAuthorize(DIRECCION)
    @Operation(summary = "Registra (o reemplaza) el costo de un mes en una categoría")
    public CostoDto registrarCosto(@Valid @RequestBody CostoRequest request) {
        return service.registrarCosto(YearMonth.parse(request.mes()), request.categoria(), request.valor(), request.nota());
    }

    @GetMapping("/costos/categorias")
    @PreAuthorize(DIRECCION)
    public List<CategoriaDto> categorias() {
        return service.categorias();
    }
}
