package co.edu.ucentral.clinica.catalogos;

import co.edu.ucentral.clinica.comun.excepciones.RecursoNoEncontradoException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** RFAU-005: control de versiones de los catálogos de diagnósticos y de servicios. */
@Service
public class CatalogoService {

    public static final String DIAGNOSTICOS = "DIAGNOSTICOS";
    public static final String SERVICIOS = "SERVICIOS";

    public record VersionDto(String catalogo, int version, Instant actualizadoEn, String actualizadoPor) {
    }

    public record CambioDto(int version, String codigo, String accion, String detalle, String usuario, Instant fecha) {
    }

    private final CatalogoVersionRepository versiones;
    private final CatalogoCambioRepository cambios;
    private final AuditorAware<String> auditor;

    public CatalogoService(CatalogoVersionRepository versiones, CatalogoCambioRepository cambios, AuditorAware<String> auditor) {
        this.versiones = versiones;
        this.cambios = cambios;
        this.auditor = auditor;
    }

    /** Registra el cambio con la nueva versión del catálogo; debe llamarse dentro de la transacción del cambio. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int registrarCambio(String catalogo, String codigo, String accion, String detalle) {
        String usuario = auditor.getCurrentAuditor().orElse("sistema");
        CatalogoVersion v = versiones.paraActualizar(catalogo).orElseThrow(() -> new RecursoNoEncontradoException("Catálogo", catalogo));
        int version = v.subir(usuario);
        cambios.save(new CatalogoCambio(catalogo, version, codigo, accion, detalle, usuario));
        return version;
    }

    @Transactional(readOnly = true)
    public VersionDto version(String catalogo) {
        CatalogoVersion v = versiones.findById(catalogo).orElseThrow(() -> new RecursoNoEncontradoException("Catálogo", catalogo));
        return new VersionDto(v.getCatalogo(), v.getVersion(), v.getActualizadoEn(), v.getActualizadoPor());
    }

    @Transactional(readOnly = true)
    public List<CambioDto> historial(String catalogo) {
        return cambios.findTop100ByCatalogoOrderByFechaDescIdDesc(catalogo).stream()
                .map(c -> new CambioDto(c.getVersion(), c.getCodigo(), c.getAccion(), c.getDetalle(), c.getUsuario(), c.getFecha()))
                .toList();
    }
}
