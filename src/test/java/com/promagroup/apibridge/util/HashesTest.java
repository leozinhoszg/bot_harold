package com.promagroup.apibridge.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HashesTest {

    @Test
    void sameContentProducesSameHashRegardlessOfFieldOrder() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("title", "X");
        a.put("price", "10");
        Map<String, String> b = new LinkedHashMap<>();
        b.put("price", "10");
        b.put("title", "X");

        assertThat(Hashes.contentHash("k1", a)).isEqualTo(Hashes.contentHash("k1", b));
    }

    @Test
    void differentContentProducesDifferentHash() {
        Map<String, String> fields = Map.of("title", "X");
        assertThat(Hashes.contentHash("k1", fields)).isNotEqualTo(Hashes.contentHash("k2", fields));
        assertThat(Hashes.contentHash("k1", Map.of("title", "X")))
                .isNotEqualTo(Hashes.contentHash("k1", Map.of("title", "Y")));
    }

    @Test
    void producesHexSha256() {
        assertThat(Hashes.contentHash("k", Map.of())).hasSize(64).matches("[0-9a-f]{64}");
    }
}
