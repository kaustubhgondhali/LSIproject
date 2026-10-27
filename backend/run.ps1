# Starts the LSI backend.
# Usage (PowerShell):  cd backend ; .\run.ps1          -> starts on SERVER_PORT (default 8080)
#                      cd backend ; .\run.ps1 -Stop    -> just stops a running backend
#
# Settings come from backend/.env when that file exists; variables already set in your
# environment (e.g. JWT_SECRET) always win, so .env is optional.
#
# Before starting it frees the port: "mvn spring-boot:run" launches the app in a SEPARATE
# java.exe, and closing the terminal window leaves that process alive still holding the port,
# which is what produces "Web server failed to start. Port 8080 was already in use."
param([switch]$Stop)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

# ---- optional .env ---------------------------------------------------------------------------
$envFile = Join-Path $root ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*[^#\s]' -and $_ -match '=' } | ForEach-Object {
        $k, $v = $_ -split '=', 2
        $k = $k.Trim()
        # Never override a variable that is already set in the environment.
        if (-not [Environment]::GetEnvironmentVariable($k)) {
            [Environment]::SetEnvironmentVariable($k, $v.Trim(), "Process")
        }
    }
    Write-Host "Loaded settings from .env" -ForegroundColor DarkGray
} else {
    Write-Host "No .env file - using the variables already in your environment." -ForegroundColor DarkGray
}

if (-not [Environment]::GetEnvironmentVariable('JWT_SECRET')) {
    Write-Error "JWT_SECRET is not set. Set it as an environment variable or put it in backend/.env. It must stay the SAME every run: saved SMTP and payment-gateway credentials are encrypted with a key derived from it."
}

$port = [Environment]::GetEnvironmentVariable('SERVER_PORT')
if (-not $port) { $port = '8080' }

# ---- free the port ---------------------------------------------------------------------------
function Stop-LsiBackend([int]$Port) {
    $stopped = 0
    foreach ($conn in @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)) {
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$($conn.OwningProcess)" -ErrorAction SilentlyContinue
        if (-not $proc) { continue }
        # Only ever stop our own backend - never some unrelated program that happens to use the port.
        if ($proc.CommandLine -like "*LsiBackendApplication*" -or $proc.CommandLine -like "*lsi-backend*") {
            $parent = Get-CimInstance Win32_Process -Filter "ProcessId=$($proc.ParentProcessId)" -ErrorAction SilentlyContinue
            Write-Host "Stopping previous LSI backend (PID $($proc.ProcessId)) on port $Port..." -ForegroundColor Yellow
            Stop-Process -Id $proc.ProcessId -Force -Confirm:$false -ErrorAction SilentlyContinue
            if ($parent -and $parent.CommandLine -like "*spring-boot*") {
                Stop-Process -Id $parent.ProcessId -Force -Confirm:$false -ErrorAction SilentlyContinue
            }
            $stopped++
        } else {
            Write-Error "Port $Port is used by PID $($proc.ProcessId) ($($proc.Name)), which is NOT the LSI backend. Close that program or set SERVER_PORT to a free port."
        }
    }
    if ($stopped -gt 0) { Start-Sleep -Seconds 2 }
    return $stopped
}

$freed = Stop-LsiBackend -Port ([int]$port)

if ($Stop) {
    Write-Host $(if ($freed -gt 0) { "LSI backend stopped." } else { "No LSI backend was running on port $port." }) -ForegroundColor Cyan
    return
}

Write-Host "Starting LSI backend on port $port..." -ForegroundColor Cyan
mvn spring-boot:run
