package co.edu.ucentral.clinica.archivos;

import co.edu.ucentral.clinica.comun.excepciones.DatosInvalidosException;
import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import co.edu.ucentral.clinica.comun.seguridad.Huella;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Guarda archivos en la base de datos (quedan dentro del mismo respaldo) con su huella SHA-256.
 * El tipo se detecta por el contenido, no por la extensión ni por lo que declara el navegador:
 * un ejecutable renombrado a .pdf se rechaza.
 */
@Service
public class AlmacenArchivos {

    public enum Tipo {
        PDF("application/pdf"),
        JPEG("image/jpeg"),
        PNG("image/png"),
        DICOM("application/dicom");

        private final String mime;

        Tipo(String mime) {
            this.mime = mime;
        }

        public String getMime() {
            return mime;
        }
    }

    public static final Set<Tipo> IMAGENES = Set.of(Tipo.JPEG, Tipo.PNG, Tipo.DICOM);
    public static final Set<Tipo> DOCUMENTOS = Set.of(Tipo.PDF, Tipo.JPEG, Tipo.PNG);

    private final ArchivoRepository archivos;
    private final long maxBytes;

    public AlmacenArchivos(ArchivoRepository archivos, @Value("${app.archivos.max-mb:10}") int maxMb) {
        this.archivos = archivos;
        this.maxBytes = maxMb * 1024L * 1024L;
    }

    @Transactional
    public Archivo guardar(MultipartFile archivo, Set<Tipo> permitidos, String usuario) {
        if (archivo == null || archivo.isEmpty()) {
            throw new DatosInvalidosException(Map.of("archivo", "Seleccione un archivo"));
        }
        if (archivo.getSize() > maxBytes) {
            throw new DatosInvalidosException(Map.of("archivo", "El archivo supera el máximo de " + maxBytes / (1024 * 1024) + " MB"));
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new ReglaNegocioException("ARCHIVO_ILEGIBLE", "No fue posible leer el archivo.");
        }
        Tipo tipo = detectar(bytes);
        if (tipo == null || !permitidos.contains(tipo)) {
            throw new DatosInvalidosException(Map.of("archivo", "Formato no permitido. Se aceptan: "
                    + String.join(", ", permitidos.stream().map(Enum::name).sorted().toList())));
        }
        String nombre = nombreSeguro(archivo.getOriginalFilename());
        Archivo a = archivos.save(new Archivo(nombre, tipo.getMime(), bytes.length, Huella.sha256(bytes), usuario));
        archivos.guardarContenido(a.getId(), bytes);
        return a;
    }

    /** Contenido del archivo; falla si ya fue eliminado según la política de retención. */
    @Transactional(readOnly = true)
    public byte[] contenido(Long id) {
        byte[] bytes = archivos.contenido(id);
        if (bytes == null) {
            throw new RecursoNoEncontradoException("Contenido del archivo", id);
        }
        return bytes;
    }

    @Transactional
    public void purgar(Collection<Long> ids) {
        if (!ids.isEmpty()) archivos.purgar(ids);
    }

    /** Firma de los primeros bytes de cada formato permitido. */
    static Tipo detectar(byte[] b) {
        if (empieza(b, 0, 0x25, 0x50, 0x44, 0x46)) return Tipo.PDF;                 // %PDF
        if (empieza(b, 0, 0xFF, 0xD8, 0xFF)) return Tipo.JPEG;
        if (empieza(b, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return Tipo.PNG;
        if (empieza(b, 128, 'D', 'I', 'C', 'M')) return Tipo.DICOM;                 // preámbulo de 128 bytes
        return null;
    }

    private static boolean empieza(byte[] b, int desde, int... firma) {
        if (b.length < desde + firma.length) return false;
        for (int i = 0; i < firma.length; i++) {
            if ((b[desde + i] & 0xFF) != firma[i]) return false;
        }
        return true;
    }

    /** Sin rutas ni caracteres de control; el nombre solo se usa para mostrar y descargar. */
    static String nombreSeguro(String original) {
        String n = original == null ? "archivo" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (n.isEmpty()) n = "archivo";
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    public static boolean esImagenVisible(String mime) {
        return Arrays.asList(Tipo.JPEG.mime, Tipo.PNG.mime).contains(mime);
    }
}
