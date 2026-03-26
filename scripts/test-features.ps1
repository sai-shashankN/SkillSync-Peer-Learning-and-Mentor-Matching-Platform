$ErrorActionPreference = "Continue"

$BaseUrl = "http://localhost:8091"
$Password = "Demo@1234"
$script:TotalTests = 18
$script:PassedTests = 0

$script:AdminToken = $null
$script:MentorToken = $null
$script:LearnerToken = $null

function Get-ResponseContentString {
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

function Convert-TextToJson {
    param(
        [string]$Text
    )

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }

    try {
        return $Text | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Get-JsonData {
    param(
        [object]$Json
    )

    if ($null -eq $Json) {
        return $null
    }

    if ($null -ne $Json.PSObject.Properties["data"]) {
        return $Json.data
    }

    return $Json
}

function Invoke-HttpRequest {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        [object]$Body,
        [hashtable]$Headers
    )

    $requestHeaders = @{}
    if ($Headers) {
        foreach ($key in $Headers.Keys) {
            $requestHeaders[$key] = $Headers[$key]
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $requestHeaders["Authorization"] = "Bearer $Token"
    }

    $params = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        UseBasicParsing = $true
        ErrorAction = "Stop"
    }

    if ($requestHeaders.Count -gt 0) {
        $params.Headers = $requestHeaders
    }

    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        if ($Body -is [string]) {
            $params.Body = $Body
        } else {
            $params.Body = $Body | ConvertTo-Json -Compress
        }
    }

    try {
        $response = Invoke-WebRequest @params
        $contentText = Get-ResponseContentString -Content $response.Content

        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Content = $contentText
            Json = Convert-TextToJson -Text $contentText
            Success = $true
        }
    } catch {
        $statusCode = 0
        $contentText = $_.Exception.Message

        if ($_.Exception.Response) {
            try {
                $statusCode = [int]$_.Exception.Response.StatusCode
            } catch {
                $statusCode = 0
            }

            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($null -ne $stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    try {
                        $contentText = $reader.ReadToEnd()
                    } finally {
                        $reader.Dispose()
                    }
                }
            } catch {
                $contentText = $_.Exception.Message
            }
        }

        return [pscustomobject]@{
            StatusCode = $statusCode
            Content = $contentText
            Json = Convert-TextToJson -Text $contentText
            Success = $false
        }
    }
}

function Get-AccessToken {
    param(
        [object]$Response
    )

    if ($null -eq $Response -or $null -eq $Response.Json) {
        return $null
    }

    $data = Get-JsonData -Json $Response.Json
    if ($null -ne $data -and $null -ne $data.PSObject.Properties["accessToken"]) {
        return $data.accessToken
    }

    return $null
}

function Write-TestResult {
    param(
        [int]$Number,
        [string]$Name,
        [bool]$Passed,
        [object]$Response,
        [string]$Details
    )

    if ($Passed) {
        $script:PassedTests++
    }

    $statusLabel = if ($Passed) { "PASS" } else { "FAIL" }
    $codeLabel = "n/a"
    if ($null -ne $Response -and $null -ne $Response.StatusCode) {
        $codeLabel = [string]$Response.StatusCode
    }

    Write-Host ("[{0}] Test {1}: {2} (HTTP {3})" -f $statusLabel, $Number, $Name, $codeLabel)

    if (-not $Passed -and -not [string]::IsNullOrWhiteSpace($Details)) {
        Write-Host ("       {0}" -f $Details)
    }
}

function Invoke-Test {
    param(
        [int]$Number,
        [string]$Name,
        [scriptblock]$Action,
        [scriptblock]$Assert,
        [scriptblock]$OnPass
    )

    $response = $null
    $details = $null
    $passed = $false

    try {
        $response = & $Action
        $passed = [bool](& $Assert $response)
        if ($passed -and $OnPass) {
            & $OnPass $response
        }
        if (-not $passed) {
            $details = $response.Content
        }
    } catch {
        $details = $_.Exception.Message
    }

    Write-TestResult -Number $Number -Name $Name -Passed $passed -Response $response -Details $details
}

Write-Host "Running SkillSync feature tests against $BaseUrl"
Write-Host ""

Invoke-Test -Number 1 -Name "POST /auth/login with admin" -Action {
    Invoke-HttpRequest -Method "POST" -Path "/auth/login" -Body @{
        email = "admin@skillsync.com"
        password = $Password
    }
} -Assert {
    param($response)
    $token = Get-AccessToken -Response $response
    $response.StatusCode -eq 200 -and -not [string]::IsNullOrWhiteSpace($token)
} -OnPass {
    param($response)
    $script:AdminToken = Get-AccessToken -Response $response
}

