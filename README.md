# API Bridge Bot

Serviço de integração que faz **polling de APIs externas**, detecta **registros novos** e publica
**notificações formatadas no Telegram**. A arquitetura é **config-driven**: novas APIs são
adicionadas por configuração (JSONPath + template), sem alterar o código do core.

- **Stack:** Java 17 · Spring Boot 3 · SQLite (Liquibase) · Redis (cache opcional) · WebClient · Telegram Bot API
- **Build:** Maven (via wrapper `./mvnw`)

---

## Índice
- [Arquitetura](#arquitetura)
- [Pré-requisitos](#pré-requisitos)
- [Configuração (variáveis de ambiente)](#configuração-variáveis-de-ambiente)
- [Como rodar](#como-rodar)
- [Onboarding de uma nova API](#onboarding-de-uma-nova-api)
- [Banco de dados](#banco-de-dados)
- [Observabilidade](#observabilidade)
- [Testes](#testes)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Segurança](#segurança)
- [Roadmap](#roadmap)

---

## Arquitetura

```
Scheduler dinâmico (CronTrigger por integração)
        │
        ▼
Lock em memória (por integração)
        │
        ▼
API Client ── auth + timeout + retry/backoff + paginação ──► JSON
        │
        ▼
Extractor (JSONPath) ──► registros normalizados
        │
        ▼
Detector ── dedup: Redis (cache) → SQLite (fonte da verdade)
        │
        ▼  (só registros novos)
Formatter (template + escaping) ──► Telegram Publisher ──► Telegram Bot API
        │
        ▼
Persiste Notification (SENT/FAILED) + marca visto (seen_records) APÓS envio OK
```

Princípios: o **SQLite é a fonte da verdade** da deduplicação (o Redis é só cache e degrada sem
derrubar o polling); a marcação de "visto" ocorre **após** o envio confirmado, então uma falha de
envio é re-tentada no próximo ciclo.

---

## Pré-requisitos

- **JDK 17** (o projeto usa Java 17; migração para 21 é trivial — trocar `<java.version>` no `pom.xml`).
- **Maven** não precisa estar instalado — use o wrapper (`.\mvnw.cmd` no Windows, `./mvnw` no Unix).
- **Redis** é **opcional** (o app degrada para SQLite se ausente).
- **Docker** opcional (para Redis local e para buildar a imagem).
- Um **bot do Telegram** criado no [@BotFather](https://t.me/BotFather) (veja abaixo).

### Criar o bot no BotFather
1. Fale com **@BotFather** → `/newbot` → escolha nome e username (termina em `bot`).
2. Guarde o **token** retornado.
3. Descubra seu `chat_id`: envie uma mensagem ao seu bot e acesse
   `https://api.telegram.org/bot<TOKEN>/getUpdates` — o campo `chat.id` é o seu destino.

---

## Configuração (variáveis de ambiente)

**Nada é hardcoded**: toda configuração variável vem de env, com defaults em
[`application.yml`](src/main/resources/application.yml). Copie o template e preencha:

```bash
cp .env.example .env      # depois edite o .env (é ignorado pelo git)
```

| Variável | Default | Descrição |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | *(vazio)* | **Segredo.** Token do BotFather. |
| `TELEGRAM_DEFAULT_CHAT` | *(vazio)* | Chat destino padrão (usado quando a integração não define `chatId`). |
| `TELEGRAM_BASE_URL` | `https://api.telegram.org` | Base da Bot API (troque só para testes com mock). |
| `TELEGRAM_TIMEOUT` | `3s` | Timeout por tentativa de envio. |
| `TELEGRAM_RETRY_MAX_ATTEMPTS` | `2` | Retries (5xx/429/timeout). |
| `TELEGRAM_RETRY_BACKOFF` | `300ms` | Backoff inicial (exponencial). |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Cache de dedup (opcional). |
| `DEDUP_TTL` | `24h` | TTL das chaves de dedup no Redis. |
| `DATABASE_URL` | `jdbc:sqlite:data/database.db` | JDBC do SQLite. |
| `APP_SCHEDULER_ENABLED` | `true` | Liga/desliga o agendamento. |
| `POLLING_THREADS` | `5` | Tamanho do pool do scheduler. |
| `ROOT_LOG_LEVEL` / `LOG_LEVEL` | `INFO` / `DEBUG` | Nível de log (root / app). |

> ⚠️ **Nunca comite segredos.** O `.env` está no `.gitignore`; o token só vive no seu ambiente.

---

## Como rodar

### Local (com script que carrega o `.env`)

```powershell
# Windows / PowerShell
.\scripts\run-local.ps1
```
```bash
# Linux / macOS
./scripts/run-local.sh
```

Os scripts carregam o `.env` no ambiente do processo e sobem o app no perfil `local`
([`application-local.yml`](src/main/resources/application-local.yml)), que já inclui uma integração
de exemplo (JSONPlaceholder). Na 1ª execução chegam 2 mensagens no seu Telegram; nas seguintes o
dedup silencia (comportamento correto). Para um demo "limpo", apague `data/database.db` antes.

### Local (sem script)

```powershell
$env:TELEGRAM_BOT_TOKEN = "<token>"
$env:TELEGRAM_DEFAULT_CHAT = "<chat_id>"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

### Jar

```bash
./mvnw clean package
java -jar target/api-bridge-bot-*.jar --spring.profiles.active=local
```

### Docker

```bash
docker build -t api-bridge-bot .
docker run --rm \
  -e TELEGRAM_BOT_TOKEN=<token> \
  -e TELEGRAM_DEFAULT_CHAT=<chat_id> \
  -e REDIS_HOST=host.docker.internal \
  -v "$(pwd)/data:/app/data" \
  api-bridge-bot
```
Ou com `--env-file .env`. A imagem roda como usuário não-root e persiste o SQLite no volume `/app/data`.

---

## Onboarding de uma nova API

Uma integração é declarada por **configuração**, sem tocar no core. Adicione a
`app.seed.integrations` (por profile/YAML) ou insira direto na tabela `integrations`.

```yaml
app:
  seed:
    integrations:
      - name: minha-api
        url: "https://api.exemplo.com/v1/items"
        enabled: true
        cron: "*/30 * * * * *"          # cron de 6 campos (Spring)
        authType: BEARER                # NONE | BEARER | API_KEY | BASIC | (OAUTH2 = futuro)
        secretRef: "env:MINHA_API_TOKEN"  # referência a env var (nunca o segredo cru)
        chatId: "-1001234567890"        # opcional; se ausente usa TELEGRAM_DEFAULT_CHAT
        config:
          recordsPath: "$.data[*]"      # JSONPath para a lista de registros
          businessKey: "$.id"           # chave estável de deduplicação (por registro)
          template: "<b>{title}</b> — {price}"   # parse mode HTML por padrão
          fields:                        # placeholder -> JSONPath relativo ao registro
            title: "$.name"
            price: "$.price"
          parseMode: HTML               # HTML (default) | MARKDOWN_V2 | NONE
          pagination:                    # opcional
            nextUrlPath: "$.paging.next" # JSONPath para a URL da próxima página
            maxPages: 5
```

Notas:
- **Escaping seguro:** a marcação do template é confiável; apenas os **valores** dos campos (vindos
  da API) são escapados conforme o `parseMode`.
- **Segredos:** `secretRef: "env:NOME"` resolve a variável de ambiente `NOME` em runtime.
- **Paginação:** dirigida pela resposta (segue `nextUrlPath` até `maxPages`); omita para página única.

---

## Banco de dados

SQLite com **WAL**, schema versionado por **Liquibase**
([`db.changelog-master.sql`](src/main/resources/db/changelog/db.changelog-master.sql)).

| Tabela | Papel |
|---|---|
| `integrations` | APIs monitoradas (+ `config_json` config-driven) |
| `seen_records` | Fonte da verdade da deduplicação `(integration_id, business_key)` única |
| `notifications` | Histórico de envios (`PENDING`/`SENT`/`FAILED`) |
| `api_history` | Auditoria da resposta bruta (opcional) |
| `logs` | Log operacional persistido |

---

## Observabilidade

- **Actuator:** `health`, `info` habilitados.
- **Métricas Micrometer:** `apibridge.polls`, `apibridge.notifications.sent`,
  `apibridge.notifications.failed`, `apibridge.polls.skipped` (tag `integration`).
- Logging estruturado por execução: `Polling '<nome>' concluido: N novos, N enviados, N falhas em Xms`.

---

## Testes

```bash
./mvnw test
```

64 testes (unitários + e2e). Alguns são **guardados** e pulam sem dependências:
- `TelegramRealSendManualTest` — envio real; roda com `TELEGRAM_BOT_TOKEN` + `TELEGRAM_TEST_CHAT` no ambiente.
- `RedisDedupCacheContainerTest` — Testcontainers Redis; roda quando há Docker (`disabledWithoutDocker`).

Envio real manual:
```powershell
$env:TELEGRAM_BOT_TOKEN = "<token>"; $env:TELEGRAM_TEST_CHAT = "<chat_id>"
.\mvnw.cmd test "-Dtest=TelegramRealSendManualTest"
```

---

## Estrutura do projeto

```
src/main/java/com/promagroup/apibridge/
  client/      # ApiClient, auth, retry, SecretResolver
  service/     # Extractor, Detector, ProcessingService, PollingService, PollingLock
  telegram/    # Formatter, TelegramPublisher
  cache/       # DedupCache (Redis, degradação graciosa)
  scheduler/   # DynamicPollScheduler (CronTrigger por integração)
  config/      # seed via YAML
  entity/ repository/ dto/ util/
src/main/resources/
  application.yml          # config base (env-driven)
  application-local.yml    # perfil de demo
  db/changelog/            # Liquibase
```

---

## Segurança

- Segredos apenas via variáveis de ambiente / `secretRef` — nunca no banco ou nos logs.
- HTTPS-only nas chamadas externas; o token do Telegram nunca é logado.
- `.env` ignorado pelo git; imagem Docker roda como usuário não-root.

---

## Roadmap

Itens futuros (não implementados): OAuth2 no client, fila de reenvio para `FAILED`, lock distribuído
(Redis) para multi-instância, migração para Postgres/escala horizontal, dashboard/Swagger, e outros
conectores (Discord, Slack, Teams, e-mail).
