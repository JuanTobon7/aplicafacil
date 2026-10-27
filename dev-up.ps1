<#
.SYNOPSIS
  Levanta AplicaFacil en local (ADR 0003/0004): infraestructura en Docker; scraper y servicios Java en este equipo.

.DESCRIPTION
  1. Si Docker Desktop no esta corriendo, lo abre y espera a que responda.
  2. infra/.env: lo crea desde .env.example, agrega variables nuevas y genera SESSION_ENCRYPTION_KEY y
     SERVICE_CLIENT_SECRET si estan vacias.
  3. docker compose up -d (Postgres, RabbitMQ y Redis, publicados solo en 127.0.0.1) y espera a Postgres.
  4. Crea los schemas/usuarios que falten en un volumen existente (idempotente, sin borrar datos).
  5. Compila lo necesario (Java con JDK 25, scraper, web y el entorno Python del mcp-gateway) la primera vez
     o con -Build. Si falta uv lo instala con pip, y uv descarga Python 3.13 si no esta.
  6. Arranca scraper, identity, linkedin, computrabajo, profile, jobs, ai-config, api-gateway y mcp-gateway (logs en logs/).
  7. Abre http://127.0.0.1:8080 (el gateway sirve el front con el token local) y se queda en primer plano mostrando
     la actividad del worker de busquedas. Ctrl+C detiene todos los servicios y los contenedores (sin borrar datos).

  Con -Test no arranca nada: corre todas las pruebas (Java, mcp-gateway con RabbitMQ real, scraper y
  typecheck del front) y termina con codigo 1 si alguna falla.

.EXAMPLE
  .\dev-up.ps1           # todo
  .\dev-up.ps1 -Build    # recompila Java, scraper, web y mcp-gateway antes de arrancar
  .\dev-up.ps1 -Dev      # ademas corre Vite en http://127.0.0.1:5173 (recarga en caliente)
  .\dev-up.ps1 -Test     # corre todas las pruebas
  .\dev-up.ps1 -ShowBrowser # las busquedas de vacantes se ven en una ventana del navegador (no headless)
  .\dev-up.ps1 -Record    # como -ShowBrowser, mas lento y grabando un video de cada busqueda en recordings/
  .\dev-up.ps1 -Detach   # deja los servicios en segundo plano (como antes); se detienen con -Down
  .\dev-up.ps1 -KeepInfra # Ctrl+C detiene los servicios pero deja corriendo Postgres, RabbitMQ y Redis
  .\dev-up.ps1 -Down     # detiene los servicios y los contenedores (sin borrar datos)
#>
param(
    [switch]$Build,
    [switch]$Dev,
    [switch]$Test,
    [switch]$Detach,
    [switch]$ShowBrowser,
    [switch]$Record,
    [switch]$KeepInfra,
    [switch]$Down
)

# No 'Stop': en PowerShell 5.1 cualquier aviso de un comando nativo en stderr (docker, mvn, npm) se volveria
# un error fatal. Los fallos se detectan con $LASTEXITCODE.
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$infra = Join-Path $root 'infra'
$envFile = Join-Path $infra '.env'
$envExample = Join-Path $infra '.env.example'
$backend = Join-Path $root 'services\backend'
$scraper = Join-Path $root 'services\scraper'
$web = Join-Path $root 'apps\web'
$logs = Join-Path $root 'logs'
$pidFile = Join-Path $logs 'pids.txt'
$mcp = Join-Path $root 'services\mcp-gateway'
$compose = @('compose', '-f', (Join-Path $infra 'docker-compose.yml'))

# Servicios Java: modulo Maven, artefacto y puerto
$javaServices = @(
    @{ Name = 'identity-service'; Module = 'identity-service'; Port = 9000 },
    @{ Name = 'linkedin-service'; Module = 'linkedin-service'; Port = 8085 },
    @{ Name = 'computrabajo-service'; Module = 'computrabajo-service'; Port = 8087 },
    @{ Name = 'profile-service';  Module = 'profile-service';  Port = 8084 },
    @{ Name = 'jobs-service';     Module = 'jobs-service';     Port = 8082 },
    @{ Name = 'ai-config-service'; Module = 'ai-config-service'; Port = 8086 },
    @{ Name = 'api-gateway';      Module = 'platform\api-gateway'; Port = 8080 }
)

