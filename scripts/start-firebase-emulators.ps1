# Entorno de pruebas local. No publica reglas ni contacta una base de datos real.
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$testDirectory = Join-Path $projectRoot "build/firebase-emulators"
New-Item -ItemType Directory -Force -Path $testDirectory | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
$config = @{
    firestore = @{ rules = "firestore.emulator.rules" }
    storage = @{ rules = "storage.emulator.rules" }
    emulators = @{
        auth = @{ port = 9099 }
        firestore = @{ port = 8080 }
        storage = @{ port = 9199 }
        ui = @{ enabled = $false }
        singleProjectMode = $true
    }
} | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText((Join-Path $testDirectory "firebase.json"), $config, $utf8)
# Estas reglas mínimas son exclusivamente fixtures descartables del emulador.
[IO.File]::WriteAllText((Join-Path $testDirectory "firestore.emulator.rules"), "rules_version = '2'; service cloud.firestore { match /databases/{database}/documents { match /{doc=**} { allow read, write: if request.auth != null; } } }", $utf8)
[IO.File]::WriteAllText((Join-Path $testDirectory "storage.emulator.rules"), "rules_version = '2'; service firebase.storage { match /b/{bucket}/o { match /{path=**} { allow read, write: if request.auth != null; } } }", $utf8)
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
Push-Location $testDirectory
try {
    & firebase emulators:start --project demo-share-you --only "auth,firestore,storage" --config firebase.json
} finally { Pop-Location }
