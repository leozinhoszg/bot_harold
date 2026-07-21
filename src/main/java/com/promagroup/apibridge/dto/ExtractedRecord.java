package com.promagroup.apibridge.dto;

import java.util.Map;

/**
 * Um registro extraido de uma resposta de API pelo {@code Extractor}.
 *
 * @param businessKey chave estavel de deduplicacao (resolvida via {@code businessKey} da config)
 * @param fields      valores dos placeholders do template (nome -> valor), ja como String
 */
public record ExtractedRecord(String businessKey, Map<String, String> fields) {
}
