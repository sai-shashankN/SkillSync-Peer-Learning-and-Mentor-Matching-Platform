[CmdletBinding()]
param(
    [switch]$Bootstrap,
    [switch]$SkipInfra,
    [switch]$SkipFrontend
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot\local-dev-common.ps1"

function Write-Step {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Host ""
    Write-Host "==> $Message"
}

if ($Bootstrap) {
    Write-Step "Running local bootstrap first"
    & "$PSScriptRoot\setup-local.ps1"
}

Ensure-Directory -Path $script:RuntimeDir
$state = Load-ProcessState

if (-not $SkipInfra) {
    Write-Step "Starting Docker infrastructure"
    Push-Location $script:InfraDir
    try {
        & docker compose up -d
    } finally {
        Pop-Location
    }
}

$startupOrder = @(
    "config-server",
    "eureka-server",
    "auth-service",
    "user-service",
    "mentor-service",
    "skill-service",
    "session-service",
    "payment-service",
    "review-service",
    "group-service",
    "notification-service",
    "audit-service",
    "api-gateway"
)

if (-not $SkipFrontend) {
    $startupOrder += "frontend"
}

foreach ($serviceName in $startupOrder) {
    $service = Get-ManagedService -Name $serviceName
    $state = Start-ManagedService -Service $service -State $state
    Save-ProcessState -Entries $state

    if ($serviceName -eq "audit-service") {
        Write-Step "Waiting for Eureka registrations"
        Wait-ForEurekaRegistrations
        Write-Host "All core backend services are registered in Eureka."
    }
}

Save-ProcessState -Entries $state

Write-Step "Startup complete"
Write-Host "Logs are being written to $script:RuntimeDir"
Write-Host "Run .\scripts\stop-local.ps1 to stop the processes started by this script."
