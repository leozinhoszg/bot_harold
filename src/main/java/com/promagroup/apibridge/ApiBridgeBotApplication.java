package com.promagroup.apibridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;

/**
 * Ponto de entrada do API Bridge Bot.
 *
 * <p>O servico faz polling de APIs externas, detecta registros novos (por chave de
 * negocio) e publica notificacoes formatadas no Telegram. O agendamento e dinamico
 * por integracao; {@link EnableScheduling} habilita a infraestrutura de scheduling
 * do Spring usada pelo scheduler dinamico (Fase 5).
 */
@SpringBootApplication
@EnableScheduling
public class ApiBridgeBotApplication {

    public static void main(String[] args) {
        // SQLite (org.xerial) cria o arquivo do banco, mas nao o diretorio pai.
        // Garante data/ antes do Hikari abrir a conexao. Phase 1 pode tornar isso config-driven.
        new File("data").mkdirs();
        SpringApplication.run(ApiBridgeBotApplication.class, args);
    }
}
