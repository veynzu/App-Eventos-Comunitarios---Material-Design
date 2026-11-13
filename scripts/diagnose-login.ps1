param([switch]$SoloConfiguracion)

# Usa exclusivamente una cuenta existente. Nunca imprime ni guarda credenciales o tokens.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$packageName = 'com.eventoscomunitarios.app'

function Get-KeyFingerprint([string]$Value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)))).Replace('-', '').ToLowerInvariant().Substring(0, 16)
    } finally { $sha.Dispose() }
}

function Invoke-AuthRequest([string]$Resource, $JsonBody) {
    $response = $null
    $content = $null
    $raw = $null
    try {
        $uri = 'https://identitytoolkit.googleapis.com/v1/' + $Resource + '?key=' + [Uri]::EscapeDataString($apiKey)
        if ($null -eq $JsonBody) {
            $response = $http.GetAsync($uri).GetAwaiter().GetResult()
        } else {
            $content = [Net.Http.StringContent]::new($JsonBody, [Text.Encoding]::UTF8, 'application/json')
            $response = $http.PostAsync($uri, $content).GetAwaiter().GetResult()
        }
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $data = $raw | ConvertFrom-Json
        $code = $null
        if (-not $response.IsSuccessStatusCode) {
            # Solo conservar un identificador; el cuerpo completo puede contener datos privados.
            $code = 'HTTP_' + [int]$response.StatusCode
            if ([string]$data.error.message -cmatch '^([A-Z][A-Z0-9_]+)(?:\s|:|$)') {
                $code = $Matches[1]
            }
        }
        return [pscustomobject]@{
            Ok = $response.IsSuccessStatusCode
            Status = [int]$response.StatusCode
            Code = $code
            Data = $data
        }
    } catch {
        # Nunca mostrar la excepcion: puede incluir la URL con la clave o el cuerpo HTTP.
        return [pscustomobject]@{ Ok = $false; Status = 0; Code = 'NETWORK_OR_RESPONSE_ERROR'; Data = $null }
    } finally {
        $raw = $null
        if ($null -ne $content) { $content.Dispose() }
        if ($null -ne $response) { $response.Dispose() }
    }
}

try {
    $config = Get-Content -LiteralPath (Join-Path $projectRoot 'app/google-services.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $client = @($config.client | Where-Object { $_.client_info.android_client_info.package_name -eq $packageName })
    if ($client.Count -ne 1) { throw 'Invalid config' }
    $apiKey = [string]$client[0].api_key[0].current_key
    $appId = [string]$client[0].client_info.mobilesdk_app_id
    $projectId = [string]$config.project_info.project_id
    $projectNumber = [string]$config.project_info.project_number
    if (-not $apiKey -or -not $appId -or -not $projectId -or -not $projectNumber) { throw 'Incomplete config' }
} catch {
    throw 'No se pudo leer app/google-services.json para com.eventoscomunitarios.app. Revisa que exista el archivo completo.'
}

$report = [ordered]@{
    configuredProject = $projectId
    configuredAppId = $appId
    keyId = Get-KeyFingerprint $apiKey
    compiledConfig = 'NOT_BUILT'
    actualAuthProject = $null
    authProjectMatches = $null
    configHttp = $null
    configCode = $null
    login = 'NOT_RUN'
    loginHttp = $null
    loginCode = $null
}

# Comprueba tambien los recursos realmente generados por Gradle, sin mostrar sus claves.
$generatedPaths = @(
    'app/build/generated/res/processDebugGoogleServices/values/values.xml',
    'app/build/generated/res/google-services/debug/values/values.xml'
)
foreach ($relativePath in $generatedPaths) {
    $generatedFile = Join-Path $projectRoot $relativePath
    if (Test-Path -LiteralPath $generatedFile) {
        try {
            [xml]$resources = Get-Content -LiteralPath $generatedFile -Raw -Encoding UTF8
            $compiled = @{}
            foreach ($entry in $resources.resources.string) { $compiled[[string]$entry.name] = [string]$entry.InnerText }
            $same = $compiled['google_api_key'] -ceq $apiKey -and $compiled['google_app_id'] -ceq $appId -and $compiled['project_id'] -ceq $projectId
            $report.compiledConfig = if ($same) { 'MATCH' } else { 'MISMATCH' }
        } catch { $report.compiledConfig = 'UNREADABLE' }
        break
    }
}

Add-Type -AssemblyName System.Net.Http
$handler = [Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$http = [Net.Http.HttpClient]::new($handler)
$http.Timeout = [TimeSpan]::FromSeconds(25)
$previousTls = [Net.ServicePointManager]::SecurityProtocol
[Net.ServicePointManager]::SecurityProtocol = $previousTls -bor [Net.SecurityProtocolType]::Tls12
$securePassword = $null
$passwordPointer = [IntPtr]::Zero
$plainPassword = $null
$requestBody = $null
$loginResponse = $null

try {
    $publicConfig = Invoke-AuthRequest -Resource 'projects' -JsonBody $null
    $report.configHttp = $publicConfig.Status
    $report.configCode = $publicConfig.Code
    if ($publicConfig.Ok) {
        $report.actualAuthProject = [string]$publicConfig.Data.projectId
        $report.authProjectMatches = $report.actualAuthProject -eq $projectId -or $report.actualAuthProject -eq $projectNumber
    }

    if (-not $SoloConfiguracion) {
        if (-not $publicConfig.Ok) {
            $report.login = 'SKIPPED_CONFIG_UNAVAILABLE'
            Write-Host 'No se pudo comprobar el proyecto de destino. Se omitio el login; comparte el diagnostico.'
        } elseif ($report.authProjectMatches -eq $false) {
            $report.login = 'SKIPPED_PROJECT_MISMATCH'
            Write-Host 'La clave apunta a otro proyecto. Se omitio el login para no enviar las credenciales alli.'
        } else {
            Write-Host 'Usa una cuenta que YA exista en Share You. No se crean cuentas ni se cambian contrasenas.'
            $loginEmail = (Read-Host 'Correo de la cuenta existente').Trim()
            $securePassword = Read-Host 'Contrasena (se escribe oculta; pulsa Enter al terminar)' -AsSecureString
            if (-not $loginEmail -or $securePassword.Length -eq 0) {
                $report.login = 'SKIPPED_EMPTY_INPUT'
            } else {
                $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
                $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
                $requestBody = @{ email = $loginEmail; password = $plainPassword; returnSecureToken = $true } | ConvertTo-Json -Compress
                $plainPassword = $null
                [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
                $passwordPointer = [IntPtr]::Zero
                $loginResponse = Invoke-AuthRequest -Resource 'accounts:signInWithPassword' -JsonBody $requestBody
                $requestBody = $null
                $report.login = if ($loginResponse.Ok -and $loginResponse.Data.idToken) { 'OK' } else { 'FAILED' }
                $report.loginHttp = $loginResponse.Status
                $report.loginCode = $loginResponse.Code
                # Descartar los tokens de esta prueba. No se escriben en disco ni en consola.
                $loginResponse = $null
            }
        }
    }
} finally {
    $plainPassword = $null
    $requestBody = $null
    $loginResponse = $null
    if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
    if ($null -ne $securePassword) { $securePassword.Dispose() }
    $http.Dispose()
    $apiKey = $null
    $config = $null
    $client = $null
    $compiled = $null
    [Net.ServicePointManager]::SecurityProtocol = $previousTls
}

Write-Host ''
Write-Host '--- INICIO DIAGNOSTICO SHARE YOU ---'
$report | ConvertTo-Json | Write-Host
Write-Host '--- FIN DIAGNOSTICO SHARE YOU ---'
