package com.promagroup.apibridge.dto;

import java.util.Map;

/**
 * Modelo config-driven de uma integracao (serializado em {@code integrations.config_json}).
 *
 * @param recordsPath JSONPath para a lista de registros na resposta (ex.: {@code $.data[*]})
 * @param businessKey JSONPath, relativo a cada registro, da chave estavel de deduplicacao (ex.: {@code $.id})
 * @param template    template da mensagem Telegram com placeholders {@code {campo}}
 * @param fields      mapa nome-do-placeholder -> JSONPath relativo ao registro (ex.: {@code title -> $.name})
 * @param pagination  configuracao opcional de paginacao; {@code null} = pagina unica
 * @param parseMode   modo de formatacao Telegram; {@code null} = HTML (padrao)
 */
public record IntegrationConfig(
        String recordsPath,
        String businessKey,
        String template,
        Map<String, String> fields,
        PaginationConfig pagination,
        ParseMode parseMode
) {

    /** Parse mode efetivo: HTML quando nao especificado. */
    public ParseMode effectiveParseMode() {
        return parseMode != null ? parseMode : ParseMode.HTML;
    }

    /**
     * Paginacao dirigida pela resposta: o cliente le {@code nextUrlPath} (JSONPath) de cada pagina
     * para descobrir a proxima URL (absoluta ou relativa a atual). Para quando ausente/vazia.
     *
     * @param nextUrlPath JSONPath para a URL da proxima pagina (ex.: {@code $.paging.next})
     * @param maxPages    limite de paginas por execucao (guarda contra loops); {@code null} ou <=0 => 1
     */
    public record PaginationConfig(String nextUrlPath, Integer maxPages) {
    }
}
