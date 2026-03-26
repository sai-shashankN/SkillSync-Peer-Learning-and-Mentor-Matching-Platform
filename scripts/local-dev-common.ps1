Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:RepoRoot = Split-Path -Parent $PSScriptRoot
$script:BackendDir = Join-Path $script:RepoRoot "backend"
$script:FrontendDir = Join-Path $script:RepoRoot "frontend"
$script:InfraDir = Join-Path $script:RepoRoot "infra"
$script:RuntimeDir = Join-Path $script:RepoRoot "runtime-logs"
$script:StatePath = Join-Path $script:RuntimeDir "local-processes.json"

$script:ManagedServices = @(
    [pscustomobject]@{
        Name = "config-server"
        Port = 8888
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl config-server"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:8888/auth-service/default"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "eureka-server"
        Port = 8761
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl eureka-server"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:8761"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "auth-service"
        Port = 8081
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl auth-service"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:8081/actuator/health"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "user-service"
        Port = 8082
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl user-service"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:8082/actuator/health"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "mentor-service"
        Port = 8083
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl mentor-service"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:8083/actuator/health"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "skill-service"
        Port = 8084
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl skill-service"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:8084/actuator/health"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "session-service"
        Port = 8085
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl session-service"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:8085/actuator/health"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "payment-service"
        Port = 8086
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl payment-service"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:8086/actuator/health"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "review-service"
        Port = 8087
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl review-service"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:8087/actuator/health"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "group-service"
        Port = 8088
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl group-service"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:8088/actuator/health"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "notification-service"
        Port = 8089
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl notification-service"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:8089/actuator/health"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "audit-service"
        Port = 8090
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl audit-service"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:8090/actuator/health"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "api-gateway"
        Port = 8091
        Workdir = $script:BackendDir
        LaunchCommand = "mvn.cmd spring-boot:run -pl api-gateway"
        VerifyMode = "tcp"
        VerifyTarget = "8091"
        StartupSeconds = 180
    },
    [pscustomobject]@{
        Name = "frontend"
        Port = 3000
        Workdir = $script:FrontendDir
        LaunchCommand = "npm.cmd run dev"
        VerifyMode = "http"
        VerifyTarget = "http://localhost:3000"
        StartupSeconds = 120
    }
)

$script:EurekaServiceNames = @(
    "AUTH-SERVICE",
    "USER-SERVICE",
    "MENTOR-SERVICE",
    "SKILL-SERVICE",
    "SESSION-SERVICE",
    "PAYMENT-SERVICE",
    "REVIEW-SERVICE",
    "GROUP-SERVICE",
    "NOTIFICATION-SERVICE",
    "AUDIT-SERVICE"
)

function Ensure-Directory {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Get-LogPath {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][ValidateSet("out", "err")][string]$Stream
    )

    Ensure-Directory -Path $script:RuntimeDir
    return Join-Path $script:RuntimeDir "$ServiceName.$Stream.log"
}

function Load-ProcessState {
    if (-not (Test-Path $script:StatePath)) {
        return @()
    }

    $rawState = Get-Content $script:StatePath -Raw
    if (-not $rawState.Trim()) {
        return @()
    }

    $parsedState = ConvertFrom-Json -InputObject $rawState
    return ,(Normalize-ProcessState -State $parsedState)
}

function Normalize-ProcessState {
    param([AllowEmptyCollection()][object[]]$State)

    if (-not $State) {
        return @()
    }

    $flattened = @()
    foreach ($entry in $State) {
        if ($null -eq $entry) {
            continue
        }

        if ($entry.PSObject.Properties.Name -contains "value" -and $entry.value) {
            $flattened += Normalize-ProcessState -State (@($entry.value))
            continue
        }

        if (-not ($entry.PSObject.Properties.Name -contains "Name")) {
            continue
        }

        $flattened += $entry
    }

    $entriesByName = [ordered]@{}
    foreach ($entry in $flattened) {
        $entriesByName[$entry.Name] = $entry
    }

    return @($entriesByName.Values)
}

function Save-ProcessState {
    param([Parameter(Mandatory = $true)][object[]]$Entries)

    Ensure-Directory -Path $script:RuntimeDir
    $normalizedEntries = Normalize-ProcessState -State $Entries
    $normalizedEntries | ConvertTo-Json -Depth 6 | Set-Content -Path $script:StatePath -Encoding UTF8
}

function Get-StateEntry {
    param(
        [AllowEmptyCollection()][object[]]$State,
        [Parameter(Mandatory = $true)][string]$Name
    )

    return $State |
        Where-Object { $_ -and $_.PSObject.Properties.Name -contains "Name" -and $_.Name -eq $Name } |
        Select-Object -First 1
}

function Set-StateEntry {
    param(
        [AllowEmptyCollection()][object[]]$State,
        [Parameter(Mandatory = $true)][pscustomobject]$Entry
    )

    $nextState = @(
        $State |
            Where-Object { -not $_ -or -not ($_.PSObject.Properties.Name -contains "Name") -or $_.Name -ne $Entry.Name }
    )
    $nextState += $Entry
    return Normalize-ProcessState -State $nextState
}

function Get-PortOwner {
    param([Parameter(Mandatory = $true)][int]$Port)

    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1

    if ($listener) {
        return [int]$listener.OwningProcess
    }

    return $null
}

function Test-PortListening {
    param([Parameter(Mandatory = $true)][int]$Port)

    return [bool](Get-PortOwner -Port $Port)
}

