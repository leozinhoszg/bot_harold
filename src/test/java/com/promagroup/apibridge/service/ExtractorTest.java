package com.promagroup.apibridge.service;

import com.promagroup.apibridge.dto.ExtractedRecord;
import com.promagroup.apibridge.dto.IntegrationConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractorTest {

    private final Extractor extractor = new Extractor();

    private static IntegrationConfig config(String recordsPath, String businessKey, Map<String, String> fields) {
        return new IntegrationConfig(recordsPath, businessKey, "template", fields, null, null);
    }

    @Test
    void extractsListWithBusinessKeyAndFields() {
        String payload = "{\"data\":[{\"id\":1,\"name\":\"A\",\"price\":10},{\"id\":2,\"name\":\"B\"}]}";
        IntegrationConfig config = config("$.data[*]", "$.id",
                Map.of("title", "$.name", "price", "$.price"));

        List<ExtractedRecord> records = extractor.extract(config, payload);

        assertThat(records).hasSize(2);
        assertThat(records.get(0).businessKey()).isEqualTo("1");
        assertThat(records.get(0).fields()).containsEntry("title", "A").containsEntry("price", "10");
        // campo ausente vira "" (registro 2 nao tem price)
        assertThat(records.get(1).businessKey()).isEqualTo("2");
        assertThat(records.get(1).fields()).containsEntry("title", "B").containsEntry("price", "");
    }

    @Test
    void skipsRecordsWithoutBusinessKey() {
        String payload = "{\"data\":[{\"name\":\"semId\"}]}";
        IntegrationConfig config = config("$.data[*]", "$.id", Map.of("title", "$.name"));

        assertThat(extractor.extract(config, payload)).isEmpty();
    }

    @Test
    void treatsWholePayloadAsSingleRecordWhenNoRecordsPath() {
        String payload = "{\"id\":5,\"name\":\"Z\"}";
        IntegrationConfig config = config(null, "$.id", Map.of("title", "$.name"));

        List<ExtractedRecord> records = extractor.extract(config, payload);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).businessKey()).isEqualTo("5");
        assertThat(records.get(0).fields()).containsEntry("title", "Z");
    }

    @Test
    void returnsEmptyForBlankPayloadOrMissingBusinessKeyConfig() {
        assertThat(extractor.extract(config("$.data[*]", "$.id", Map.of()), "")).isEmpty();
        assertThat(extractor.extract(config("$.data[*]", null, Map.of()), "{\"data\":[]}")).isEmpty();
    }
}