Invoke-Test -Number 2 -Name "POST /auth/login with mentor" -Action {
    Invoke-HttpRequest -Method "POST" -Path "/auth/login" -Body @{
        email = "priya.mentor@skillsync.com"
        password = $Password
    }
} -Assert {
    param($response)
    $token = Get-AccessToken -Response $response
    $response.StatusCode -eq 200 -and -not [string]::IsNullOrWhiteSpace($token)
} -OnPass {
    param($response)
    $script:MentorToken = Get-AccessToken -Response $response
}

Invoke-Test -Number 3 -Name "POST /auth/login with learner" -Action {
    Invoke-HttpRequest -Method "POST" -Path "/auth/login" -Body @{
        email = "arjun.learner@skillsync.com"
        password = $Password
    }
} -Assert {
    param($response)
    $token = Get-AccessToken -Response $response
    $response.StatusCode -eq 200 -and -not [string]::IsNullOrWhiteSpace($token)
} -OnPass {
    param($response)
    $script:LearnerToken = Get-AccessToken -Response $response
}

Invoke-Test -Number 4 -Name "GET /auth/validate with admin token" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/auth/validate" -Token $script:AdminToken
} -Assert {
    param($response)
    $data = Get-JsonData -Json $response.Json
    $response.StatusCode -eq 200 -and $data.email -eq "admin@skillsync.com"
}

Invoke-Test -Number 5 -Name "GET /users/me with admin token" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/users/me" -Token $script:AdminToken
} -Assert {
    param($response)
    $data = Get-JsonData -Json $response.Json
    $response.StatusCode -eq 200 -and $data.email -eq "admin@skillsync.com"
}

Invoke-Test -Number 6 -Name "GET /users/me with mentor token" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/users/me" -Token $script:MentorToken
} -Assert {
    param($response)
    $data = Get-JsonData -Json $response.Json
    $response.StatusCode -eq 200 -and $data.email -eq "priya.mentor@skillsync.com"
}

Invoke-Test -Number 7 -Name "GET /users/me with learner token" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/users/me" -Token $script:LearnerToken
} -Assert {
    param($response)
    $data = Get-JsonData -Json $response.Json
    $response.StatusCode -eq 200 -and $data.email -eq "arjun.learner@skillsync.com"
}

Invoke-Test -Number 8 -Name "GET /skills" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/skills"
} -Assert {
    param($response)
    $data = Get-JsonData -Json $response.Json
    $count = 0
    if ($data -is [System.Array]) {
        $count = $data.Count
    } elseif ($data -and $data -is [System.Collections.IEnumerable]) {
        $count = @($data).Count
    }
    $response.StatusCode -eq 200 -and $count -ge 12
}

Invoke-Test -Number 9 -Name "GET /skills/1" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/skills/1"
} -Assert {
    param($response)
    $data = Get-JsonData -Json $response.Json
    $response.StatusCode -eq 200 -and $null -ne $data -and $null -ne $data.PSObject.Properties["id"]
}

Invoke-Test -Number 10 -Name "GET /mentors" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/mentors"
} -Assert {
    param($response)
    $response.StatusCode -eq 200
}

Invoke-Test -Number 11 -Name "GET /mentors/me with mentor token" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/mentors/me" -Token $script:MentorToken
} -Assert {
    param($response)
    $data = Get-JsonData -Json $response.Json
    $response.StatusCode -eq 200 -and $null -ne $data
}

Invoke-Test -Number 12 -Name "GET /mentors/me with learner token" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/mentors/me" -Token $script:LearnerToken
} -Assert {
    param($response)
    $response.StatusCode -in @(400, 404)
}

Invoke-Test -Number 13 -Name "GET /groups with learner token" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/groups" -Token $script:LearnerToken
} -Assert {
    param($response)
    $response.StatusCode -eq 200
}

Invoke-Test -Number 14 -Name "GET /groups with mentor token" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/groups" -Token $script:MentorToken
} -Assert {
    param($response)
    $response.StatusCode -eq 200
}

Invoke-Test -Number 15 -Name "GET /notifications/me with learner token" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/notifications/me" -Token $script:LearnerToken
} -Assert {
    param($response)
    $response.StatusCode -eq 200
}

Invoke-Test -Number 16 -Name "GET /notifications/me with admin token" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/notifications/me" -Token $script:AdminToken
} -Assert {
    param($response)
    $response.StatusCode -eq 200
}

Invoke-Test -Number 17 -Name "GET /users/admin with admin token" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/users/admin" -Token $script:AdminToken
} -Assert {
    param($response)
    $response.StatusCode -eq 200
}

Invoke-Test -Number 18 -Name "GET /users/admin with learner token (denied)" -Action {
    Invoke-HttpRequest -Method "GET" -Path "/users/admin" -Token $script:LearnerToken
} -Assert {
    param($response)
    $response.StatusCode -in @(401, 403)
}

Write-Host ""
Write-Host ("Summary: {0}/{1} tests passed." -f $script:PassedTests, $script:TotalTests)
