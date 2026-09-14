# Starts the LSI backend with the variables from backend/.env
# Usage (PowerShell):  cd backend ; .\run.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) {
    Write-Error ".env not found. Copy .env.example to .env and fill in the values."
}
Get-Content $envFile | Where-Object { $_ -match '^\s*[^#\s]' -and $_ -match '=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim(), "Process")
}
Set-Location $root
Write-Host "Starting LSI backend on port $([Environment]::GetEnvironmentVariable('SERVER_PORT'))..." -ForegroundColor Cyan
mvn spring-boot:run
