[CmdletBinding()]
param(
    [switch]$StopInfra
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot\local-dev-common.ps1"

function Write-Step {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Host ""
    Write-Host "==> $Message"
}

$state = Load-ProcessState
if (-not $state.Count) {
    Write-Host "No tracked local processes were found."
} else {
    Write-Step "Stopping tracked local processes"

    foreach ($entry in $state | Sort-Object Port -Descending) {
        if (-not $entry.StartedByScript) {
            continue
        }

        $processId = [int]$entry.Pid
        if (-not (Get-Process -Id $processId -ErrorAction SilentlyContinue)) {
            Write-Host "$($entry.Name) is not running anymore."
            continue
        }

        try {
            Stop-Process -Id $processId -Force
            Write-Host "Stopped $($entry.Name) (PID $processId)."
        } catch {
            Write-Warning "Could not stop $($entry.Name) (PID $processId): $($_.Exception.Message)"
        }
    }

    Remove-Item -Path $script:StatePath -ErrorAction SilentlyContinue
}

if ($StopInfra) {
    Write-Step "Stopping Docker infrastructure"
    Push-Location $script:InfraDir
    try {
        & docker compose down
    } finally {
        Pop-Location
    }
}

Write-Host ""
Write-Host "Local shutdown complete."
