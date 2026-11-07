# Entorno de pruebas local. No publica reglas ni contacta una base de datos real.
param([switch]$RunTests)
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
# La integración usa las mismas reglas que se entregan para Firebase Console.
Copy-Item -LiteralPath (Join-Path $projectRoot 'firestore.rules') -Destination (Join-Path $testDirectory 'firestore.emulator.rules')
Copy-Item -LiteralPath (Join-Path $projectRoot 'storage.rules') -Destination (Join-Path $testDirectory 'storage.emulator.rules')
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
Push-Location $testDirectory
try {
    if ($RunTests) {
        $ErrorActionPreference = 'Continue'
        & firebase emulators:exec --project demo-share-you --only 'auth,firestore,storage' --config firebase.json '..\..\gradlew.bat -p ..\.. :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.firebaseEmulators=true -Pandroid.enableAdditionalTestOutput=true --no-daemon --console=plain'
        $taskTestExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($taskTestExit -ne 0) { throw 'Fallaron las pruebas Android con Firebase local.' }
    } else {
        & firebase emulators:start --project demo-share-you --only "auth,firestore,storage" --config firebase.json
    }
} finally { Pop-Location }
