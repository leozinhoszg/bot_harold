package com.promagroup.apibridge.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/** Utilitario de hashing determinístico para deduplicacao. */
public final class Hashes {

    private Hashes() {
    }

    /**
     * Hash SHA-256 (hex) do conteudo de um registro. Independe da ordem dos campos:
     * a chave e os campos sao canonicalizados antes do digest, para que o mesmo conteudo
     * produza sempre o mesmo hash.
     */
    public static String contentHash(String businessKey, Map<String, String> fields) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(businessKey == null ? "" : businessKey).append('|');
        Map<String, String> sorted = fields == null ? Map.of() : new TreeMap<>(fields);
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            canonical.append(entry.getKey()).append('=')
                    .append(entry.getValue() == null ? "" : entry.getValue()).append(';');
        }
        return sha256Hex(canonical.toString());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit((b & 0xF), 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
