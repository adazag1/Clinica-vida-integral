package co.edu.ucentral.clinica.ia.asistente;

import co.edu.ucentral.clinica.comun.excepciones.ReglaNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Azure OpenAI (chat completions). Apagado por defecto: se enciende con IA_LLM_HABILITADO=true
 * y las variables AZURE_OPENAI_*. Temperatura baja para respuestas conservadoras y reproducibles.
 */
@Component
public class AzureOpenAiAsistente implements AsistenteLenguaje {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final boolean habilitado;
    private final String endpoint;
    private final String deployment;
    private final String apiKey;
    private final String apiVersion;
    private final int maxTokens;
    private final Duration timeout;
    private final HttpClient http;

    public AzureOpenAiAsistente(@Value("${app.ia.llm.habilitado:false}") boolean habilitado,
                                @Value("${app.ia.llm.endpoint:}") String endpoint,
                                @Value("${app.ia.llm.deployment:}") String deployment,
                                @Value("${app.ia.llm.api-key:}") String apiKey,
                                @Value("${app.ia.llm.api-version:2024-10-21}") String apiVersion,
                                @Value("${app.ia.llm.max-tokens:700}") int maxTokens,
                                @Value("${app.ia.llm.timeout-segundos:40}") int timeoutSegundos) {
        this.habilitado = habilitado;
        this.endpoint = endpoint == null ? "" : endpoint.replaceAll("/+$", "");
        this.deployment = deployment;
        this.apiKey = apiKey;
        this.apiVersion = apiVersion;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(timeoutSegundos);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public boolean habilitado() {
        return habilitado;
    }

    @Override
    public boolean configurado() {
        return !endpoint.isBlank() && deployment != null && !deployment.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean disponible() {
        return habilitado && configurado();
    }

    @Override
    public String modelo() {
        return deployment == null || deployment.isBlank() ? null : "azure-openai/" + deployment;
    }

    @Override
    public Respuesta completar(String instrucciones, String contenido) {
        String url = endpoint + "/openai/deployments/" + URLEncoder.encode(deployment, StandardCharsets.UTF_8)
                + "/chat/completions?api-version=" + URLEncoder.encode(apiVersion, StandardCharsets.UTF_8);
        Map<String, Object> cuerpo = Map.of(
                "messages", List.of(Map.of("role", "system", "content", instrucciones), Map.of("role", "user", "content", contenido)),
                "temperature", 0.2,
                "max_tokens", maxTokens);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Content-Type", "application/json").header("api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo))).build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ReglaNegocioException("IA_NO_DISPONIBLE", "No se pudo contactar el servicio de Azure OpenAI: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReglaNegocioException("IA_NO_DISPONIBLE", "La consulta al asistente se interrumpió.");
        }
        if (respuesta.statusCode() / 100 != 2) {
            throw new ReglaNegocioException("IA_NO_DISPONIBLE", "Azure OpenAI respondió " + respuesta.statusCode()
                    + (respuesta.statusCode() == 429 ? " (límite de uso alcanzado; intente en un momento)." : "."));
        }
        JsonNode json = JSON.readTree(respuesta.body());
        String texto = json.path("choices").path(0).path("message").path("content").asString("");
        JsonNode uso = json.path("usage");
        return new Respuesta(texto.trim(), json.path("model").asString(modelo()),
                uso.has("prompt_tokens") ? uso.get("prompt_tokens").asInt() : null,
                uso.has("completion_tokens") ? uso.get("completion_tokens").asInt() : null);
    }
}