function Step($message) { Write-Host "`n==> $message" -ForegroundColor Cyan }
function Fail($message) { Write-Host "ERROR: $message" -ForegroundColor Red; exit 1 }

function Read-EnvFile($path) {
    $values = @{}
    foreach ($line in Get-Content $path) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $values[$Matches[1]] = $Matches[2].Trim() }
    }
    return $values
}

function New-Secret {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes)
}

function Stop-Services {
    if (Test-Path $pidFile) {
        foreach ($id in Get-Content $pidFile) {
            if ($id -match '^\d+$') {
                # /T: tambien los procesos hijos (Chromium del scraper)
                cmd /c "taskkill /PID $id /T /F >nul 2>&1"
            }
        }
        Remove-Item $pidFile -Force
    }
}

function Wait-Http($name, $url, $timeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    Write-Host -NoNewline "    $name "
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3 | Out-Null
            Write-Host 'OK' -ForegroundColor Green
            return $true
        } catch {
            if ($_.Exception.Response) { Write-Host 'OK' -ForegroundColor Green; return $true }
        }
        Write-Host -NoNewline '.'
        Start-Sleep -Seconds 2
    }
    Write-Host 'sin respuesta' -ForegroundColor Yellow
    return $false
}

function Find-Jdk25 {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += Get-ChildItem -Directory -ErrorAction SilentlyContinue `
        'C:\JAVA-VERSIONS\*', 'C:\Program Files\Java\*', 'C:\Program Files\Eclipse Adoptium\*', "$HOME\.jdks\*" |
        ForEach-Object { $_.FullName; Get-ChildItem -Directory $_.FullName -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName } }
    foreach ($dir in $candidates) {
        $release = Join-Path $dir 'release'
        if ((Test-Path $release) -and (Select-String -Path $release -Pattern 'JAVA_VERSION="25' -Quiet)) { return $dir }
    }
    return $null
}

function Test-Docker {
    cmd /c "docker info >nul 2>&1"
    return $LASTEXITCODE -eq 0
}

# Abre Docker Desktop si esta instalado pero apagado y espera hasta que el motor responda
function Start-DockerDesktop {
    if (Test-Docker) { return }
    $exe = @("$env:ProgramFiles\Docker\Docker\Docker Desktop.exe", "$env:LOCALAPPDATA\Docker\Docker Desktop.exe") |
        Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $exe) { Fail 'Docker no esta corriendo y no se encontro Docker Desktop. Abrelo y vuelve a intentar.' }
    Step 'Abriendo Docker Desktop'
    Start-Process -FilePath $exe | Out-Null
    $deadline = (Get-Date).AddSeconds(180)
    Write-Host -NoNewline '    esperando al motor de Docker '
    while (-not (Test-Docker)) {
        if ((Get-Date) -gt $deadline) { Write-Host ''; Fail 'Docker Desktop no respondio en 3 minutos.' }
        Write-Host -NoNewline '.'
        Start-Sleep -Seconds 3
    }
    Write-Host 'OK' -ForegroundColor Green
}

# uv para el mcp-gateway. Si falta, se instala con pip (con cualquier Python disponible) o con winget.
# Devuelve el comando como arreglo (p. ej. @('uv') o @('py', '-3', '-m', 'uv')), o $null si no se pudo.
function Get-Uv {
    if (Get-Command uv -ErrorAction SilentlyContinue) { return @('uv') }
    $pythons = @()
    if (Get-Command py -ErrorAction SilentlyContinue) { $pythons += , @('py', '-3.13'); $pythons += , @('py', '-3') }
    if (Get-Command python -ErrorAction SilentlyContinue) { $pythons += , @('python') }
    foreach ($python in $pythons) {
        $prefix = @($python | Select-Object -Skip 1)
        & $python[0] @($prefix + @('-m', 'uv', '--version')) *> $null
        if ($LASTEXITCODE -eq 0) { return $python + @('-m', 'uv') }
    }
    Step 'Instalando uv (gestor de Python del mcp-gateway)'
    foreach ($python in $pythons) {
        $prefix = @($python | Select-Object -Skip 1)
        & $python[0] @($prefix + @('-m', 'pip', 'install', '--user', '--quiet', 'uv')) | Out-Host
        & $python[0] @($prefix + @('-m', 'uv', '--version')) *> $null
        if ($LASTEXITCODE -eq 0) { return $python + @('-m', 'uv') }
    }
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        winget install --id astral-sh.uv -e --silent --accept-package-agreements --accept-source-agreements | Out-Host
        $uvExe = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Links\uv.exe'
        if (Test-Path $uvExe) { return @($uvExe) }
    }
    return $null
}

function Invoke-Uv([string[]]$uv, [string[]]$arguments) {
    $prefix = @($uv | Select-Object -Skip 1)
    & $uv[0] @($prefix + $arguments)
}

# Crea o actualiza services/mcp-gateway/.venv (uv descarga Python 3.13 si no esta instalado)
function Sync-McpGateway([string[]]$uv, [switch]$WithDev) {
    Push-Location $mcp
    try {
        $syncArgs = @('sync', '--python', '3.13')
        if (-not $WithDev) { $syncArgs += '--no-dev' }
        Invoke-Uv $uv $syncArgs | Out-Host
        return $LASTEXITCODE -eq 0
    } finally { Pop-Location }
}

# docker-py (Testcontainers de Python) usa el pipe por defecto; Docker Desktop puede exponer otro (desktop-linux)
function Set-DockerHostFromContext {
    if ($env:DOCKER_HOST) { return }
    $dockerHost = docker context inspect --format '{{.Endpoints.docker.Host}}' 2>$null
    if ($LASTEXITCODE -eq 0 -and $dockerHost) { $env:DOCKER_HOST = $dockerHost.Trim() }
}

# -- Apagar ----------------------------------------------------------
if ($Down) {
    Step 'Deteniendo servicios'
    Stop-Services
    if (Test-Docker) { docker @compose stop }
    exit 0
}

# -- Docker ----------------------------------------------------------
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Fail 'Docker no esta instalado.' }
Start-DockerDesktop

# -- Pruebas ---------------------------------------------------------
if ($Test) {
    $results = [ordered]@{}
    $jdk = Find-Jdk25
    if (-not $jdk) { Fail 'No se encontro un JDK 25 (el proyecto compila con Java 25). Instalalo o define JAVA_HOME.' }
    $env:JAVA_HOME = $jdk

    Step 'Pruebas Java (Testcontainers: Postgres y RabbitMQ)'
    Push-Location $backend
    try { & .\mvnw.cmd -B test; $results['Java (backend)'] = $LASTEXITCODE -eq 0 } finally { Pop-Location }

    Step 'Pruebas del mcp-gateway (incluye RabbitMQ real)'
    $uv = Get-Uv
    if (-not $uv) {
        $results['mcp-gateway'] = $false
        Write-Host '    No se pudo instalar uv: instala Python o uv y vuelve a intentar.' -ForegroundColor Yellow
    } elseif (-not (Sync-McpGateway $uv -WithDev)) {
        $results['mcp-gateway'] = $false
    } else {
        Set-DockerHostFromContext
        Push-Location $mcp
        try { Invoke-Uv $uv @('run', 'pytest', '-rs'); $results['mcp-gateway'] = $LASTEXITCODE -eq 0 } finally { Pop-Location }
    }

    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { Fail 'Node.js/npm no esta instalado.' }
    foreach ($app in @($scraper, $web)) {
        $name = Split-Path $app -Leaf
        Step "Pruebas de $name"
        Push-Location $app
        try {
            if (-not (Test-Path 'node_modules')) { npm install --no-audit --no-fund }
            npm run typecheck
            $ok = $LASTEXITCODE -eq 0
            if ($name -eq 'scraper' -and $ok) { npm test; $ok = $LASTEXITCODE -eq 0 }
            $results[$name] = $ok
        } finally { Pop-Location }
    }

    Step 'Resumen'
    foreach ($entry in $results.GetEnumerator()) {
        if ($entry.Value) { Write-Host "    OK     $($entry.Key)" -ForegroundColor Green }
        else { Write-Host "    FALLO  $($entry.Key)" -ForegroundColor Red }
    }
    if ($results.Values -contains $false) { exit 1 }
    exit 0
}

# -- infra/.env ------------------------------------------------------
Step 'Preparando infra/.env'
if (-not (Test-Path $envFile)) {
    Copy-Item $envExample $envFile
    Write-Host '    Creado desde .env.example'
}
$lines = [System.Collections.Generic.List[string]](Get-Content $envFile)
$current = Read-EnvFile $envFile
foreach ($line in Get-Content $envExample) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=' -and -not $current.ContainsKey($Matches[1])) {
        $lines.Add($line)
        Write-Host "    Variable nueva: $($Matches[1])"
    }
}
foreach ($name in 'SESSION_ENCRYPTION_KEY', 'SERVICE_CLIENT_SECRET') {
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^\s*$name=\s*$") {
            $lines[$i] = "$name=" + (New-Secret)
            Write-Host "    Generada $name"
        }
    }
}
[System.IO.File]::WriteAllLines($envFile, $lines, (New-Object System.Text.UTF8Encoding $false))
$cfg = Read-EnvFile $envFile

# -- Contenedores ----------------------------------------------------
Step 'Levantando Postgres, RabbitMQ y Redis'
docker @compose up -d
if ($LASTEXITCODE -ne 0) { Fail 'docker compose fallo (revisa la salida de arriba).' }
$deadline = (Get-Date).AddSeconds(90)
do {
    Start-Sleep -Seconds 2
    $health = docker @compose ps --format '{{.Health}}' postgres
} while ($health -ne 'healthy' -and (Get-Date) -lt $deadline)
if ($health -ne 'healthy') { Fail 'Postgres no quedo saludable. Revisa: docker compose -f infra/docker-compose.yml logs postgres' }

# Volumenes creados antes de ADR 0003 no tienen los schemas nuevos: se crean sin tocar lo existente
Step 'Verificando schemas de la base de datos'
$sql = @"
SET client_min_messages = warning;
DO `$`$
DECLARE r record;
BEGIN
  FOR r IN SELECT * FROM (VALUES
      ('identity_svc', 'identity', '$($cfg.IDENTITY_DB_PASSWORD)'),
      ('linkedin_svc', 'linkedin', '$($cfg.LINKEDIN_DB_PASSWORD)'),
      ('computrabajo_svc', 'computrabajo', '$($cfg.COMPUTRABAJO_DB_PASSWORD)'),
      ('profile_svc',  'profile',  '$($cfg.PROFILE_DB_PASSWORD)'),
      ('jobs_svc',     'jobs',     '$($cfg.JOBS_DB_PASSWORD)'),
      ('aiconfig_svc', 'aiconfig', '$($cfg.AICONFIG_DB_PASSWORD)')) AS t(usr, sch, pwd)
  LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r.usr) THEN
      EXECUTE format('CREATE USER %I WITH PASSWORD %L', r.usr, r.pwd);
    END IF;
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I AUTHORIZATION %I', r.sch, r.usr);
    EXECUTE format('ALTER ROLE %I SET search_path = %I, public', r.usr, r.sch);
  END LOOP;
END
`$`$;
CREATE EXTENSION IF NOT EXISTS vector;
"@
$sql | docker @compose exec -T postgres psql -q -v ON_ERROR_STOP=1 -U $cfg.POSTGRES_USER -d $cfg.POSTGRES_DB
if ($LASTEXITCODE -ne 0) { Fail 'No se pudieron crear los schemas.' }

# -- Compilacion -----------------------------------------------------
# Antes de compilar: Windows no deja reemplazar un .jar que esta en uso
Stop-Services
$jdk = Find-Jdk25
if (-not $jdk) { Fail 'No se encontro un JDK 25 (el proyecto compila con Java 25). Instalalo o define JAVA_HOME.' }
$env:JAVA_HOME = $jdk
$java = Join-Path $jdk 'bin\java.exe'

$jarsMissing = $javaServices | Where-Object { -not (Get-ChildItem (Join-Path $backend "$($_.Module)\target\*.jar") -ErrorAction SilentlyContinue) }
if ($Build -or $jarsMissing) {
    Step "Compilando servicios Java (JDK: $jdk)"
    $modules = ($javaServices | ForEach-Object { $_.Module -replace '\\', '/' }) -join ','
    Push-Location $backend
    try {
        & .\mvnw.cmd -q -B -DskipTests -pl $modules -am package
        if ($LASTEXITCODE -ne 0) { Fail 'La compilacion de Java fallo.' }
    } finally { Pop-Location }
}

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { Fail 'Node.js/npm no esta instalado.' }
foreach ($app in @($scraper, $web)) {
    if ($Build -or -not (Test-Path (Join-Path $app 'node_modules')) -or -not (Test-Path (Join-Path $app 'dist'))) {
        Step "Compilando $(Split-Path $app -Leaf)"
        Push-Location $app
        try {
            npm install --no-audit --no-fund
            npm run build
            if ($LASTEXITCODE -ne 0) { Fail "La compilacion de $app fallo." }
        } finally { Pop-Location }
    }
}

# mcp-gateway (Python, ADR 0005): uv crea .venv la primera vez (y descarga Python 3.13 si hace falta)
$uv = Get-Uv
$mcpReady = $false
if (-not $uv) {
    Write-Host '    No se pudo instalar uv: se omite el mcp-gateway. Instala Python o uv y vuelve a correr el script.' -ForegroundColor Yellow
} elseif ($Build -or -not (Test-Path (Join-Path $mcp '.venv\Scripts\python.exe'))) {
    Step 'Preparando el entorno del mcp-gateway (uv)'
    $mcpReady = Sync-McpGateway $uv
    if (-not $mcpReady) { Write-Host '    uv sync fallo: se omite el mcp-gateway (revisa la salida de arriba).' -ForegroundColor Yellow }
} else {
    $mcpReady = $true
}

# -- Arranque --------------------------------------------------------
Step 'Arrancando servicios (logs en logs\)'
New-Item -ItemType Directory -Force $logs | Out-Null

$configRepo = (Join-Path $backend 'config-repo') -replace '\\', '/'
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = $cfg.POSTGRES_PORT
$env:DB_NAME = $cfg.POSTGRES_DB
foreach ($key in 'IDENTITY_DB_PASSWORD', 'LINKEDIN_DB_PASSWORD', 'COMPUTRABAJO_DB_PASSWORD', 'PROFILE_DB_PASSWORD', 'JOBS_DB_PASSWORD', 'AICONFIG_DB_PASSWORD',
                 'RABBITMQ_USER', 'RABBITMQ_PASSWORD', 'SESSION_ENCRYPTION_KEY', 'SESSION_ENCRYPTION_KEY_VERSION',
                 'SERVICE_CLIENT_SECRET', 'LINKEDIN_OAUTH_ENABLED', 'LINKEDIN_CLIENT_ID', 'LINKEDIN_CLIENT_SECRET') {
    Set-Item "env:$key" $cfg[$key]
}
$env:FRONT_DIST = (Join-Path $web 'dist') -replace '\\', '/'
# Procesos lanzados: nombre y Process. Al salir (Ctrl+C) se detienen todos.
$started = [System.Collections.Generic.List[object]]::new()
function Start-AppProcess($name, $file, $arguments, $workingDirectory) {
    $proc = Start-Process -FilePath $file -ArgumentList $arguments -WorkingDirectory $workingDirectory -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs "$name.log") -RedirectStandardError (Join-Path $logs "$name.err.log") -PassThru
    $started.Add([pscustomobject]@{ Name = $name; Process = $proc })
    # pids.txt: si se cierra la ventana sin Ctrl+C, la siguiente ejecucion (o -Down) los detiene igual
    $started | ForEach-Object { $_.Process.Id } | Set-Content $pidFile
}

# Scraper (Playwright): necesita el escritorio para abrir el navegador visible
if ($Record) { $ShowBrowser = $true }
$env:SCRAPER_SHOW_BROWSER = if ($ShowBrowser) { 'true' } else { 'false' }
# -Record: video de cada busqueda (nunca del inicio de sesion) y una pausa antes de cada accion para que se vea
$env:SCRAPER_RECORD_DIR = if ($Record) { (Join-Path $root 'recordings') -replace '\\', '/' } else { '' }
$env:SCRAPER_SLOW_MO_MS = if ($Record) { '400' } else { '0' }
Start-AppProcess 'scraper' 'node' @('dist/main.js') $scraper

foreach ($svc in $javaServices) {
    $env:CONFIG_IMPORT = @(
        "optional:file:$configRepo/application.yml",
        "optional:file:$configRepo/$($svc.Name).yml",
        "optional:file:$configRepo/application-local.yml",
        "optional:file:$configRepo/$($svc.Name)-local.yml"
    ) -join ','
    $jar = Get-ChildItem (Join-Path $backend "$($svc.Module)\target\*.jar") | Where-Object { $_.Name -notlike '*plain*' } | Select-Object -First 1
    Start-AppProcess $svc.Name $java @('-jar', "`"$($jar.FullName)`"") (Join-Path $backend $svc.Module)
}
if ($mcpReady) {
    $env:SERVICE_CLIENT_ID = 'mcp-gateway'
    $env:MCP_PORT = if ($cfg.MCP_PORT) { $cfg.MCP_PORT } else { '8000' }
    foreach ($key in 'MCP_CREDENTIAL_TTL_SECONDS', 'MCP_RECONCILE_INTERVAL_SECONDS') { if ($cfg[$key]) { Set-Item "env:$key" $cfg[$key] } }
    Start-AppProcess 'mcp-gateway' (Join-Path $mcp '.venv\Scripts\python.exe') @('-m', 'aplicafacil_mcp') $mcp
}
if ($Dev) {
    # Vite con recarga en caliente; su proxy usa el gateway en :8080
    Start-AppProcess 'vite' 'cmd.exe' @('/c', 'npm', 'run', 'dev') $web
}

