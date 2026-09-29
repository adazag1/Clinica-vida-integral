package co.edu.ucentral.clinica.comun.auditoria;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Registra cada acceso autenticado a la API: quién, qué recurso, cuándo y con qué resultado
 * (RNFMT-007 / RNFGAC-006 / Ley 1581: trazabilidad de consultas a la historia clínica).
 */
@Component
public class FiltroAuditoria extends OncePerRequestFilter {

    private final AuditoriaService auditoriaService;

    public FiltroAuditoria(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // El login se audita en AuthService con más detalle (éxito / fallo / bloqueo).
        return !uri.startsWith("/api/") || uri.startsWith("/api/auth/login")
                || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                String recurso = request.getRequestURI()
                        + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
                auditoriaService.registrar(auth.getName(), accionPara(request.getMethod()), recurso,
                        request.getMethod(), response.getStatus(), request.getRemoteAddr(), null);
            }
        }
    }

    private static String accionPara(String metodo) {
        return switch (metodo) {
            case "GET" -> "CONSULTA";
            case "POST" -> "CREACION";
            case "PUT", "PATCH" -> "MODIFICACION";
            case "DELETE" -> "ELIMINACION";
            default -> metodo;
        };
    }
}
