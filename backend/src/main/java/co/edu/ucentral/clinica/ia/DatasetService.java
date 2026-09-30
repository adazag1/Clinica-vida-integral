package co.edu.ucentral.clinica.ia;

import co.edu.ucentral.clinica.comun.auditoria.AuditoriaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * US 4.1: dataset anonimizado para entrenar o validar modelos. Cada paciente aparece con un
 * seudónimo (HMAC con una sal secreta, así no se puede recalcular desde el id), su sexo, su grupo
 * de edad en el momento del evento y el día relativo a su primer evento. No incluye nombres,
 * documentos, fechas exactas ni texto libre (que podría contener identificadores).
 */
@Service
public class DatasetService {

    static final String ENCABEZADO = "paciente,sexo,grupo_edad,dia,tipo,codigo,valor,unidad,interpretacion";

    private record Fila(Long pacienteId, Instant fecha, String tipo, String codigo, String valor, String unidad, String interpretacion) {
    }

    private record Demografia(String sexo, LocalDate nacimiento) {
    }

    public record Exportacion(String csv, int filas, int pacientes) {
    }

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;
    private final AuditorAware<String> auditor;
    private final byte[] sal;

    public DatasetService(JdbcTemplate jdbc, AuditoriaService auditoria, AuditorAware<String> auditor,
                          @Value("${app.ia.dataset.sal:}") String sal, @Value("${app.jwt.secret:}") String jwtSecret) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.auditor = auditor;
        String base = sal == null || sal.isBlank() ? "dataset|" + jwtSecret : sal;
        this.sal = base.getBytes(StandardCharsets.UTF_8);
    }

    /** Sin transacción propia: son solo lecturas y la auditoría de la descarga debe poder escribirse. */
    public Exportacion exportar(String ip) {
        Map<Long, Demografia> demografia = new HashMap<>();
        jdbc.query("select id, sexo, fecha_nacimiento from paciente", rs -> {
            java.sql.Date nac = rs.getDate("fecha_nacimiento");
            demografia.put(rs.getLong("id"), new Demografia(rs.getString("sexo"), nac == null ? null : nac.toLocalDate()));
        });

        List<Fila> filas = new ArrayList<>();
        jdbc.query("""
                select c.paciente_id, c.inicio, d.codigo, cd.principal from consulta_diagnostico cd
                join consulta c on c.id = cd.consulta_id join diagnostico d on d.id = cd.diagnostico_id
                where c.estado = 'FIRMADA'
                """, rs -> {
            filas.add(new Fila(rs.getLong(1), instante(rs.getTimestamp(2)), "DX", rs.getString(3), rs.getBoolean(4) ? "principal" : "relacionado", null, null));
        });
        jdbc.query("""
                select o.paciente_id, o.validada_en, a.codigo, r.valor, a.unidad, r.interpretacion from resultado_lab r
                join orden_lab o on o.id = r.orden_id join analito a on a.id = r.analito_id
                where o.estado = 'VALIDADA'
                """, rs -> {
            filas.add(new Fila(rs.getLong(1), instante(rs.getTimestamp(2)), "LAB", rs.getString(3), numero(rs.getBigDecimal(4)),
                    rs.getString(5), rs.getString(6)));
        });
        jdbc.query("""
                select paciente_id, fecha, presion_sistolica, presion_diastolica, frecuencia_cardiaca, frecuencia_respiratoria,
                       temperatura, saturacion_oxigeno from signos_vitales
                """, rs -> {
            Long p = rs.getLong(1);
            Instant f = instante(rs.getTimestamp(2));
            String[][] signos = {{"PAS", "mmHg"}, {"PAD", "mmHg"}, {"FC", "lpm"}, {"FR", "rpm"}, {"TEMP", "°C"}, {"SPO2", "%"}};
            for (int i = 0; i < signos.length; i++) {
                BigDecimal v = rs.getBigDecimal(3 + i);
                if (v != null) filas.add(new Fila(p, f, "SIGNO", signos[i][0], numero(v), signos[i][1], null));
            }
        });
        jdbc.query("""
                select e.paciente_id, e.informado_en, t.codigo, e.critico from estudio_imagen e
                join tipo_estudio t on t.id = e.tipo_estudio_id where e.informado_en is not null
                """, rs -> {
            filas.add(new Fila(rs.getLong(1), instante(rs.getTimestamp(2)), "IMAGEN", rs.getString(3), null, null,
                    rs.getBoolean(4) ? "CRITICO" : null));
        });
        jdbc.query("select paciente_id, validado_en, tipo from caso_patologia where validado_en is not null", rs -> {
            filas.add(new Fila(rs.getLong(1), instante(rs.getTimestamp(2)), "PATOLOGIA", rs.getString(3), null, null, null));
        });

        Map<Long, Instant> primerEvento = new HashMap<>();
        filas.forEach(f -> primerEvento.merge(f.pacienteId(), f.fecha(), (a, b) -> a.isBefore(b) ? a : b));
        Map<Long, String> seudonimos = new HashMap<>();

        StringBuilder csv = new StringBuilder(ENCABEZADO).append('\n');
        filas.stream()
                .sorted(Comparator.comparing((Fila f) -> seudonimos.computeIfAbsent(f.pacienteId(), this::seudonimo))
                        .thenComparing(Fila::fecha).thenComparing(Fila::tipo).thenComparing(Fila::codigo))
                .forEach(f -> {
                    Demografia d = demografia.get(f.pacienteId());
                    Integer edad = d == null || d.nacimiento() == null ? null
                            : Period.between(d.nacimiento(), f.fecha().atOffset(ZoneOffset.UTC).toLocalDate()).getYears();
                    long dia = Duration.between(primerEvento.get(f.pacienteId()), f.fecha()).toDays();
                    csv.append(String.join(",", seudonimos.get(f.pacienteId()), campo(d == null ? null : d.sexo()),
                            AnonimizadorClinico.grupoEdad(edad), String.valueOf(dia), f.tipo(), campo(f.codigo()), campo(f.valor()),
                            campo(f.unidad()), campo(f.interpretacion()))).append('\n');
                });

        Exportacion e = new Exportacion(csv.toString(), filas.size(), primerEvento.size());
        auditoria.registrarEvento(auditor.getCurrentAuditor().orElse("sistema"), "IA_DATASET_EXPORTADO",
                e.filas() + " filas de " + e.pacientes() + " pacientes (anonimizado)", ip);
        return e;
    }

    /** 16 caracteres hexadecimales del HMAC-SHA256 del id: estable entre exportaciones, no reversible sin la sal. */
    String seudonimo(Long pacienteId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sal, "HmacSHA256"));
            return "P" + HexFormat.of().formatHex(mac.doFinal(String.valueOf(pacienteId).getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 no disponible", ex);
        }
    }

    private static Instant instante(Timestamp t) {
        return t.toInstant();
    }

    private static String numero(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    /** CSV RFC 4180: comillas si hay coma, comillas o salto de línea. */
    private static String campo(String v) {
        if (v == null) return "";
        return v.contains(",") || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
