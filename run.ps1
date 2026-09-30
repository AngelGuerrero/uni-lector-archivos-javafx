<#
.SYNOPSIS
    Levanta el Lector de archivos en Docker y abre el navegador.

.EXAMPLE
    .\run.ps1
    Construye si hace falta, arranca el contenedor y abre la aplicacion.

.EXAMPLE
    .\run.ps1 -Build
    Fuerza reconstruir la imagen desde cero.

.EXAMPLE
    .\run.ps1 -Stop
    Detiene el contenedor y lo elimina.
#>
[CmdletBinding()]
param(
    [switch]$Build,
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$url = 'http://localhost:6080'

if ($Stop) {
    docker compose down
    exit 0
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error 'No se encontro Docker. Instala Docker Desktop y vuelve a intentarlo.'
}

if ($Build) {
    docker compose build --no-cache
    if ($LASTEXITCODE -ne 0) { Write-Error 'Fallo la construccion de la imagen.' }
}

docker compose up -d --build
if ($LASTEXITCODE -ne 0) { Write-Error 'No se pudo levantar el contenedor.' }

Write-Host 'Esperando a que la aplicacion responda' -NoNewline

foreach ($intento in 1..60) {
    try {
        $respuesta = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2
        if ($respuesta.StatusCode -eq 200) {
            Write-Host ''
            Write-Host "Listo: $url" -ForegroundColor Green
            Start-Process $url
            exit 0
        }
    } catch {
        # Todavia no levanta; se reintenta.
    }
    Write-Host '.' -NoNewline
    Start-Sleep -Seconds 1
}

Write-Host ''
Write-Warning 'La aplicacion no respondio a tiempo. Revisa los registros con:'
Write-Host '    docker compose logs'
exit 1
