package co.edu.ucentral.clinica.reportes;

import co.edu.ucentral.clinica.comun.excepciones.DatosInvalidosException;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.reportes.CalculadoraIndicadores.Proyeccion;
import co.edu.ucentral.clinica.reportes.CalculadoraIndicadores.Resumen;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.CasoReciente;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.CategoriaDto;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.ComponenteTiempo;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.CostoDto;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.DiagnosticoCritico;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.MesClinico;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.MesFinanciero;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.ParametroDto;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.RoiDto;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.TableroClinicoDto;
import co.edu.ucentral.clinica.reportes.dto.ReportesDtos.TableroFinancieroDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Épica 5: tablero clínico del CEO (tiempo de diagnóstico de enfermedades críticas, US 5.1) y reporte
 * financiero y operativo del CFO (costos contra capacidad de atención y ROI, US 5.2). Se calculan al
 * pedirlos con los datos que ya registran los módulos clínicos y de facturación.
 */
@Service
public class IndicadoresService {

    /** Series de más de 3 años no caben en el tablero; el ROI se mide a 36 meses. */
    private static final int MAX_MESES = 36;
    private static final int MAX_RECIENTES = 12;

    private final JdbcTemplate jdbc;
    private final ParametroIndicadorRepository parametros;
    private final CostoOperativoRepository costos;
    private final AuditorAware<String> auditor;
    private final ZoneId zona;

    public IndicadoresService(JdbcTemplate jdbc, ParametroIndicadorRepository parametros, CostoOperativoRepository costos,
                              AuditorAware<String> auditor, @Value("${app.zona-horaria:America/Bogota}") String zona) {
        this.jdbc = jdbc;
        this.parametros = parametros;
        this.costos = costos;
        this.auditor = auditor;
        this.zona = ZoneId.of(zona);
    }

    // ---------- Parámetros ----------

    @Transactional(readOnly = true)
    public List<ParametroDto> parametros() {
        return parametros.findAllByOrderByOrden().stream().map(ParametroDto::de).toList();
    }

