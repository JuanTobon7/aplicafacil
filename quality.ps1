<#
.SYNOPSIS
  Verifica CONSTRAINTS.md. Tres etapas segun el momento:

.DESCRIPTION
  -Fast   mientras se edita (segundos): piso (floor-guard) + oxlint + Ruff.
  (nada)  al terminar una tarea (hasta ~5 min): lo anterior + PMD + ratchet + ArchUnit + import-linter
          + pruebas de los proyectos cambiados (Java con Testcontainers, vitest, pytest).
  -Full   antes de integrar: todas las pruebas + gitleaks + osv-scanner (npm, Python y SBOM de Java).

  Sale con 1 si algo bloquea. Las metricas en periodo de aviso (ver CONSTRAINTS.md) solo avisan.

.EXAMPLE
  .\quality.cmd
  .\quality.cmd -Fast
  .\quality.cmd -Full
  .\quality.cmd -Base origin/develop
#>
param(
    [switch]$Fast,
    [switch]$Full,
    [string]$Base = 'origin/main'
)

$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$backend = Join-Path $root 'services\backend'
$results = [ordered]@{}
$started = Get-Date

function Step($message) { Write-Host "`n==> $message" -ForegroundColor Cyan }
function Record($name, [bool]$ok) { $results[$name] = $ok }

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

# Binarios de winget (el PATH de una terminal abierta antes de instalarlos no los ve)
function Find-Tool($name, $package) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidate = Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages\$package\$name.exe"
    if (Test-Path $candidate) { return $candidate }
    return $null
}

# Docker Desktop expone otro pipe que el que usa docker-py (Testcontainers de Python)
if (-not $env:DOCKER_HOST) {
    $dockerHost = docker context inspect --format '{{.Endpoints.docker.Host}}' 2>$null
    if ($LASTEXITCODE -eq 0 -and $dockerHost) { $env:DOCKER_HOST = $dockerHost.Trim() }
}

# -- Que cambio respecto de la base ---------------------------------------------------------
$mergeBase = git -C $root merge-base $Base HEAD 2>$null
$changedFiles = @()
if ($mergeBase) {
    $changedFiles = @(git -C $root diff --name-only $mergeBase) + @(git -C $root ls-files --others --exclude-standard)
}
$javaModules = @('identity-service', 'linkedin-service', 'computrabajo-service', 'profile-service', 'jobs-service', 'automation-service',
                 'ai-config-service', 'platform/api-gateway', 'platform/config-server', 'platform/discovery-server',
                 'libs/platform-common', 'libs/platform-crypto', 'libs/linkedin-contract', 'libs/architecture-rules')
$changedJava = @($javaModules | Where-Object { $m = $_; $changedFiles | Where-Object { $_ -like "services/backend/$m/*" } } | Select-Object -Unique)
if ($changedFiles | Where-Object { $_ -match '^services/backend/(pom\.xml|service-parent/)' }) { $changedJava = $javaModules }
$changedScraper = [bool]($changedFiles | Where-Object { $_ -like 'services/scraper/*' })
$changedWeb = [bool]($changedFiles | Where-Object { $_ -like 'apps/web/*' })
$changedPython = [bool]($changedFiles | Where-Object { $_ -like 'services/mcp-gateway/*' })
if ($Full) { $changedJava = $javaModules; $changedScraper = $true; $changedWeb = $true; $changedPython = $true }

# -- 1. Piso -----------------------------------------------------------------------------------
Step "Piso (floor-guard contra $Base)"
node (Join-Path $root 'tools\quality\floor-guard.mjs') --base $Base
Record 'Piso' ($LASTEXITCODE -eq 0)

