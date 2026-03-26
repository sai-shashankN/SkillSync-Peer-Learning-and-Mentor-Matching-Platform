$GW = "http://localhost:8091"
$TIMESTAMP = Get-Date -Format "yyyyMMddHHmmss"

$script:Results = New-Object System.Collections.Generic.List[object]
$script:LearnerToken = $null
$script:MentorToken = $null
$script:GroupId = 0
$script:MentorId = 0
$script:LearnerName = "Test Learner $TIMESTAMP"
$script:LearnerEmail = "learner-$TIMESTAMP@test.com"
$script:MentorName = "Test Mentor $TIMESTAMP"
$script:MentorEmail = "mentor-$TIMESTAMP@test.com"

function Convert-ContentToJson {
    param(
        [object]$Content
    )

    if ([string]::IsNullOrWhiteSpace($Content)) {
        return $null
    }

    try {
        return $Content | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Convert-ResponseContentToString {
    param(
        [object]$Content
    )

    if ($null -eq $Content) {
        return ""
    }

    if ($Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Content)
    }

    return [string]$Content
}

function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        [object]$Body
    )

    $params = @{
        Uri = $Url
        Method = $Method
        UseBasicParsing = $true
        ErrorAction = "Stop"
    }

    if ($Headers) {
        $params.Headers = $Headers
    }

    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        if ($Body -is [string]) {
            $params.Body = $Body
        } else {
            $params.Body = $Body | ConvertTo-Json -Depth 20 -Compress
        }
    }

    try {
        $response = Invoke-WebRequest @params
        $content = Convert-ResponseContentToString -Content $response.Content
        return [pscustomobject]@{
            Success = $true
            StatusCode = [int]$response.StatusCode
            Content = $content
            Json = Convert-ContentToJson -Content $content
        }
    } catch {
        $statusCode = 0
        $content = $_.Exception.Message

        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            try {
                $content = $reader.ReadToEnd()
            } finally {
                $reader.Dispose()
            }
        }

        return [pscustomobject]@{
            Success = $false
            StatusCode = $statusCode
            Content = $content
            Json = Convert-ContentToJson -Content $content
        }
    }
}

function Add-TestResult {
    param(
        [int]$Number,
        [string]$Name,
        [bool]$Passed,
        [object]$HttpCode
    )

    $script:Results.Add([pscustomobject]@{
        Number = $Number
        Name = $Name
        Status = if ($Passed) { "PASS" } else { "FAIL" }
        HttpCode = $HttpCode
    }) | Out-Null
}

function Complete-Test {
    param(
        [int]$Number,
        [string]$Name,
        [object]$Response,
        [scriptblock]$Predicate,
        [scriptblock]$OnPass,
        [scriptblock]$OnFail
    )

    $passed = $false
    if ($Response -and $Predicate) {
        $passed = [bool](& $Predicate $Response)
    }

    if ($passed -and $OnPass) {
        & $OnPass $Response
    }

    if (-not $passed) {
        Write-Host ""
        Write-Host ("Test {0} FAILED: {1}" -f $Number, $Name)
        if ($OnFail) {
            & $OnFail $Response
        }
        if ($Response -and $Response.Content) {
            Write-Host $Response.Content
        }
    }

    Add-TestResult -Number $Number -Name $Name -Passed $passed -HttpCode $(if ($Response.StatusCode) { $Response.StatusCode } else { "N/A" })
}

function Get-AuthHeaders {
    param(
        [string]$Token
    )

    if ([string]::IsNullOrWhiteSpace($Token)) {
        return @{}
    }

    return @{ Authorization = "Bearer $Token" }
}

Write-Host "SkillSync E2E started at $TIMESTAMP"

$healthTargets = @(
    @{ Name = "Auth Service"; Port = 8081 },
    @{ Name = "User Service"; Port = 8082 },
    @{ Name = "Mentor Service"; Port = 8083 },
    @{ Name = "Skill Service"; Port = 8084 },
    @{ Name = "Session Service"; Port = 8085 },
    @{ Name = "Payment Service"; Port = 8086 },
    @{ Name = "Review Service"; Port = 8087 },
    @{ Name = "Group Service"; Port = 8088 },
    @{ Name = "Notification Service"; Port = 8089 },
    @{ Name = "Audit Service"; Port = 8090 }
)

Write-Host ""
Write-Host "Health checks:"
$allHealthy = $true
foreach ($target in $healthTargets) {
    $response = Invoke-ApiRequest -Method "GET" -Url ("http://localhost:{0}/actuator/health" -f $target.Port)
    $healthy = $response.StatusCode -eq 200 -and $response.Content -match '"status"\s*:\s*"UP"'
    if (-not $healthy) {
        $allHealthy = $false
    }
    Write-Host ("  {0,-22} {1,-4} {2}" -f $target.Name, $response.StatusCode, $(if ($healthy) { "UP" } else { "DOWN" }))
}

