package co.edu.ucentral.clinica.agenda;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;

@Configuration
@EnableScheduling
public class AgendaConfig {

    @Bean
    public CalculadoraDisponibilidad calculadoraDisponibilidad(@Value("${app.zona-horaria}") String zona) {
        return new CalculadoraDisponibilidad(ZoneId.of(zona));
    }
}
