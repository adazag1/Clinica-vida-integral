package co.edu.ucentral.clinica.historia;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * RFMT-011: huella SHA-256 del contenido clínico firmado (quién, cuándo, notas, diagnósticos y
 * fórmula). Si alguien alterara la consulta en la base de datos, la huella dejaría de coincidir.
 *
 * <p>Es una firma electrónica (identidad confirmada con contraseña + integridad). Para la
 * "firma digital" con plena validez jurídica (Ley 527 de 1999) se necesita un certificado emitido
 * por una entidad de certificación abierta acreditada ante ONAC; esa integración queda pendiente.</p>
 */
@Component
public class FirmaClinica {

    public String huella(Consulta c) {
        String diagnosticos = c.getDiagnosticos().stream()
                .map(d -> d.getDiagnostico().getCodigo() + ":" + d.isPrincipal() + ":" + d.isConfirmado())
                .sorted()
                .collect(Collectors.joining(","));
        String formula = c.getPrescripciones().stream()
                .map(p -> p.getMedicamento().getNombre() + ":" + p.getDosis() + ":" + p.getFrecuencia() + ":"
                        + p.getDuracionDias() + ":" + nulo(p.getIndicaciones()))
                .sorted()
                .collect(Collectors.joining(","));
        String contenido = String.join("|", String.valueOf(c.getId()), String.valueOf(c.getPacienteId()), c.getMedico(),
                String.valueOf(c.getFirmadaEn()), nulo(c.getMotivoConsulta()), nulo(c.getEnfermedadActual()),
                nulo(c.getExamenFisico()), nulo(c.getPlan()), diagnosticos, formula);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(contenido.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String nulo(String s) {
        return s == null ? "" : s;
    }
}