function Wait-Until {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Condition,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            if (& $Condition) {
                return
            }
        } catch {
            Start-Sleep -Seconds 2
            continue
        }

        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for $Description after $TimeoutSeconds seconds."
}

function Test-HttpReady {
    param([Parameter(Mandatory = $true)][string]$Url)

    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 10
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500
    } catch {
        $webResponse = $_.Exception.Response
        if ($webResponse) {
            return [int]$webResponse.StatusCode -lt 500
        }

        return $false
    }
}

function Wait-ForService {
    param([Parameter(Mandatory = $true)][pscustomobject]$Service)

    switch ($Service.VerifyMode) {
        "http" {
            Wait-Until -TimeoutSeconds $Service.StartupSeconds -Description "$($Service.Name) at $($Service.VerifyTarget)" -Condition {
                Test-HttpReady -Url $Service.VerifyTarget
            }
        }
        "tcp" {
            Wait-Until -TimeoutSeconds $Service.StartupSeconds -Description "$($Service.Name) port $($Service.Port)" -Condition {
                Test-PortListening -Port $Service.Port
            }
        }
        default {
            throw "Unknown verification mode '$($Service.VerifyMode)' for $($Service.Name)."
        }
    }
}

function Show-ServiceLogs {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [int]$Tail = 40
    )

    $outLog = Get-LogPath -ServiceName $ServiceName -Stream "out"
    $errLog = Get-LogPath -ServiceName $ServiceName -Stream "err"

    if (Test-Path $outLog) {
        Write-Host ""
        Write-Host "Last lines from $ServiceName stdout:"
        Get-Content $outLog -Tail $Tail
    }

    if (Test-Path $errLog) {
        $errContent = Get-Content $errLog -Tail $Tail
        if ($errContent) {
            Write-Host ""
            Write-Host "Last lines from $ServiceName stderr:"
            $errContent
        }
    }
}

function Start-ManagedService {
    param(
        [Parameter(Mandatory = $true)][pscustomobject]$Service,
        [AllowEmptyCollection()][object[]]$State
    )

    $currentPid = @(Get-PortOwner -Port $Service.Port) | Select-Object -First 1
    if ($currentPid) {
        Write-Host "$($Service.Name) is already listening on port $($Service.Port) with PID $currentPid. Skipping startup."

        $existingEntry = @(Get-StateEntry -State $State -Name $Service.Name) | Select-Object -First 1
        $startedByScript = $false
        if ($existingEntry -and $existingEntry.StartedByScript -and [string]$existingEntry.Pid -eq [string]$currentPid) {
            $startedByScript = $true
        }

        $record = [pscustomobject]@{
            Name = $Service.Name
            Port = $Service.Port
            Pid = $currentPid
            StartedByScript = $startedByScript
            LastSeenAt = (Get-Date).ToString("o")
            OutLog = Get-LogPath -ServiceName $Service.Name -Stream "out"
            ErrLog = Get-LogPath -ServiceName $Service.Name -Stream "err"
        }

        return Set-StateEntry -State $State -Entry $record
    }

    $outLog = Get-LogPath -ServiceName $Service.Name -Stream "out"
    $errLog = Get-LogPath -ServiceName $Service.Name -Stream "err"
    Add-Content -Path $outLog -Value "`n=== $(Get-Date -Format s) starting $($Service.Name) ==="
    Add-Content -Path $errLog -Value "`n=== $(Get-Date -Format s) starting $($Service.Name) ==="

    Write-Host "Starting $($Service.Name)..."
    $shellCommand = "$($Service.LaunchCommand) 1>> ""$outLog"" 2>> ""$errLog"""
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $shellCommand -WorkingDirectory $Service.Workdir -WindowStyle Hidden | Out-Null

    try {
        Wait-ForService -Service $Service
    } catch {
        Write-Host "$($Service.Name) failed to become ready."
        Show-ServiceLogs -ServiceName $Service.Name
        throw
    }

    $startedPid = @(Get-PortOwner -Port $Service.Port) | Select-Object -First 1
    if (-not $startedPid) {
        Show-ServiceLogs -ServiceName $Service.Name
        throw "Could not determine the listening PID for $($Service.Name) on port $($Service.Port)."
    }

    Write-Host "$($Service.Name) is ready on port $($Service.Port) with PID $startedPid."
    $entry = [pscustomobject]@{
        Name = $Service.Name
        Port = $Service.Port
        Pid = $startedPid
        StartedByScript = $true
        LastSeenAt = (Get-Date).ToString("o")
        OutLog = $outLog
        ErrLog = $errLog
    }

    return Set-StateEntry -State $State -Entry $entry
}

function Wait-ForEurekaRegistrations {
    param([int]$TimeoutSeconds = 180)

    Wait-Until -TimeoutSeconds $TimeoutSeconds -Description "Eureka registrations" -Condition {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8761/eureka/apps" -TimeoutSec 10
            $content = $response.Content
            foreach ($serviceName in $script:EurekaServiceNames) {
                if ($content -notmatch $serviceName) {
                    return $false
                }
            }

            return $true
        } catch {
            return $false
        }
    }
}

function Get-ManagedService {
    param([Parameter(Mandatory = $true)][string]$Name)

    $service = $script:ManagedServices | Where-Object { $_.Name -eq $Name } | Select-Object -First 1
    if (-not $service) {
        throw "Unknown managed service '$Name'."
    }

    return $service
}
