<#
.SYNOPSIS
    Initializes the local development infrastructure.

.DESCRIPTION
    Creates .env from .env.example when missing, starts PostgreSQL and Keycloak and waits
    for their health checks, applies the oms_owner / oms_app roles and grants, and verifies
    the database logins and the Keycloak realm. Safe to run repeatedly.

    PowerShell equivalent of scripts/init-local-env.sh.

.PARAMETER Reset
    Remove the containers and the database volume first (destroys local data).

.EXAMPLE
    .\scripts\init-local-env.ps1

.EXAMPLE
    .\scripts\init-local-env.ps1 -Reset
#>

$repoRoot = Split-Path -Parent $PSScriptRoot
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
Initializes the local development infrastructure:
  - creates .env from .env.example when missing
  - starts PostgreSQL and Keycloak and waits for their health checks
  - applies the oms_owner / oms_app roles and grants (docker/local/postgres/init/01-roles.sh)
  - verifies database logins and the Keycloak realm

Safe to run repeatedly. Run it from anywhere; paths resolve from the repository root.

Usage: .\scripts\init-local-env.ps1 [-Reset]
  -Reset   remove the containers and the database volume first (destroys local data)
'@
}

function Read-EnvFile
{
    param([string] $Path)
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path)
    {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#'))
        {
            continue
        }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1)
        {
            continue
        }
        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        $values[$key] = $value
    }
    return $values
}

function Test-Login
{
    param([string] $Role, [string] $Password)
    & docker compose exec -T -e "PGPASSWORD=$Password" postgres `
        psql -h 127.0.0.1 -U $Role -d $script:dbName -tAc 'select 1' *> $null
    if ($LASTEXITCODE -ne 0)
    {
        Stop-Init "$Role cannot log in to $($script:dbName) with the password from .env"
    }
    Write-Log "$Role can log in to $($script:dbName)"
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

$envText = [System.IO.File]::ReadAllText($envPath)
if ($envText.Contains("`r`n"))
{
    [System.IO.File]::WriteAllText($envPath, $envText.Replace("`r`n", "`n"))
    Write-Log 'converted .env to LF line endings'
}

$values = Read-EnvFile -Path $envPath
$defaults = Read-EnvFile -Path $examplePath

$required = @('DB_URL', 'DB_APP_USER', 'DB_APP_PASSWORD', 'DB_OWNER_USER',
    'DB_OWNER_PASSWORD', 'OIDC_ISSUER_URI')

foreach ($name in $required)
{
    if ($values.ContainsKey($name) -and $values[$name].Length -gt 0)
    {
        Set-Item -Path "Env:$name" -Value $values[$name]
        continue
    }
    if (-not $defaults.ContainsKey($name) -or $defaults[$name].Length -eq 0)
    {
        Stop-Init ".env is missing $name and .env.example has no value for it"
    }
    [System.IO.File]::AppendAllText($envPath, "$name=$($defaults[$name])`n")
    $values[$name] = $defaults[$name]
    Set-Item -Path "Env:$name" -Value $defaults[$name]
    Write-Log "added $name to .env from .env.example"
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

Write-Log 'starting PostgreSQL and Keycloak'
& docker compose up -d --wait --wait-timeout 240
if ($LASTEXITCODE -ne 0)
{
    Stop-Init "containers did not become healthy - inspect 'docker compose logs'"
}

$dbName = (& docker compose exec -T postgres printenv POSTGRES_DB 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($dbName))
{
    Stop-Init 'cannot read POSTGRES_DB from the postgres container'
}

Write-Log 'applying database roles and grants'
& cmd /c 'docker compose exec -T postgres sh -s < docker\local\postgres\init\01-roles.sh > NUL'
if ($LASTEXITCODE -ne 0)
{
    Stop-Init 'applying the database roles failed'
}

Test-Login -Role $env:DB_OWNER_USER -Password $env:DB_OWNER_PASSWORD
Test-Login -Role $env:DB_APP_USER -Password $env:DB_APP_PASSWORD

$issuer = $env:OIDC_ISSUER_URI
if ([string]::IsNullOrWhiteSpace($issuer))
{
    $issuer = 'http://localhost:8081/realms/order-management'
}
try
{
    Invoke-WebRequest -Uri $issuer -UseBasicParsing -TimeoutSec 10 | Out-Null
    Write-Log "Keycloak realm is available at $issuer"
} catch
{
    Stop-Init "Keycloak realm is not available at $issuer - try '.\scripts\init-local-env.ps1 -Reset'"
}

Write-Log 'local infrastructure is ready; start the application with .\mvnw.cmd spring-boot:run'
