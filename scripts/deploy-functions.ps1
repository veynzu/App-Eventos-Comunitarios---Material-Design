param([ValidateSet('Preparation', 'Analysis')][string]$Stage = 'Preparation')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$filter = 'functions:share-you-ai:aiStatus'
if ($Stage -eq 'Analysis') {
    $envFile = Join-Path $projectRoot 'functions/.env.share-you-6a706'
    if (-not (Test-Path -LiteralPath $envFile)) {
        throw 'Primero configura functions/.env.share-you-6a706 según functions/README.md.'
    }
    $settings = Get-Content -LiteralPath $envFile
    if (-not ($settings -match '^AI_ENABLED=true$') -or -not ($settings -match '^GEMINI_MODEL=\S+$')) {
        throw 'Falta activar AI_ENABLED y seleccionar GEMINI_MODEL. La clave se registra por separado en Secret Manager.'
    }
    if (-not ($settings -match '^AI_ENFORCE_APP_CHECK=true$')) {
        throw 'Configura y verifica App Check antes de desplegar el analisis con AI_ENFORCE_APP_CHECK=true.'
    }
    $filter = 'functions:share-you-ai'
}
Push-Location $projectRoot
try {
    $ErrorActionPreference = 'Continue'
    & firebase.cmd deploy --only $filter --project share-you-6a706 --non-interactive 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Falló el despliegue de Functions (código $LASTEXITCODE)." }
    # La autenticación se verifica con Firebase Auth dentro de las funciones invocables.
    # Cloud Run no debe rechazar esos JWT por esperar una identidad IAM de Google Cloud.
    $services = @('aistatus')
    if ($Stage -eq 'Analysis') { $services += @('analyzetext', 'analyzeeventdraft') }
    foreach ($service in $services) {
        & gcloud.cmd run services update $service --no-invoker-iam-check --region us-central1 --project share-you-6a706 --quiet 2>&1 | ForEach-Object { "$_" }
        if ($LASTEXITCODE -ne 0) { throw "Faltó configurar la recepción de tokens Firebase para $service." }
    }
} finally { Pop-Location }
