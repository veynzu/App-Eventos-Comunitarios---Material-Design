# Prueba las reglas de acceso/perfiles solo contra emuladores locales.
$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
$taskTestDirectory = Join-Path $taskProjectRoot 'build/firestore-rules-tests'
New-Item -ItemType Directory -Force -Path $taskTestDirectory | Out-Null
Copy-Item -LiteralPath (Join-Path $taskProjectRoot 'firestore.rules') -Destination $taskTestDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'test-firestore-rules.mjs') -Destination $taskTestDirectory
$taskConfig = @{
    firestore = @{ rules = 'firestore.rules' }
    emulators = @{
        auth = @{ port = 19099 }
        firestore = @{ port = 18080 }
        ui = @{ enabled = $false }
        singleProjectMode = $true
    }
} | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText((Join-Path $taskTestDirectory 'firebase.json'), $taskConfig, [Text.UTF8Encoding]::new($false))
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
Push-Location $taskTestDirectory
try {
    $ErrorActionPreference = 'Continue'
    & firebase.cmd emulators:exec --project demo-share-you-rules --only 'auth,firestore' --config firebase.json 'node test-firestore-rules.mjs' 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas de reglas de Firestore.' }
} finally { Pop-Location }