# Todo lo que sigue corre dentro de try/finally: Ctrl+C (o un error) detiene los servicios
$followJobs = @()
try {
    Step 'Esperando a los servicios'
    $ready = (Wait-Http 'identity-service :9000' 'http://127.0.0.1:9000/actuator/health' 180) -and
             (Wait-Http 'scraper          :8090' 'http://127.0.0.1:8090/health' 60) -and
             (Wait-Http 'linkedin-service :8085' 'http://127.0.0.1:8085/actuator/health' 180) -and
             (Wait-Http 'computrabajo-service :8087' 'http://127.0.0.1:8087/actuator/health' 180) -and
             (Wait-Http 'profile-service  :8084' 'http://127.0.0.1:8084/actuator/health' 180) -and
             (Wait-Http 'jobs-service     :8082' 'http://127.0.0.1:8082/actuator/health' 180) -and
             (Wait-Http 'ai-config-service :8086' 'http://127.0.0.1:8086/actuator/health' 180) -and
             (Wait-Http 'api-gateway      :8080' 'http://127.0.0.1:8080/actuator/health' 180)
    if ($mcpReady) { $null = Wait-Http 'mcp-gateway      :8000' 'http://127.0.0.1:8000/health/live' 60 }
    if ($Dev) { $null = Wait-Http 'vite             :5173' 'http://127.0.0.1:5173/' 60 }
    if (-not $ready) { Write-Host '    Algun servicio no respondio. Revisa logs\<servicio>.log' -ForegroundColor Yellow }

    $appUrl = if ($Dev) { 'http://127.0.0.1:5173' } else { 'http://127.0.0.1:8080' }
    Write-Host ''
    Write-Host "  AplicaFacil ..... $appUrl" -ForegroundColor Green
    Write-Host '  RabbitMQ ........ http://127.0.0.1:15672'
    Write-Host '  MCP ............. http://127.0.0.1:8000/mcp (token de usuario)'
    Write-Host '  pgAdmin ......... docker compose -f infra/docker-compose.yml --profile tools up -d  ->  http://127.0.0.1:5050'
    Write-Host '  Logs ............ logs\<servicio>.log'

    if ($Detach) {
        Write-Host '  Modo -Detach: los servicios quedan en segundo plano. Detenerlos: .\dev-up.cmd -Down' -ForegroundColor Yellow
        Start-Process $appUrl
        $started.Clear()   # el finally no los detiene
        return
    }

    Start-Process $appUrl
    Step 'Corriendo. Ctrl+C detiene todo (servicios y contenedores).'
    Write-Host '    Actividad del worker de busquedas y del scraper:' -ForegroundColor DarkGray
    if ($Record) { Write-Host '    -Record: cada busqueda se graba en recordings/ (el video queda al terminar la busqueda).' -ForegroundColor DarkGray }
    if ($ShowBrowser) { Write-Host '    -ShowBrowser: cada busqueda abre una ventana del navegador para que veas lo que hace.' -ForegroundColor DarkGray }

    $pattern = 'Worker de b|Tomada la b|squeda .* (en|fall)|ofertas|abandonadas|ERROR|\[JobSearch\]'
    $files = @((Join-Path $logs 'jobs-service.log'), (Join-Path $logs 'scraper.log'), (Join-Path $logs 'scraper.err.log'))
    # Un job por archivo: Get-Content -Wait sigue cada log desde el final
    $followJobs = foreach ($file in $files) {
        Start-Job -ArgumentList $file, $pattern -ScriptBlock {
            param($file, $pattern)
            $name = [System.IO.Path]::GetFileNameWithoutExtension($file)
            Get-Content $file -Tail 0 -Wait -Encoding UTF8 | Where-Object { $_ -match $pattern } | ForEach-Object {
                # Sin colores ANSI ni el prefijo de Spring (fecha, nivel, hilo, clase): solo el mensaje
                $line = $_ -replace '\x1b\[[0-9;]*m', ''
                # Spring: "fecha nivel pid --- [servicio] [hilo] clase : mensaje"; Nest: "[Nest] pid - fecha LOG [JobSearch] mensaje"
                $line = $line -replace '^\[Nest\].*?\[JobSearch\]\s*', '' -replace '^.*\]\s+\S+\s+:\s', ''
                "    [$name] $line"
            }
        }
    }

    $reported = @{}
    while ($true) {
        $followJobs | Receive-Job | Write-Host
        foreach ($entry in $started) {
            if ($entry.Process.HasExited -and -not $reported[$entry.Name]) {
                $reported[$entry.Name] = $true
                Write-Host "    $($entry.Name) se detuvo (codigo $($entry.Process.ExitCode)). Revisa logs\$($entry.Name).log" -ForegroundColor Red
            }
        }
        Start-Sleep -Milliseconds 500
    }
} finally {
    # Ctrl+C llega aqui: PowerShell ejecuta el finally antes de salir
    $followJobs | Stop-Job -PassThru -ErrorAction SilentlyContinue | Remove-Job -Force -ErrorAction SilentlyContinue
    if ($started.Count -gt 0) {
        Write-Host ''
        Write-Host '==> Deteniendo servicios' -ForegroundColor Cyan
        Stop-Services
        if (-not $KeepInfra) {
            Write-Host '==> Deteniendo Postgres, RabbitMQ y Redis (los datos se conservan)' -ForegroundColor Cyan
            docker @compose stop | Out-Null
        }
        Write-Host '    Listo.' -ForegroundColor Green
    }
}
