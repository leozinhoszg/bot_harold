package com.promagroup.apibridge.telegram;

import com.promagroup.apibridge.dto.ParseMode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FormatterTest {

    private final Formatter formatter = new Formatter();

    @Test
    void rendersPlaceholdersWithValues() {
        String result = formatter.render("<b>{title}</b> por {price}",
                Map.of("title", "Camisa", "price", "99"), ParseMode.HTML);
        assertThat(result).isEqualTo("<b>Camisa</b> por 99");
    }

    @Test
    void htmlEscapesOnlyValuesNotTemplateMarkup() {
        // O valor tem < e &; a marcacao <b> do template permanece intacta.
        String result = formatter.render("<b>{title}</b>",
                Map.of("title", "A & B <script>"), ParseMode.HTML);
        assertThat(result).isEqualTo("<b>A &amp; B &lt;script&gt;</b>");
    }

    @Test
    void markdownV2EscapesReservedCharsInValues() {
        String result = formatter.render("*{title}*",
                Map.of("title", "Preco-2.0 (novo!)"), ParseMode.MARKDOWN_V2);
        // - . ( ) ! sao reservados no MarkdownV2 e ganham barra invertida
        assertThat(result).isEqualTo("*Preco\\-2\\.0 \\(novo\\!\\)*");
    }

    @Test
    void unknownPlaceholderIsLeftLiteral() {
        String result = formatter.render("{title} {missing}",
                Map.of("title", "X"), ParseMode.HTML);
        assertThat(result).isEqualTo("X {missing}");
    }

    @Test
    void nullTemplateYieldsEmpty() {
        assertThat(formatter.render(null, Map.of(), ParseMode.HTML)).isEmpty();
    }

    @Test
    void defaultsToHtmlWhenModeNull() {
        String result = formatter.render("{v}", Map.of("v", "a<b"), null);
        assertThat(result).isEqualTo("a&lt;b");
    }
}
