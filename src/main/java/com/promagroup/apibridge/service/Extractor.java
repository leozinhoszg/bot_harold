package com.promagroup.apibridge.service;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.JsonPath;
import com.promagroup.apibridge.dto.ExtractedRecord;
import com.promagroup.apibridge.dto.IntegrationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforma o JSON cru de uma resposta em registros normalizados, dirigido pela config.
 *
 * <p>Usa {@code recordsPath} (JSONPath) para localizar a lista de registros e, para cada um,
 * resolve {@code businessKey} e cada {@code field} por JSONPath relativo. Registros sem
 * businessKey sao descartados (nao ha como deduplicar sem chave). Campos ausentes viram "".
 */
@Component
public class Extractor {

    private static final Logger log = LoggerFactory.getLogger(Extractor.class);

    // SUPPRESS_EXCEPTIONS + LEAF_TO_NULL: caminhos ausentes retornam null em vez de lancar.
    private final ParseContext parseContext = JsonPath.using(Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
            .build());

    public List<ExtractedRecord> extract(IntegrationConfig config, String payload) {
        if (config == null || payload == null || payload.isBlank()) {
            return List.of();
        }
        if (config.businessKey() == null || config.businessKey().isBlank()) {
            log.warn("Config sem businessKey; nenhum registro extraido");
            return List.of();
        }

        List<Object> rawRecords = readRecords(config, payload);
        Map<String, String> fieldPaths = config.fields() == null ? Map.of() : config.fields();

        List<ExtractedRecord> result = new ArrayList<>(rawRecords.size());
        for (Object raw : rawRecords) {
            var recordContext = parseContext.parse(raw);
            Object keyValue = recordContext.read(config.businessKey());
            if (keyValue == null) {
                log.debug("Registro sem businessKey ({}); descartado", config.businessKey());
                continue;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            for (Map.Entry<String, String> field : fieldPaths.entrySet()) {
                Object value = recordContext.read(field.getValue());
                fields.put(field.getKey(), value == null ? "" : String.valueOf(value));
            }
            result.add(new ExtractedRecord(String.valueOf(keyValue), fields));
        }
        return result;
    }

    private List<Object> readRecords(IntegrationConfig config, String payload) {
        var document = parseContext.parse(payload);
        // Sem recordsPath: o proprio payload e um unico registro.
        Object raw = (config.recordsPath() == null || config.recordsPath().isBlank())
                ? document.read("$")
                : document.read(config.recordsPath());
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of(raw);
    }
}
