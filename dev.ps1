#Requires -Version 5.1
<#
.SYNOPSIS
    Orquestador de desarrollo de aplicafacil: levanta server, front y mcp-server
    en segundo plano, cada uno con su propio archivo de log en ./logs/.

.DESCRIPTION
    Cada servicio escribe en un archivo individual (logs/<servicio>.log) para
    poder consultarlos por separado. Los PIDs se registran en logs/.pids.json.
    Los procesos quedan desacoplados del terminal: siguen corriendo aunque
    cierres la ventana. Para detenerlos usa: .\dev.ps1 stop

.EXAMPLE
    .\dev.ps1                    # Inicia los 3 servicios (accion por defecto)
    .\dev.ps1 start              # Igual que lo anterior
    .\dev.ps1 start front        # Inicia solo el front
    .\dev.ps1 logs               # Lista los logs disponibles
    .\dev.ps1 logs server        # Sigue en vivo el log del server (Ctrl+C para salir)
    .\dev.ps1 status             # Estado de los servicios
    .\dev.ps1 stop               # Detiene todos los servicios
    .\dev.ps1 stop front         # Detiene solo el front
    .\dev.ps1 restart            # Detiene y vuelve a iniciar todo
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet("start", "stop", "restart", "status", "logs")]
    [string]$Action = "start",

    [Parameter(Position = 1)]
    [string]$Service = "all"
)

$ErrorActionPreference = "Stop"

$Root    = Split-Path -Parent $MyInvocation.MyCommand.Path
$LogDir  = Join-Path $Root "logs"
$PidFile = Join-Path $LogDir ".pids.json"

# ------------------------------------------------------------------
# Definicion de servicios: nombre -> carpeta y argumentos de npm
# ------------------------------------------------------------------
$Services = [ordered]@{
    server = @{
        dir  = Join-Path $Root "packages\aplicafacil-server"
        args = "run start:dev"
    }
    front  = @{
        dir  = Join-Path $Root "packages\aplicafacil-front"
        args = "run dev"
    }
    mcp    = @{
        dir  = Join-Path $Root "packages\mcp-server"
        args = "run dev"
    }
}

# Mapa pid -> StreamWriter (para escribir stdout+stderr en el mismo log)
$script:logWriters = @{}

# ------------------------------------------------------------------
# Helpers de PIDs
# ------------------------------------------------------------------
function Get-ServicePids {
    $result = @{}
    if (Test-Path $PidFile) {
        try {
            $json = Get-Content $PidFile -Raw | ConvertFrom-Json
            foreach ($prop in $json.PSObject.Properties) {
                $result[$prop.Name] = [int]$prop.Value
            }
        } catch {
            # PID file corrupto: se ignora y se sobreescribe al guardar
        }
    }
    return $result
}

function Save-ServicePids([hashtable]$pids) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
    $pids | ConvertTo-Json | Set-Content -Path $PidFile -Encoding UTF8
}

function Test-ProcessAlive([int]$id) {
    if (-not $id) { return $false }
    return [bool](Get-Process -Id $id -ErrorAction SilentlyContinue)
}

# ------------------------------------------------------------------
# Iniciar / detener servicios
# ------------------------------------------------------------------
function Start-OneService([string]$name) {
    $svc = $Services[$name]
    if (-not $svc) {
        Write-Host "Servicio desconocido: $name (usa: server, front, mcp)" -ForegroundColor Red
        return
    }

    $pids = Get-ServicePids
    $existing = $pids[$name]
    if ($existing -and (Test-ProcessAlive $existing)) {
        Write-Host "-- $name ya esta corriendo (PID $existing)" -ForegroundColor Yellow
        return
    }

    $npm = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source
    if (-not $npm) {
        Write-Host "[!] npm.cmd no encontrado en PATH. Instala Node.js." -ForegroundColor Red
        return
    }

    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
    $logFile = Join-Path $LogDir "$name.log"

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName           = $npm
    $psi.Arguments          = $svc.args
    $psi.WorkingDirectory   = $svc.dir
    $psi.UseShellExecute    = $false
    $psi.CreateNoWindow     = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError  = $true

    $proc = [System.Diagnostics.Process]::Start($psi)

    # Un solo archivo de log: se fusiona stdout + stderr
    $writer = New-Object System.IO.StreamWriter($logFile, $true, [System.Text.Encoding]::UTF8)
    $writer.AutoFlush = $true
    $script:logWriters[$proc.Id] = $writer

    $outHandler = [System.Diagnostics.DataReceivedEventHandler]{
        param($s, $e)
        if ($e.Data) {
            $w = $script:logWriters[$s.Id]
            if ($w) { $w.WriteLine($e.Data) }
        }
    }
    $errHandler = [System.Diagnostics.DataReceivedEventHandler]{
        param($s, $e)
        if ($e.Data) {
            $w = $script:logWriters[$s.Id]
            if ($w) { $w.WriteLine("[err] " + $e.Data) }
        }
    }

    $proc.add_OutputDataReceived($outHandler)
    $proc.add_ErrorDataReceived($errHandler)
    $proc.BeginOutputReadLine()
    $proc.BeginErrorReadLine()

    $pids[$name] = $proc.Id
    Save-ServicePids $pids

    Write-Host "==> $name iniciado (PID $($proc.Id)) -> logs\$name.log" -ForegroundColor Green
}

