#!/usr/bin/env bash
# Carrega o .env e sobe o app no perfil 'local' (Linux / macOS / CI).
# Uso:  ./scripts/run-local.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo ".env nao encontrado. Copie .env.example para .env e preencha." >&2
  exit 1
fi

# Exporta todas as variaveis definidas no .env para o ambiente do processo.
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

if [ -z "${TELEGRAM_BOT_TOKEN:-}" ]; then
  echo "AVISO: TELEGRAM_BOT_TOKEN vazio; os envios ao Telegram vao falhar (preencha o .env)." >&2
fi

cd "$ROOT"
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
