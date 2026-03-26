[CmdletBinding()]
param(
    [switch]$SkipBackend,
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

function Show-JavaWarning {
    $javaVersionOutput = cmd.exe /c "java -version 2>&1"
    $javaLine = ($javaVersionOutput | Select-Object -First 1)
    if ($javaLine -notmatch '"(?<major>\d+)') {
        return
    }

    $majorVersion = [int]$Matches.major
    if ($majorVersion -ne 17) {
        Write-Warning "SkillSync is documented for Java 17, but this machine is currently using Java $majorVersion. Continuing anyway."
    }
}

Ensure-Directory -Path $script:RuntimeDir

Write-Step "Checking local toolchain"
Get-Command mvn.cmd | Out-Null
Get-Command npm.cmd | Out-Null
Get-Command docker | Out-Null
Show-JavaWarning

if (-not $SkipBackend) {
    Write-Step "Building backend modules and installing shared artifacts"
    Push-Location $script:BackendDir
    try {
        & mvn.cmd -DskipTests install
    } finally {
        Pop-Location
    }
}

if (-not $SkipFrontend) {
    Write-Step "Installing frontend dependencies"
    Push-Location $script:FrontendDir
    try {
        & npm.cmd install
    } finally {
        Pop-Location
    }
}

Write-Host ""
Write-Host "Local setup completed."
