package co.edu.ucentral.clinica.comun.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorActual")
// Páginas serializadas como { content, page: {...} }: formato estable para el frontend
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class JpaAuditoriaConfig {

    /** Usuario autenticado que se guarda en creado_por / modificado_por. */
    @Bean
    public AuditorAware<String> auditorActual() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
                return Optional.of("sistema");
            }
            return Optional.of(auth.getName());
        };
    }
}
