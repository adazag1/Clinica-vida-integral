package co.edu.ucentral.clinica.comun.auditoria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final AuditoriaRepository repository;

    public AuditoriaService(AuditoriaRepository repository) {
        this.repository = repository;
    }

    /**
     * Guarda el registro en su propia transacción: si la operación de negocio falla,
     * el intento igual queda auditado; y si la auditoría falla, no tumba la operación.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String usuario, String accion, String recurso, String metodoHttp,
                          Integer estadoHttp, String ip, String detalle) {
        try {
            repository.save(new Auditoria(usuario, accion, recurso, metodoHttp, estadoHttp, ip, detalle));
        } catch (RuntimeException e) {
            log.error("No se pudo registrar auditoría de {} {}", accion, recurso, e);
        }
    }

    public void registrarEvento(String usuario, String accion, String detalle, String ip) {
        registrar(usuario, accion, "evento", null, null, ip, detalle);
    }
}
