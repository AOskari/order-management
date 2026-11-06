<#
.SYNOPSIS
    Starts the local development environment.

.DESCRIPTION
    Creates .env from .env.example when missing, then starts PostgreSQL, Keycloak and the
    application and waits for their health checks. PostgreSQL roles and the Keycloak realm
    are created by the containers themselves, so nothing runs after 'up'. Safe to run
    repeatedly.

    PowerShell equivalent of scripts/init-local-env.sh.

.PARAMETER Reset
    Remove the containers and the database volume first (destroys local data).

.EXAMPLE
    .\scripts\local\init-local-env.ps1

.EXAMPLE
    .\scripts\local\init-local-env.ps1 -Reset
#>

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location -LiteralPath $repoRoot

function Write-Log
{
    param([string] $Message)
    Write-Host "[init] $Message"
}

function Stop-Init
{
    param([string] $Message)
    [Console]::Error.WriteLine("[init] ERROR: $Message")
    exit 1
}

function Show-Usage
{
    Write-Host @'
Starts the local development environment:
  - creates .env from .env.example when missing
  - starts PostgreSQL, Keycloak and the application and waits for their health checks

PostgreSQL roles and the Keycloak realm are created by the containers themselves.

Safe to run repeatedly. Run it from anywhere; paths resolve from the repository root.

Usage: .\scripts\local\init-local-env.ps1 [-Reset]
  -Reset   remove the containers and the database volume first (destroys local data)
'@
}

$reset = $false
foreach ($argument in $args)
{
    switch -Regex ($argument)
    {
        '^(--reset|-Reset)$'
        {
            $reset = $true
        }
        '^(--help|-h|-Help|/\?)$'
        {
            Show-Usage
            exit 0
        }
        default
        {
            [Console]::Error.WriteLine("[init] ERROR: unknown option: $argument")
            Show-Usage
            exit 2
        }
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue))
{
    Stop-Init 'docker is required'
}
& docker compose version *> $null
if ($LASTEXITCODE -ne 0)
{
    Stop-Init 'the docker compose plugin is required'
}
& docker info *> $null
if ($LASTEXITCODE -ne 0)
{
    Stop-Init 'the Docker daemon is not running'
}

$envPath = Join-Path $repoRoot '.env'
$examplePath = Join-Path $repoRoot '.env.example'

if (-not (Test-Path -LiteralPath $envPath))
{
    Copy-Item -LiteralPath $examplePath -Destination $envPath
    Write-Log 'created .env from .env.example'
}

# Windows editors write CRLF; the .env import does not tolerate a stray \r.
$envText = [System.IO.File]::ReadAllText($envPath)
if ($envText.Contains("`r`n"))
{
    [System.IO.File]::WriteAllText($envPath, $envText.Replace("`r`n", "`n"))
    Write-Log 'converted .env to LF line endings'
}

if ($reset)
{
    Write-Log 'removing containers and the database volume'
    & docker compose down -v
    if ($LASTEXITCODE -ne 0)
    {
        Stop-Init 'removing the containers failed'
    }
}

Write-Log 'starting PostgreSQL, Keycloak and the application'
& docker compose up -d --wait --wait-timeout 300
if ($LASTEXITCODE -ne 0)
{
    Stop-Init "the containers did not become healthy - inspect 'docker compose logs'"
}

$appPort = '8080'
foreach ($line in Get-Content -LiteralPath $envPath)
{
    if ($line -match '^APP_PORT=(.+)$')
    {
        $appPort = $Matches[1].Trim()
    }
}
if ($env:APP_PORT)
{
    $appPort = $env:APP_PORT
}

Write-Log "application is available at http://localhost:$appPort/api/v1/orders"
Write-Log 'token: .\scripts\local\get-token.ps1 -Username customer1 -Password password'
