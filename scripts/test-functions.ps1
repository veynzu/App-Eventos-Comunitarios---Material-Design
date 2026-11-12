param([switch]$UnitOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
function Invoke-CheckedNative([string]$Command, [string[]]$Arguments) {
    # Windows PowerShell interpreta stderr de npm como error aunque termine con código 0.
    $ErrorActionPreference = 'Continue'
    & $Command @Arguments 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Falló $Command (código $LASTEXITCODE)." }
}
Push-Location $projectRoot
try {
    Invoke-CheckedNative 'npm.cmd' @('--prefix', 'functions', 'run', 'check')
    if (-not $UnitOnly) {
        foreach ($port in @(5001, 9099, 8080)) {
            if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
                throw "El puerto $port está ocupado. Cierra el entorno de pruebas anterior antes de iniciar otro."
            }
        }
        $localEnvFile = Join-Path $projectRoot 'functions/.env.local'
        if ((Test-Path -LiteralPath $localEnvFile) -or (Test-Path -LiteralPath (Join-Path $projectRoot 'functions/.secret.local'))) {
            throw 'Aparta functions/.env.local y functions/.secret.local antes de las pruebas automáticas: estas no usan Gemini real.'
        }
        [IO.File]::WriteAllText($localEnvFile, "AI_ENABLED=false`nGEMINI_MODEL=`nAI_ENFORCE_APP_CHECK=false`n", [Text.UTF8Encoding]::new($false))
        $previousAiEnabled = $env:AI_ENABLED
        $previousModel = $env:GEMINI_MODEL
        $previousProject = $env:GCLOUD_PROJECT
        $env:AI_ENABLED = 'false'
        $env:GEMINI_MODEL = ''
        $env:GCLOUD_PROJECT = 'demo-share-you-ai'
        if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
        try {
            Invoke-CheckedNative 'firebase.cmd' @('emulators:exec', '--project', 'demo-share-you-ai', '--config', 'firebase.json', '--only', 'functions,auth,firestore', 'npm --prefix functions run test:integration')
        } finally {
            $env:AI_ENABLED = $previousAiEnabled
            $env:GEMINI_MODEL = $previousModel
            $env:GCLOUD_PROJECT = $previousProject
            Remove-Item -LiteralPath $localEnvFile
        }
    }
} finally { Pop-Location }