    @Transactional
    public ParametroDto cambiarParametro(String clave, String valor) {
        ParametroIndicador p = parametros.findById(clave).orElseThrow(() -> new RecursoNoEncontradoException("Parámetro", clave));
        String v = valor == null ? "" : valor.trim();
        if (clave.equals("diagnostico.inicio")) {
            try {
                LocalDate.parse(v);
            } catch (DateTimeParseException e) {
                throw new DatosInvalidosException(Map.of("valor", "Use el formato AAAA-MM-DD"));
            }
        } else if (clave.equals("diagnostico.cie10_criticos")) {
            v = Arrays.stream(v.split(",")).map(String::trim).map(String::toUpperCase).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(","));
            if (v.isEmpty()) throw new DatosInvalidosException(Map.of("valor", "Indique al menos un prefijo CIE-10"));
        } else {
            BigDecimal n;
            try {
                n = new BigDecimal(v.replace(",", "."));
            } catch (NumberFormatException e) {
                throw new DatosInvalidosException(Map.of("valor", "Debe ser un número"));
            }
            if (n.signum() < 0) throw new DatosInvalidosException(Map.of("valor", "No puede ser negativo"));
            if (clave.endsWith("_pct") && n.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new DatosInvalidosException(Map.of("valor", "Un porcentaje no puede superar 100"));
            }
            if (clave.endsWith("plazo_meses") && (n.signum() == 0 || n.stripTrailingZeros().scale() > 0)) {
                throw new DatosInvalidosException(Map.of("valor", "Indique un número entero de meses"));
            }
            v = n.stripTrailingZeros().toPlainString();
        }
        p.cambiar(v, auditor.getCurrentAuditor().orElse("sistema"));
        return ParametroDto.de(p);
    }

    private Map<String, ParametroIndicador> mapaParametros() {
        return parametros.findAll().stream().collect(Collectors.toMap(ParametroIndicador::getClave, p -> p));
    }

    private static BigDecimal numero(Map<String, ParametroIndicador> p, String clave) {
        ParametroIndicador x = p.get(clave);
        return x == null || x.getValor().isBlank() ? null : new BigDecimal(x.getValor());
    }

    private static boolean dePrueba(Map<String, ParametroIndicador> p, String prefijo) {
        return p.values().stream().anyMatch(x -> x.getClave().startsWith(prefijo) && x.isPrueba());
    }

    private LocalDate inicio(Map<String, ParametroIndicador> p) {
        return LocalDate.parse(p.get("diagnostico.inicio").getValor());
    }

    // ---------- US 5.1: tiempo de diagnóstico de enfermedades críticas ----------

    private record Caso(Long consultaId, Instant inicio, Instant firmada, String codigo, String nombre, boolean desdeAdmision,
                        boolean principal) {
        double horas() {
            return Duration.between(inicio, firmada).toMinutes() / 60.0;
        }
    }

    /**
     * Un caso es una consulta firmada con un diagnóstico crítico confirmado. El tiempo va desde la llegada
     * del paciente (admisión o triage) hasta la firma de esa consulta; si llegó por cita, desde el inicio
     * de la consulta. Cuenta una vez por consulta, con su diagnóstico crítico principal.
     */
    @Transactional(readOnly = true)
    public TableroClinicoDto tableroClinico() {
        Map<String, ParametroIndicador> p = mapaParametros();
        BigDecimal base = numero(p, "diagnostico.linea_base_horas");
        BigDecimal reduccion = numero(p, "diagnostico.meta_reduccion_pct");
        int plazo = numero(p, "diagnostico.plazo_meses").intValue();
        LocalDate inicio = inicio(p);
        List<String> prefijos = Arrays.stream(p.get("diagnostico.cie10_criticos").getValor().split(",")).map(String::trim).toList();
        Instant ahora = Instant.now();
        YearMonth primerMes = YearMonth.from(inicio);
        YearMonth mesActual = YearMonth.now(zona);
        if (ChronoUnit.MONTHS.between(primerMes, mesActual) >= MAX_MESES) primerMes = mesActual.minusMonths(MAX_MESES - 1L);
        Instant desde = primerMes.atDay(1).atStartOfDay(zona).toInstant();

        Map<Long, Caso> porConsulta = new LinkedHashMap<>();
        jdbc.query("""
                select c.id, coalesce(e.fecha_ingreso, c.inicio), c.firmada_en, d.codigo, d.nombre, c.episodio_id is not null, cd.principal
                from consulta c join consulta_diagnostico cd on cd.consulta_id = c.id and cd.confirmado
                join diagnostico d on d.id = cd.diagnostico_id left join episodio e on e.id = c.episodio_id
                where c.estado = 'FIRMADA' and c.firmada_en >= ?
                order by c.firmada_en, cd.principal desc
                """, rs -> {
            String codigo = rs.getString(4);
            if (prefijos.stream().noneMatch(codigo::startsWith)) return;
            porConsulta.putIfAbsent(rs.getLong(1), new Caso(rs.getLong(1), instante(rs.getTimestamp(2)), instante(rs.getTimestamp(3)),
                    codigo, rs.getString(5), rs.getBoolean(6), rs.getBoolean(7)));
        }, Timestamp.from(desde));
        List<Caso> casos = porConsulta.values().stream().filter(c -> !c.firmada().isBefore(c.inicio())).toList();

        List<MesClinico> serie = new ArrayList<>();
        for (YearMonth m = primerMes; !m.isAfter(mesActual); m = m.plusMonths(1)) {
            YearMonth mes = m;
            Resumen r = CalculadoraIndicadores.resumir(casos.stream()
                    .filter(c -> YearMonth.from(c.firmada().atZone(zona)).equals(mes)).map(Caso::horas).toList());
            serie.add(new MesClinico(mes.toString(), r.casos(), r.promedio(), r.mediana()));
        }

        Instant hace30 = ahora.minus(Duration.ofDays(30));
        Resumen ultimos = CalculadoraIndicadores.resumir(casos.stream().filter(c -> c.firmada().isAfter(hace30)).map(Caso::horas).toList());
        BigDecimal metaHoras = CalculadoraIndicadores.meta(base, reduccion);
        BigDecimal variacion = CalculadoraIndicadores.variacionPct(ultimos.promedio(), base);
        String estado = ultimos.casos() == 0 || base == null ? "SIN_DATOS"
                : ultimos.promedio().compareTo(metaHoras) <= 0 ? "META_ALCANZADA"
                : ultimos.promedio().compareTo(base) < 0 ? "EN_PROGRESO" : "SIN_MEJORA";

        List<DiagnosticoCritico> porDx = casos.stream().collect(Collectors.groupingBy(Caso::codigo, LinkedHashMap::new, Collectors.toList()))
                .values().stream().map(l -> new DiagnosticoCritico(l.getFirst().codigo(), l.getFirst().nombre(), l.size(),
                        CalculadoraIndicadores.resumir(l.stream().map(Caso::horas).toList()).promedio()))
                .sorted(Comparator.comparingInt(DiagnosticoCritico::casos).reversed()).limit(10).toList();
        List<CasoReciente> recientes = casos.reversed().stream().limit(MAX_RECIENTES)
                .map(c -> new CasoReciente(c.firmada(), c.codigo(), c.nombre(), CalculadoraIndicadores.redondear(c.horas()), c.desdeAdmision()))
                .toList();

        int transcurridos = (int) Math.max(0, ChronoUnit.MONTHS.between(YearMonth.from(inicio), mesActual));
        return new TableroClinicoDto(base, reduccion, metaHoras, plazo, inicio.toString(), transcurridos, ultimos,
                variacion == null ? null : variacion.negate(), estado, serie, componentes(hace30), porDx, recientes, prefijos,
                dePrueba(p, "diagnostico."), ahora);
    }

    /** Las etapas que suman al tiempo de diagnóstico, en los últimos 30 días y para todos los pacientes. */
    private List<ComponenteTiempo> componentes(Instant desde) {
        Timestamp t = Timestamp.from(desde);
        return List.of(
                componente("Llegada → atención médica", "Desde la admisión hasta que el médico abre la consulta", """
                        select extract(epoch from (min(c.inicio) - e.fecha_ingreso)) / 3600 from episodio e
                        join consulta c on c.episodio_id = e.id where e.fecha_ingreso >= ? group by e.id, e.fecha_ingreso
                        """, t),
                componente("Laboratorio", "Desde la orden hasta el resultado validado", """
                        select extract(epoch from (validada_en - ordenada_en)) / 3600 from orden_lab
                        where estado = 'VALIDADA' and validada_en >= ?
                        """, t),
                componente("Imágenes diagnósticas", "Desde la orden hasta el informe firmado", """
                        select extract(epoch from (informado_en - ordenado_en)) / 3600 from estudio_imagen where informado_en >= ?
                        """, t),
                componente("Patología", "Desde que se recibe la muestra hasta el informe validado", """
                        select extract(epoch from (validado_en - recibido_en)) / 3600 from caso_patologia
                        where validado_en >= ? and recibido_en is not null
                        """, t));
    }

    private ComponenteTiempo componente(String nombre, String descripcion, String sql, Timestamp desde) {
        List<Double> horas = jdbc.query(sql, (rs, i) -> rs.getDouble(1), desde).stream().filter(h -> h >= 0).toList();
        Resumen r = CalculadoraIndicadores.resumir(horas);
        return new ComponenteTiempo(nombre, descripcion, r.casos(), r.promedio());
    }

    // ---------- US 5.2: costos contra capacidad de atención y ROI ----------

    @Transactional(readOnly = true)
    public TableroFinancieroDto tableroFinanciero() {
        Map<String, ParametroIndicador> p = mapaParametros();
        BigDecimal costosBase = numero(p, "costos.linea_base_mensual");
        BigDecimal costosReduccion = numero(p, "costos.meta_reduccion_pct");
        BigDecimal capacidadBase = numero(p, "capacidad.linea_base_atenciones_medico");
        BigDecimal capacidadAumento = numero(p, "capacidad.meta_aumento_pct");
        BigDecimal valorAtencion = numero(p, "roi.valor_atencion");
        BigDecimal margen = numero(p, "roi.margen_atencion_pct").movePointLeft(2);
        BigDecimal inversionUsd = numero(p, "roi.inversion_usd");
        BigDecimal inversion = inversionUsd.multiply(numero(p, "roi.trm"));
        int plazo = numero(p, "roi.plazo_meses").intValue();

        YearMonth primerMes = YearMonth.from(inicio(p));
        YearMonth mesActual = YearMonth.now(zona);
        if (ChronoUnit.MONTHS.between(primerMes, mesActual) >= MAX_MESES) primerMes = mesActual.minusMonths(MAX_MESES - 1L);
        Timestamp desde = Timestamp.from(primerMes.atDay(1).atStartOfDay(zona).toInstant());

        Map<YearMonth, List<CostoOperativo>> costosPorMes = costos
                .findByMesBetweenOrderByMesAscCategoriaAsc(primerMes.atDay(1), mesActual.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(c -> YearMonth.from(c.getMes())));
        Map<YearMonth, long[]> atenciones = new LinkedHashMap<>();
        jdbc.query("""
                select date_trunc('month', firmada_en at time zone ?)::date, count(*), count(distinct medico)
                from consulta where estado = 'FIRMADA' and firmada_en >= ? group by 1
                """, rs -> {
            atenciones.put(YearMonth.from(rs.getDate(1).toLocalDate()), new long[]{rs.getLong(2), rs.getLong(3)});
        }, zona.getId(), desde);
        Map<YearMonth, BigDecimal> facturado = sumaMensual(
                "select date_trunc('month', fecha_emision at time zone ?)::date, sum(total) from factura where fecha_emision >= ? group by 1",
                zona.getId(), desde);
        Map<YearMonth, BigDecimal> recaudado = sumaMensual(
                "select date_trunc('month', fecha)::date, sum(valor) from pago where fecha >= ? group by 1",
                new java.sql.Date(desde.getTime()));
        Map<YearMonth, long[]> cargos = new LinkedHashMap<>();
        jdbc.query("""
                select date_trunc('month', registrado_en at time zone ?)::date, count(*), count(origen_modulo)
                from cargo where estado <> 'ANULADO' and registrado_en >= ? group by 1
                """, rs -> {
            cargos.put(YearMonth.from(rs.getDate(1).toLocalDate()), new long[]{rs.getLong(2), rs.getLong(3)});
        }, zona.getId(), desde);

        List<MesFinanciero> meses = new ArrayList<>();
        List<BigDecimal> beneficios = new ArrayList<>();
        for (YearMonth m = primerMes; !m.isAfter(mesActual); m = m.plusMonths(1)) {
            List<CostoOperativo> delMes = costosPorMes.getOrDefault(m, List.of());
            boolean registrados = !delMes.isEmpty();
            BigDecimal admin = registrados ? suma(delMes, true) : null;
            BigDecimal plataforma = registrados ? suma(delMes, false) : null;
            BigDecimal ahorro = admin == null || costosBase == null ? null : costosBase.subtract(admin);
            long[] at = atenciones.getOrDefault(m, new long[2]);
            BigDecimal apm = at[1] == 0 ? null : BigDecimal.valueOf(at[0]).divide(BigDecimal.valueOf(at[1]), 1, RoundingMode.HALF_UP);
            BigDecimal beneficioCapacidad = BigDecimal.ZERO;
            if (at[1] > 0 && capacidadBase != null) {
                BigDecimal adicionales = BigDecimal.valueOf(at[0]).subtract(capacidadBase.multiply(BigDecimal.valueOf(at[1])));
                if (adicionales.signum() > 0) beneficioCapacidad = adicionales.multiply(valorAtencion).multiply(margen).setScale(0, RoundingMode.HALF_UP);
            }
            BigDecimal neto = registrados ? (ahorro == null ? BigDecimal.ZERO : ahorro).add(beneficioCapacidad).subtract(plataforma) : null;
            long[] cg = cargos.get(m);
            meses.add(new MesFinanciero(m.toString(), registrados, admin, plataforma, ahorro,
                    CalculadoraIndicadores.variacionPct(admin, costosBase), at[0], at[1], apm,
                    CalculadoraIndicadores.variacionPct(apm, capacidadBase), facturado.getOrDefault(m, BigDecimal.ZERO),
                    recaudado.getOrDefault(m, BigDecimal.ZERO),
                    cg == null || cg[0] == 0 ? null : BigDecimal.valueOf(cg[1] * 100.0 / cg[0]).setScale(1, RoundingMode.HALF_UP),
                    beneficioCapacidad, neto));
            beneficios.add(neto);
        }

        Proyeccion pr = CalculadoraIndicadores.proyectar(inversion, plazo, beneficios);
        RoiDto roi = new RoiDto(pr.inversion(), inversionUsd, pr.acumulado(), pr.promedioReciente(), pr.mesesTranscurridos(),
                pr.plazoMeses(), pr.acumuladoAlPlazo(), pr.roiAlPlazoPct(), pr.mesRecuperacion(), pr.positivoEnPlazo(), pr.curva());
        return new TableroFinancieroDto(costosBase, CalculadoraIndicadores.meta(costosBase, costosReduccion), costosReduccion,
                capacidadBase, CalculadoraIndicadores.meta(capacidadBase, capacidadAumento.negate()), capacidadAumento, meses, roi,
                dePrueba(p, "costos.") || dePrueba(p, "capacidad.") || dePrueba(p, "roi."), Instant.now());
    }

    private Map<YearMonth, BigDecimal> sumaMensual(String sql, Object... args) {
        Map<YearMonth, BigDecimal> r = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            r.put(YearMonth.from(rs.getDate(1).toLocalDate()), rs.getBigDecimal(2));
        }, args);
        return r;
    }

    private static BigDecimal suma(List<CostoOperativo> lista, boolean administrativa) {
        return lista.stream().filter(c -> c.getCategoria().isAdministrativa() == administrativa)
                .map(CostoOperativo::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** US 5.2: reporte mensual exportable (CSV con separador ";" y BOM, para que Excel en español lo abra bien). */
    @Transactional(readOnly = true)
    public String csvFinanciero() {
        TableroFinancieroDto t = tableroFinanciero();
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Mes;Costo administrativo;Costo plataforma;Ahorro vs línea base;Variación costos %;Atenciones;Médicos;")
                .append("Atenciones por médico;Variación capacidad %;Facturado;Recaudado;Cargos automáticos %;")
                .append("Beneficio por capacidad;Beneficio neto\n");
        for (MesFinanciero m : t.meses()) {
            sb.append(String.join(";", m.mes(), n(m.costoAdministrativo()), n(m.costoPlataforma()), n(m.ahorro()),
                    n(m.variacionCostosPct()), String.valueOf(m.atenciones()), String.valueOf(m.medicos()), n(m.atencionesPorMedico()),
                    n(m.variacionCapacidadPct()), n(m.facturado()), n(m.recaudado()), n(m.cargosAutomaticosPct()),
                    n(m.beneficioCapacidad()), n(m.beneficioNeto()))).append('\n');
        }
        RoiDto r = t.roi();
        sb.append('\n').append("Inversión (COP);").append(n(r.inversion())).append('\n')
                .append("Beneficio neto acumulado;").append(n(r.acumulado())).append('\n')
                .append("Beneficio acumulado proyectado a ").append(r.plazoMeses()).append(" meses;").append(n(r.acumuladoAlPlazo())).append('\n')
                .append("ROI proyectado %;").append(n(r.roiAlPlazoPct())).append('\n')
                .append("Mes estimado de recuperación;").append(r.mesRecuperacion() == null ? "" : r.mesRecuperacion()).append('\n');
        if (t.parametrosDePrueba()) sb.append("\nAdvertencia;Algunas líneas base o metas son valores de prueba\n");
        return sb.toString();
    }

    /** Número con coma decimal (configuración regional de Colombia). */
    private static String n(Object v) {
        if (v == null) return "";
        return v instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString().replace('.', ',') : v.toString();
    }

    // ---------- Registro de costos ----------

    @Transactional(readOnly = true)
    public List<CostoDto> costos(YearMonth desde, YearMonth hasta) {
        return costos.findByMesBetweenOrderByMesAscCategoriaAsc(desde.atDay(1), hasta.atEndOfMonth()).stream().map(CostoDto::de).toList();
    }

    @Transactional
    public CostoDto registrarCosto(YearMonth mes, CostoOperativo.Categoria categoria, BigDecimal valor, String nota) {
        if (mes.isAfter(YearMonth.now(zona))) {
            throw new DatosInvalidosException(Map.of("mes", "No se registran costos de meses futuros"));
        }
        CostoOperativo c = costos.findByMesAndCategoria(mes.atDay(1), categoria)
                .orElseGet(() -> new CostoOperativo(mes.atDay(1), categoria));
        c.registrar(valor, nota, auditor.getCurrentAuditor().orElse("sistema"));
        return CostoDto.de(costos.save(c));
    }

    public List<CategoriaDto> categorias() {
        return Arrays.stream(CostoOperativo.Categoria.values())
                .map(c -> new CategoriaDto(c.name(), c.getDescripcion(), c.isAdministrativa())).toList();
    }

    private static Instant instante(Timestamp t) {
        return t.toInstant();
    }
}
