<#
.SYNOPSIS
    Fetches an access token from the local Keycloak realm with the resource owner password
    grant.

.DESCRIPTION
    The token is written to stdout; the summary goes to stderr, so the script can be used
    in an assignment. PowerShell equivalent of scripts/local/get-token.sh.

.PARAMETER Username
    Realm user: customer1, customer2, support1 or admin1.

.PARAMETER Password
    Password of that user.

.PARAMETER Issuer
    Realm URL. Defaults to OIDC_ISSUER_URI, then .env, then
    http://host.docker.internal:8081/realms/order-management.

.PARAMETER Json
    Print the whole token response instead of the access token.

.EXAMPLE
    $token = .\scripts\local\get-token.ps1 -Username customer1 -Password password

.EXAMPLE
    .\scripts\local\get-token.ps1 -Username admin1 -Password password -Json
#>

param(
    [string] $Username,
    [string] $Password,
    [string] $Issuer,
    [switch] $Json,
    [switch] $Help
)

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location -LiteralPath $repoRoot

function Write-Log
{
    param([string] $Message)
    [Console]::Error.WriteLine("get-token: $Message")
}

function Stop-Token
{
    param([string] $Message)
    [Console]::Error.WriteLine("get-token: ERROR: $Message")
    exit 1
}

function Show-Usage
{
    Get-Help -Name $PSCommandPath -Detailed
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
        $values[$trimmed.Substring(0, $separator).Trim()] = $trimmed.Substring($separator + 1).Trim()
    }
    return $values
}

function Get-TokenClaim
{
    param([string] $Token, [string] $Name)
    $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    switch ($payload.Length % 4)
    {
        2
        { $payload += '=='
        }
        3
        { $payload += '='
        }
    }
    $json = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload))
    return ($json | ConvertFrom-Json).$Name
}

if ($Help)
{
    Show-Usage
    exit 0
}

if ([string]::IsNullOrWhiteSpace($Username))
{
    Stop-Token '-Username is required (customer1, customer2, support1, admin1)'
}
if ([string]::IsNullOrWhiteSpace($Password))
{
    Stop-Token '-Password is required'
}

if ([string]::IsNullOrWhiteSpace($Issuer))
{
    $Issuer = $env:OIDC_ISSUER_URI
}
$envPath = Join-Path $repoRoot '.env'
if ([string]::IsNullOrWhiteSpace($Issuer) -and (Test-Path -LiteralPath $envPath))
{
    $Issuer = (Read-EnvFile -Path $envPath)['OIDC_ISSUER_URI']
}
if ([string]::IsNullOrWhiteSpace($Issuer))
{
    $Issuer = 'http://host.docker.internal:8081/realms/order-management'
}

$tokenUrl = "$Issuer/protocol/openid-connect/token"
$form = @{
    grant_type = 'password'
    client_id  = 'order-management-api'
    scope      = 'openid'
    username   = $Username
    password   = $Password
}

try
{
    $response = Invoke-RestMethod -Uri $tokenUrl -Method Post -Body $form -TimeoutSec 30
} catch
{
    $detail = $_.ErrorDetails.Message
    if ([string]::IsNullOrWhiteSpace($detail))
    {
        $detail = $_.Exception.Message
    } else
    {
        try
        {
            $parsed = $detail | ConvertFrom-Json
            if (-not [string]::IsNullOrWhiteSpace($parsed.error_description))
            {
                $detail = $parsed.error_description
            }
        } catch
        {
            # Not JSON: keep the raw body.
        }
    }
    Stop-Token "token request failed: $detail"
}

if ($Json)
{
    $response | ConvertTo-Json -Compress
    exit 0
}

$token = $response.access_token
if ([string]::IsNullOrWhiteSpace($token))
{
    Stop-Token 'no access_token in the response'
}

try
{
    $subject = Get-TokenClaim -Token $token -Name 'sub'
} catch
{
    $subject = $null
}

if ([string]::IsNullOrWhiteSpace($subject))
{
    Write-Log "token for $Username (valid $($response.expires_in)s)"
} else
{
    Write-Log "token for $Username (customer $subject, valid $($response.expires_in)s)"
}

Write-Output $token
