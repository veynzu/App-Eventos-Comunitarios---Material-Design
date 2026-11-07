# Comprueba Storage sin contactar el bucket real ni publicar reglas.
$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
$taskTestDirectory = Join-Path $taskProjectRoot 'build/storage-rules-tests'
New-Item -ItemType Directory -Force -Path $taskTestDirectory | Out-Null
Copy-Item -LiteralPath (Join-Path $taskProjectRoot 'storage.rules') -Destination $taskTestDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'test-storage-rules.mjs') -Destination $taskTestDirectory
$taskConfig = @{
    storage = @{ rules = 'storage.rules' }
    emulators = @{
        auth = @{ port = 19099 }
        storage = @{ port = 19199 }
        ui = @{ enabled = $false }
        singleProjectMode = $true
    }
} | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText((Join-Path $taskTestDirectory 'firebase.json'), $taskConfig, [Text.UTF8Encoding]::new($false))
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
Push-Location $taskTestDirectory
try {
    & firebase emulators:exec --project demo-share-you-storage-rules --only 'auth,storage' --config firebase.json 'node test-storage-rules.mjs'
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas de reglas de Storage.' }
} finally { Pop-Location }