$gatewayHealth = Invoke-ApiRequest -Method "GET" -Url "$GW/actuator/health"
$gatewayHealthy = $gatewayHealth.StatusCode -eq 200 -and $gatewayHealth.Content -match '"status"\s*:\s*"UP"'
if (-not $gatewayHealthy) {
    $allHealthy = $false
}
Write-Host ("  {0,-22} {1,-4} {2}" -f "API Gateway", $gatewayHealth.StatusCode, $(if ($gatewayHealthy) { "UP" } else { "DOWN" }))
Add-TestResult -Number 1 -Name "Health Checks" -Passed $allHealthy -HttpCode $(if ($gatewayHealth.StatusCode) { $gatewayHealth.StatusCode } else { "N/A" })
if (-not $allHealthy -and $gatewayHealth.Content) {
    Write-Host $gatewayHealth.Content
}

$registerLearner = Invoke-ApiRequest -Method "POST" -Url "$GW/auth/register" -Body @{
    name = $script:LearnerName
    email = $script:LearnerEmail
    password = "Test@1234"
}
Complete-Test -Number 2 -Name "Register Learner" -Response $registerLearner -Predicate {
    param($response)
    $response.StatusCode -eq 201 -and -not [string]::IsNullOrWhiteSpace($response.Json.data.accessToken)
} -OnPass {
    param($response)
    $script:LearnerToken = $response.Json.data.accessToken
}

$loginLearner = Invoke-ApiRequest -Method "POST" -Url "$GW/auth/login" -Body @{
    email = $script:LearnerEmail
    password = "Test@1234"
}
Complete-Test -Number 3 -Name "Login Learner" -Response $loginLearner -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and -not [string]::IsNullOrWhiteSpace($response.Json.data.accessToken)
} -OnPass {
    param($response)
    $script:LearnerToken = $response.Json.data.accessToken
}

$learnerProfile = Invoke-ApiRequest -Method "GET" -Url "$GW/users/me" -Headers (Get-AuthHeaders -Token $script:LearnerToken)
Complete-Test -Number 4 -Name "Get Learner Profile" -Response $learnerProfile -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and
    $response.Json.data.email -eq $script:LearnerEmail -and
    $response.Json.data.name -eq $script:LearnerName
}

$skillsResponse = Invoke-ApiRequest -Method "GET" -Url "$GW/skills"
Complete-Test -Number 5 -Name "Browse Skills" -Response $skillsResponse -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $null -ne $response.Json.data
} -OnPass {
    param($response)
    $count = @($response.Json.data).Count
    Write-Host ("Skills returned: {0}" -f $count)
}

$mentorsResponse = Invoke-ApiRequest -Method "GET" -Url "$GW/mentors?page=0&size=10"
Complete-Test -Number 6 -Name "Browse Mentors" -Response $mentorsResponse -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $null -ne $response.Json.content
} -OnPass {
    param($response)
    $count = @($response.Json.content).Count
    Write-Host ("Mentors returned: {0}" -f $count)
}

$registerMentor = Invoke-ApiRequest -Method "POST" -Url "$GW/auth/register" -Body @{
    name = $script:MentorName
    email = $script:MentorEmail
    password = "Test@1234"
}
Complete-Test -Number 7 -Name "Register Mentor" -Response $registerMentor -Predicate {
    param($response)
    $response.StatusCode -eq 201 -and -not [string]::IsNullOrWhiteSpace($response.Json.data.accessToken)
} -OnPass {
    param($response)
    $script:MentorToken = $response.Json.data.accessToken
}

$mentorApply = Invoke-ApiRequest -Method "POST" -Url "$GW/mentors/apply" -Headers (Get-AuthHeaders -Token $script:MentorToken) -Body @{
    headline = "Expert in Java"
    bio = "10 years experience"
    experience = "10 years"
    hourlyRate = 500
    skillIds = @()
}
Complete-Test -Number 8 -Name "Apply as Mentor" -Response $mentorApply -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $null -ne $response.Json.data.id
} -OnPass {
    param($response)
    $script:MentorId = [int64]$response.Json.data.id
}

$mentorProfile = Invoke-ApiRequest -Method "GET" -Url "$GW/mentors/me" -Headers (Get-AuthHeaders -Token $script:MentorToken)
Complete-Test -Number 9 -Name "Get Mentor Profile" -Response $mentorProfile -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $null -ne $response.Json.data.id
}

