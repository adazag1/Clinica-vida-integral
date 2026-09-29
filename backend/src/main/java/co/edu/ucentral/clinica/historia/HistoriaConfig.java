package co.edu.ucentral.clinica.historia;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EvaluadorSignos.Rangos.class)
public class HistoriaConfig {
}
