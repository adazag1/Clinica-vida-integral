package co.edu.ucentral.clinica.comun.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Huella SHA-256 en hexadecimal: si el contenido cambia, la huella deja de coincidir. */
public final class Huella {

    private Huella() {
    }

    public static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Une las partes con "|" (los nulos cuentan como vacío) y calcula la huella. */
    public static String de(Object... partes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(partes[i] == null ? "" : partes[i]);
        }
        return sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
