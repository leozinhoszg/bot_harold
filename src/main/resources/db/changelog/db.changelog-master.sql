--liquibase formatted sql

--changeset apibridge:1 splitStatements:true endDelimiter:;
-- Schema inicial do API Bridge Bot (SQLite).
-- Tipagem SQLite: BOOLEAN -> INTEGER (0/1); DATETIME -> TEXT ISO-8601.

-- Uma API externa monitorada. config_json guarda o modelo config-driven
-- (recordsPath, businessKey, template, fields) para onboarding sem mudar o core.
CREATE TABLE integrations (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL UNIQUE,
    url         TEXT    NOT NULL,
    auth_type   TEXT    NOT NULL DEFAULT 'NONE',
    secret_ref  TEXT,
    enabled     INTEGER NOT NULL DEFAULT 1,
    cron        TEXT    NOT NULL,
    chat_id     TEXT,
    config_json TEXT    NOT NULL,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- Fonte da verdade da deduplicacao: registros ja notificados por integracao.
CREATE TABLE seen_records (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    integration_id INTEGER NOT NULL,
    business_key   TEXT    NOT NULL,
    hash           TEXT    NOT NULL,
    created_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    CONSTRAINT fk_seen_integration FOREIGN KEY (integration_id) REFERENCES integrations (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_seen_integration_key ON seen_records (integration_id, business_key);

-- Historico de notificacoes enviadas ao Telegram.
CREATE TABLE notifications (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    integration_id      INTEGER NOT NULL,
    business_key        TEXT    NOT NULL,
    telegram_message_id TEXT,
    message             TEXT    NOT NULL,
    status              TEXT    NOT NULL DEFAULT 'PENDING',
    created_at          TEXT    NOT NULL DEFAULT (datetime('now')),
    CONSTRAINT fk_notif_integration FOREIGN KEY (integration_id) REFERENCES integrations (id) ON DELETE CASCADE
);

CREATE INDEX ix_notifications_integration ON notifications (integration_id);

-- Auditoria da resposta bruta da API (retencao configuravel; opcional).
CREATE TABLE api_history (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    integration_id INTEGER NOT NULL,
    http_code      INTEGER,
    payload        TEXT,
    created_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    CONSTRAINT fk_history_integration FOREIGN KEY (integration_id) REFERENCES integrations (id) ON DELETE CASCADE
);

CREATE INDEX ix_api_history_integration ON api_history (integration_id);

-- Log operacional persistido (o log estruturado principal vai para stdout/Logback).
CREATE TABLE logs (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    level      TEXT NOT NULL,
    message    TEXT NOT NULL,
    stacktrace TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
