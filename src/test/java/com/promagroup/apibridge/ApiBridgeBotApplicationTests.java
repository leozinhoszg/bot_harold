package com.promagroup.apibridge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Teste de fumaca da Fase 0: o contexto Spring sobe, o datasource SQLite conecta
 * e as migrations Flyway (V1) sao aplicadas com sucesso.
 */
@SpringBootTest
class ApiBridgeBotApplicationTests {

    @Test
    void contextLoads() {
        // Se o contexto carregar sem excecao, o bootstrap (datasource + JPA + Flyway) esta ok.
    }
}
