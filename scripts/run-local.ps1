# Carrega o .env e sobe o app no perfil 'local' (Windows / PowerShell).
# Uso:  .\scripts\run-local.ps1
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$envFile = Join-Path $root ".env"

if (-not (Test-Path $envFile)) {
    Write-Error ".env nao encontrado. Copie .env.example para .env e preencha."
    exit 1
}

# Le KEY=VALUE do .env e injeta no ambiente do processo (nao persiste no sistema).
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $key = $line.Substring(0, $idx).Trim()
    $val = $line.Substring($idx + 1).Trim()
    [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
}

if (-not $env:TELEGRAM_BOT_TOKEN) {
    Write-Warning "TELEGRAM_BOT_TOKEN vazio: os envios ao Telegram vao falhar (preencha o .env)."
}

Push-Location $root
try {
    & ".\mvnw.cmd" spring-boot:run "-Dspring-boot.run.profiles=local"
} finally {
    Pop-Location
}