$notificationsResponse = Invoke-ApiRequest -Method "GET" -Url "$GW/notifications/me?page=0&size=10" -Headers (Get-AuthHeaders -Token $script:LearnerToken)
Complete-Test -Number 10 -Name "Get Notifications" -Response $notificationsResponse -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $null -ne $response.Json.data.content
} -OnPass {
    param($response)
    $count = @($response.Json.data.content).Count
    Write-Host ("Notifications returned: {0}" -f $count)
}

$unreadCountResponse = Invoke-ApiRequest -Method "GET" -Url "$GW/notifications/me/unread-count" -Headers (Get-AuthHeaders -Token $script:LearnerToken)
Complete-Test -Number 11 -Name "Get Unread Notification Count" -Response $unreadCountResponse -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $null -ne $response.Json.data.count
} -OnPass {
    param($response)
    Write-Host ("Unread count: {0}" -f $response.Json.data.count)
}

$createGroup = Invoke-ApiRequest -Method "POST" -Url "$GW/groups" -Headers (Get-AuthHeaders -Token $script:LearnerToken) -Body @{
    name = "Java Study Group $TIMESTAMP"
    description = "Learning Java together"
    maxMembers = 10
}
Complete-Test -Number 12 -Name "Create Study Group" -Response $createGroup -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $null -ne $response.Json.data.id
} -OnPass {
    param($response)
    $script:GroupId = [int64]$response.Json.data.id
}

$groupDetails = Invoke-ApiRequest -Method "GET" -Url ("$GW/groups/{0}" -f $script:GroupId) -Headers (Get-AuthHeaders -Token $script:LearnerToken)
Complete-Test -Number 13 -Name "Get Group Details" -Response $groupDetails -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $response.Json.data.id -eq $script:GroupId
}

$sendMessage = Invoke-ApiRequest -Method "POST" -Url ("$GW/groups/{0}/messages" -f $script:GroupId) -Headers (Get-AuthHeaders -Token $script:LearnerToken) -Body @{
    content = "Hello everyone!"
}
Complete-Test -Number 14 -Name "Send Group Message" -Response $sendMessage -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $response.Json.data.content -eq "Hello everyone!"
}

$groupMessages = Invoke-ApiRequest -Method "GET" -Url ("$GW/groups/{0}/messages?page=0&size=10" -f $script:GroupId) -Headers (Get-AuthHeaders -Token $script:LearnerToken)
Complete-Test -Number 15 -Name "Get Group Messages" -Response $groupMessages -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $null -ne $response.Json.data.content
} -OnPass {
    param($response)
    $count = @($response.Json.data.content).Count
    Write-Host ("Group messages returned: {0}" -f $count)
}

$myGroups = Invoke-ApiRequest -Method "GET" -Url "$GW/groups/me" -Headers (Get-AuthHeaders -Token $script:LearnerToken)
Complete-Test -Number 16 -Name "Get My Groups" -Response $myGroups -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $null -ne $response.Json.data
} -OnPass {
    param($response)
    $count = @($response.Json.data).Count
    Write-Host ("My groups returned: {0}" -f $count)
}

$updateProfile = Invoke-ApiRequest -Method "PUT" -Url "$GW/users/me" -Headers (Get-AuthHeaders -Token $script:LearnerToken) -Body @{
    name = "Updated Learner $TIMESTAMP"
    bio = "I love learning"
    phone = "+911234567890"
}
Complete-Test -Number 17 -Name "Update Learner Profile" -Response $updateProfile -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $response.Json.data.phone -eq "+911234567890"
}

$preferences = Invoke-ApiRequest -Method "GET" -Url "$GW/users/me/preferences" -Headers (Get-AuthHeaders -Token $script:LearnerToken)
Complete-Test -Number 18 -Name "Get Learner Preferences" -Response $preferences -Predicate {
    param($response)
    $response.StatusCode -eq 200 -and $null -ne $response.Json.data
}

Write-Host ""
Write-Host "Test #   | Name                      | Status | HTTP Code"
Write-Host "---------|---------------------------|--------|----------"
foreach ($result in $script:Results) {
    Write-Host ("{0,-8} | {1,-25} | {2,-6} | {3}" -f $result.Number, $result.Name, $result.Status, $result.HttpCode)
}

$passCount = @($script:Results | Where-Object { $_.Status -eq "PASS" }).Count
$failCount = @($script:Results | Where-Object { $_.Status -eq "FAIL" }).Count

Write-Host ""
Write-Host ("Total PASS: {0}" -f $passCount)
Write-Host ("Total FAIL: {0}" -f $failCount)
