package co.edu.ucentral.clinica.patologia;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** Valores estructurados de macroscopía y microscopía (clave del campo → valor), guardados como JSON. */
@Converter
public class ValoresJson implements AttributeConverter<Map<String, String>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, String>> TIPO = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, String> valores) {
        return valores == null ? null : JSON.writeValueAsString(valores);
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String json) {
        return json == null ? null : JSON.readValue(json, TIPO);
    }
}
