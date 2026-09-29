package co.edu.ucentral.clinica.agenda;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RFAGE-010: el paciente abre el enlace del recordatorio y confirma o cancela sin iniciar sesión.
 * El token de 48 caracteres aleatorios es la única credencial; solo expone datos mínimos de la cita.
 */
@RestController
@RequestMapping("/api/publico/citas")
@Tag(name = "Confirmación de citas (paciente)", description = "Sin autenticación: acceso por enlace")
public class CitaPublicaController {

    private final CitaService service;

    public CitaPublicaController(CitaService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Datos básicos de la cita del enlace")
    public CitaService.CitaPublicaDto ver(@PathVariable String token) {
        return service.publica(token);
    }

    @PostMapping("/{token}/confirmar")
    public CitaService.CitaPublicaDto confirmar(@PathVariable String token) {
        return service.confirmarPorPaciente(token);
    }

    @PostMapping("/{token}/cancelar")
    public CitaService.CitaPublicaDto cancelar(@PathVariable String token) {
        return service.cancelarPorPaciente(token);
    }
}
