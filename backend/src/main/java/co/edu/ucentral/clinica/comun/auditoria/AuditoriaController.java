package co.edu.ucentral.clinica.comun.auditoria;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
@Tag(name = "Auditoría", description = "Consulta del registro de accesos y acciones")
public class AuditoriaController {

    private final AuditoriaRepository repository;

    public AuditoriaController(AuditoriaRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUDITORIA')")
    @Operation(summary = "Lista el registro de auditoría, del más reciente al más antiguo")
    public Page<Auditoria> listar(@RequestParam(required = false) String usuario,
                                  @RequestParam(defaultValue = "0") int pagina,
                                  @RequestParam(defaultValue = "50") int tamano) {
        PageRequest page = PageRequest.of(pagina, Math.min(tamano, 200));
        return usuario == null || usuario.isBlank()
                ? repository.findAllByOrderByFechaDesc(page)
                : repository.findByUsuarioOrderByFechaDesc(usuario, page);
    }
}