function Stop-OneService([string]$name) {
    $pids = Get-ServicePids
    $id = $pids[$name]

    if ($id -and (Test-ProcessAlive $id)) {
        Write-Host "[-] Deteniendo $name (PID $id)..." -ForegroundColor Yellow
        # /T mata todo el arbol de procesos (npm -> node -> hijos)
        & taskkill /PID $id /T /F 2>&1 | Out-Null

        $w = $script:logWriters[$id]
        if ($w) {
            $w.Dispose()
            $script:logWriters.Remove($id)
        }
    } else {
        Write-Host "-- $name no esta corriendo" -ForegroundColor DarkGray
    }

    $pids.Remove($name)
    Save-ServicePids $pids
}

# ------------------------------------------------------------------
# Estado y logs
# ------------------------------------------------------------------
function Show-Status {
    $pids = Get-ServicePids

    Write-Host ""
    Write-Host "Servicios de desarrollo de aplicafacil" -ForegroundColor Cyan
    Write-Host ("-" * 52)
    foreach ($name in $Services.Keys) {
        $id    = $pids[$name]
        $alive = $id -and (Test-ProcessAlive $id)
        $logFile = Join-Path $LogDir "$name.log"
        $size = if (Test-Path $logFile) { "{0:N0} KB" -f ((Get-Item $logFile).Length / 1KB) } else { "-" }

        if ($alive) {
            Write-Host ("  {0,-7} PID {1,-7} {2,-9} [x] corriendo" -f $name, $id, $size) -ForegroundColor Green
        } else {
            Write-Host ("  {0,-7} {1,-14} {2,-9} [ ] detenido" -f $name, "-", $size) -ForegroundColor DarkGray
        }
    }
    Write-Host ("-" * 52)
    Write-Host "Logs: .\dev.ps1 logs <servicio>  |  Detener todo: .\dev.ps1 stop"
}

function Show-Logs([string]$name) {
    if ($name -eq "all") {
        Write-Host "Logs disponibles en $LogDir :" -ForegroundColor Cyan
        Get-ChildItem $LogDir -Filter *.log -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Format-Table Name, @{L="Tamanio"; E={"$([math]::Round($_.Length/1KB,1)) KB"}}, LastWriteTime -AutoSize
        Write-Host ""
        Write-Host "Para seguir uno en vivo: .\dev.ps1 logs <server|front|mcp>" -ForegroundColor Yellow
        return
    }

    $svc = $Services[$name]
    if (-not $svc) {
        Write-Host "Servicio desconocido: $name (usa: server, front, mcp)" -ForegroundColor Red
        return
    }

    $logFile = Join-Path $LogDir "$name.log"
    if (-not (Test-Path $logFile)) {
        Write-Host "Todavia no existe el log de $name. Inicia los servicios primero: .\dev.ps1 start" -ForegroundColor Yellow
        return
    }

    Write-Host "Siguiendo logs\$name.log (Ctrl+C para salir)..." -ForegroundColor Cyan
    Get-Content $logFile -Wait -Tail 30
}

# ------------------------------------------------------------------
# Dispatch
# ------------------------------------------------------------------
$targets = if ($Service -eq "all") { @($Services.Keys) } else { @($Service) }

switch ($Action) {
    "start"   { foreach ($t in $targets) { Start-OneService $t } }
    "stop"    { foreach ($t in $targets) { Stop-OneService $t } }
    "restart" {
        foreach ($t in $targets) { Stop-OneService $t }
        foreach ($t in $targets) { Start-OneService $t }
    }
    "status"  { Show-Status }
    "logs"    { Show-Logs $Service }
}