# -- 2. Linters rapidos --------------------------------------------------------------------------
Step 'oxlint (TypeScript) y Ruff (Python)'
foreach ($project in 'apps\web', 'services\scraper') {
    Push-Location (Join-Path $root $project)
    # Directo, sin npx: en Windows npx tarda segundos solo en arrancar
    try { & .\node_modules\.bin\oxlint.cmd -c .oxlintrc.json src | Select-Object -Last 3 } finally { Pop-Location }
}
$mcp = Join-Path $root 'services\mcp-gateway'
& (Join-Path $mcp '.venv\Scripts\ruff.exe') check (Join-Path $mcp 'src') --exit-zero --output-format concise
# Los linters solo informan aqui; el ratchet decide si empeoro (etapa de tarea)

if ($Fast) {
    # En -Fast el ratchet no corre: lo que se ve arriba es informativo
} else {
    $jdk = Find-Jdk25
    if (-not $jdk) { Write-Host 'No se encontro un JDK 25.' -ForegroundColor Red; exit 2 }
    $env:JAVA_HOME = $jdk

    # -- 3. Tamano, complejidad y acoplamiento (ratchet) ------------------------------------------
    Step 'PMD (Java) y ratchet contra tools/quality/baseline.json'
    Push-Location $backend
    try {
        # Reportes viejos fuera: si PMD falla, el ratchet no puede leer datos de otra corrida y dar un verde falso
        Get-ChildItem -Path . -Filter pmd.xml -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.Directory.Name -eq 'target' } | Remove-Item -Force
        # test-compile: el reactor construye las librerias internas (architecture-rules) antes de que PMD resuelva
        & .\mvnw.cmd -B -q -o '-DskipTests' test-compile pmd:pmd 2>&1 | Where-Object { $_ -notmatch '^WARNING|Unsafe' } | Select-Object -Last 10
        $pmdOk = $LASTEXITCODE -eq 0
    } finally { Pop-Location }
    if ($pmdOk) {
        node (Join-Path $root 'tools\quality\ratchet.mjs')
        Record 'Tamano y complejidad (ratchet)' ($LASTEXITCODE -eq 0)
    } else {
        Write-Host '    PMD fallo: sin medicion no hay ratchet' -ForegroundColor Red
        Record 'Tamano y complejidad (ratchet)' $false
    }

    # -- 4. Capas --------------------------------------------------------------------------------
    Step 'Capas: ArchUnit (todos los servicios) e import-linter (Python)'
    Push-Location $backend
    try {
        # Comillas: sin ellas PowerShell parte "-Dsurefire.failIfNoSpecifiedTests=false" en el punto
        & .\mvnw.cmd -B -q -o -fae '-Dtest=ArchitectureTest' '-Dsurefire.failIfNoSpecifiedTests=false' `
            -pl 'identity-service,linkedin-service,computrabajo-service,profile-service,jobs-service,automation-service,ai-config-service' -am test 2>&1 |
            Where-Object { $_ -match 'Tests run|FAIL|violated|Violation' }
        Record 'Capas Java (ArchUnit)' ($LASTEXITCODE -eq 0)
    } finally { Pop-Location }
    Push-Location $mcp
    try {
        $env:PYTHONPATH = 'src'
        # Sin la herramienta la etapa falla a la vista (antes la excepción la sacaba del resumen)
        if (Test-Path .\.venv\Scripts\lint-imports.exe) {
            & .\.venv\Scripts\lint-imports.exe 2>&1 | Where-Object { $_ -match 'KEPT|BROKEN|not allowed|->' }
            Record 'Capas Python (import-linter)' ($LASTEXITCODE -eq 0)
        } else {
            Write-Host '    falta import-linter: en services/mcp-gateway corre "uv sync" (con las dependencias de desarrollo)' -ForegroundColor Yellow
            Record 'Capas Python (import-linter)' $false
        }
    } finally { Pop-Location; Remove-Item env:PYTHONPATH -ErrorAction SilentlyContinue }

    # -- 5. Pruebas de lo cambiado ---------------------------------------------------------------
    if ($changedJava.Count -gt 0) {
        Step "Pruebas Java: $($changedJava -join ', ')"
        Push-Location $backend
        try {
            & .\mvnw.cmd -B -q -o -fae -pl ($changedJava -join ',') -am test 2>&1 | Where-Object { $_ -match 'Tests run:|FAIL|ERROR\]' } | Select-Object -Last 15
            Record 'Pruebas Java' ($LASTEXITCODE -eq 0)
        } finally { Pop-Location }
    }
    if ($changedScraper) {
        Step 'Pruebas del scraper (vitest + typecheck, incluye arquitectura)'
        Push-Location (Join-Path $root 'services\scraper')
        try { npm run --silent typecheck; $ok = $LASTEXITCODE -eq 0; npx --no-install vitest run 2>&1 | Select-Object -Last 4; Record 'Pruebas scraper' ($ok -and $LASTEXITCODE -eq 0) } finally { Pop-Location }
    }
    if ($changedWeb) {
        Step 'Front (typecheck)'
        Push-Location (Join-Path $root 'apps\web')
        try { npm run --silent typecheck; Record 'Front (typecheck)' ($LASTEXITCODE -eq 0) } finally { Pop-Location }
    }
    if ($changedPython) {
        Step 'Pruebas del mcp-gateway (pytest, RabbitMQ real si hay Docker)'
        Push-Location $mcp
        try { & .\.venv\Scripts\python.exe -m pytest -q -p no:warnings 2>&1 | Select-Object -Last 3; Record 'Pruebas mcp-gateway' ($LASTEXITCODE -eq 0) } finally { Pop-Location }
    }
}

# -- 6. Seguridad (solo -Full) -----------------------------------------------------------------
if ($Full) {
    Step 'Secretos (gitleaks)'
    $gitleaks = Find-Tool 'gitleaks' 'Gitleaks.Gitleaks_Microsoft.Winget.Source_8wekyb3d8bbwe'
    if ($gitleaks) {
        & $gitleaks dir $root --config (Join-Path $root '.gitleaks.toml') --redact --no-banner --exit-code 1 2>&1 | Select-Object -Last 2
        Record 'Secretos' ($LASTEXITCODE -eq 0)
    } else { Write-Host '    gitleaks no esta instalado (winget install Gitleaks.Gitleaks)' -ForegroundColor Yellow; Record 'Secretos' $false }

    Step 'Dependencias (osv-scanner): npm, Python y SBOM de Java'
    $osv = Find-Tool 'osv-scanner' 'Google.OSVScanner_Microsoft.Winget.Source_8wekyb3d8bbwe'
    if ($osv) {
        & $osv scan source --lockfile=(Join-Path $root 'apps\web\package-lock.json') --lockfile=(Join-Path $root 'services\scraper\package-lock.json') `
            --lockfile=(Join-Path $mcp 'uv.lock') 2>&1 | Select-Object -Last 3
        Record 'Dependencias npm/Python' ($LASTEXITCODE -eq 0)
        Push-Location $backend
        try {
            & .\mvnw.cmd -B -q org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom '-DoutputFormat=json' '-DoutputName=bom.cdx' 2>&1 | Out-Null
            & $osv scan source --lockfile=target\bom.cdx.json 2>&1 | Select-String 'GHSA|CVE|No issues' | Select-Object -First 20
            # Medido, no exigido (CONSTRAINTS.md): se informa, no bloquea, hasta subir Spring Boot
        } finally { Pop-Location }
    } else { Write-Host '    osv-scanner no esta instalado (winget install Google.OSVScanner)' -ForegroundColor Yellow; Record 'Dependencias npm/Python' $false }
}

# -- Resumen ------------------------------------------------------------------------------------
Step ("Resumen ({0:n0} s)" -f ((Get-Date) - $started).TotalSeconds)
foreach ($entry in $results.GetEnumerator()) {
    if ($entry.Value) { Write-Host "    OK     $($entry.Key)" -ForegroundColor Green }
    else { Write-Host "    FALLO  $($entry.Key)" -ForegroundColor Red }
}
if ($results.Values -contains $false) { exit 1 }
exit 0
