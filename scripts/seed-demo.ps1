$ErrorActionPreference = "Stop"

$GW = "http://localhost:8091"
$POSTGRES_CONTAINER = "infra-postgres-1"
$REDIS_CONTAINER = "infra-redis-1"
$PASSWORD = "Demo@1234"
$BOOKING_DATE = Get-Date -Format "yyyyMMdd"

function Write-Step {
    param([string]$Message)

    Write-Host ""
    Write-Host $Message -ForegroundColor Green
}

function Write-Info {
    param([string]$Message)

    Write-Host "  $Message" -ForegroundColor Gray
}

function Write-WarnLine {
    param([string]$Message)

    Write-Host "  [WARN] $Message" -ForegroundColor Yellow
}

function Convert-ResponseContentToString {
    param([object]$Content)

    if ($null -eq $Content) {
        return ""
    }

    if ($Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Content)
    }

    return [string]$Content
}

function Api {
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
            $params.Body = $Body | ConvertTo-Json -Depth 10 -Compress
        }
    }

    try {
        $response = Invoke-WebRequest @params
        $content = Convert-ResponseContentToString -Content $response.Content
        if ([string]::IsNullOrWhiteSpace($content)) {
            return $null
        }

        return $content | ConvertFrom-Json
    } catch {
        $code = 0
        $message = $_.Exception.Message

        if ($_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            try {
                $message = $reader.ReadToEnd()
            } finally {
                $reader.Dispose()
            }
        }

        Write-WarnLine "$Method $Url -> $code $message"
        return $null
    }
}

function Auth {
    param([string]$Token)

    return @{ Authorization = "Bearer $Token" }
}

function Invoke-SqlNonQuery {
    param(
        [string]$Database,
        [string]$Sql
    )

    $Sql | docker exec -i $POSTGRES_CONTAINER psql -U skillsync -d $Database -t 2>&1 | Out-Null
}

function Get-SqlValue {
    param(
        [string]$Database,
        [string]$Sql
    )

    $result = $Sql | docker exec -i $POSTGRES_CONTAINER psql -U skillsync -d $Database -t | Out-String
    return $result.Trim()
}

function Get-TableColumns {
    param(
        [string]$Database,
        [string]$Table
    )

    $sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '$Table' ORDER BY ordinal_position;"
    $result = $sql | docker exec -i $POSTGRES_CONTAINER psql -U skillsync -d $Database -t | Out-String

    if ([string]::IsNullOrWhiteSpace($result)) {
        return @()
    }

    return @(
        $result -split "`r?`n" |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
}

function Test-TableExists {
    param(
        [string]$Database,
        [string]$Table
    )

    $exists = Get-SqlValue -Database $Database -Sql "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '$Table');"
    return $exists -eq "t"
}

function Test-ColumnsPresent {
    param(
        [string]$Database,
        [string]$Table,
        [string[]]$Columns
    )

    $tableColumns = Get-TableColumns -Database $Database -Table $Table
    foreach ($column in $Columns) {
        if ($tableColumns -notcontains $column) {
            return $false
        }
    }

    return $true
}

function Require-Value {
    param(
        [string]$Name,
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "Missing required value: $Name"
    }

    return $Value.Trim()
}

function Login-User {
    param([pscustomobject]$User)

    $loginResult = Api -Method POST -Url "$GW/auth/login" -Body @{
        email = $User.Email
        password = $User.Password
    }

    if ($loginResult -and $loginResult.data -and $loginResult.data.accessToken) {
        return $loginResult.data.accessToken
    }

    return $null
}

function Register-Or-LoginUser {
    param([pscustomobject]$User)

    $registerResult = Api -Method POST -Url "$GW/auth/register" -Body @{
        name = $User.Name
        email = $User.Email
        password = $User.Password
    }

    if ($registerResult -and $registerResult.data -and $registerResult.data.accessToken) {
        Write-Info "Registered $($User.Email)"
        return $registerResult.data.accessToken
    }

    $token = Login-User -User $User
    if ($token) {
        Write-Info "Logged in existing user $($User.Email)"
        return $token
    }

    Write-WarnLine "Failed to register or login $($User.Email)"
    return $null
}

function Refresh-AllTokens {
    param(
        [object[]]$Users,
        [hashtable]$Tokens
    )

    foreach ($user in $Users) {
        $token = Login-User -User $user
        if ($token) {
            $Tokens[$user.Email] = $token
            Write-Info "Refreshed JWT for $($user.Email)"
        } else {
            Write-WarnLine "Could not refresh JWT for $($user.Email)"
        }
    }
}

function Get-SkillIdBySlug {
    param([string]$Slug)

    return Get-SqlValue -Database "skillsync_skills" -Sql "SELECT id FROM skills WHERE slug = '$Slug';"
}

function Ensure-MentorProfile {
    param(
        [string]$Email,
        [hashtable]$Tokens,
        [string]$Headline,
        [string]$Bio,
        [int]$ExperienceYears,
        [decimal]$HourlyRate,
        [int[]]$SkillIds
    )

    $token = $Tokens[$Email]
    if (-not $token) {
        Write-WarnLine "Skipping mentor profile for $Email because no token is available"
        return $null
    }

    $existingProfile = Api -Method GET -Url "$GW/mentors/me" -Headers (Auth $token)
    if ($existingProfile -and $existingProfile.data -and $existingProfile.data.id) {
        Write-Info "Mentor profile already exists for $Email"
        return $existingProfile.data
    }

    $result = Api -Method POST -Url "$GW/mentors/apply" -Headers (Auth $token) -Body @{
        headline = $Headline
        bio = $Bio
        experienceYears = $ExperienceYears
        hourlyRate = $HourlyRate
        skillIds = @($SkillIds)
    }

    if ($result -and $result.data -and $result.data.id) {
        Write-Info "Created mentor profile for $Email"
        return $result.data
    }

    Write-WarnLine "Mentor profile creation failed for $Email"
    return $null
}

function Find-GroupByName {
    param(
        [string]$Token,
        [string]$Name
    )

    $encodedName = [System.Uri]::EscapeDataString($Name)
    $response = Api -Method GET -Url "$GW/groups?search=$encodedName&page=0&size=50" -Headers (Auth $Token)
    if (-not $response -or -not $response.data -or -not $response.data.content) {
        return $null
    }

    foreach ($group in @($response.data.content)) {
        if ($group.name -eq $Name) {
            return $group
        }
    }

    return $null
}

function Ensure-Group {
    param(
        [string]$Name,
        [string]$Description,
        [int]$MaxMembers,
        [int[]]$SkillIds,
        [string]$CreatorEmail,
        [hashtable]$Tokens
    )

    $creatorToken = $Tokens[$CreatorEmail]
    if (-not $creatorToken) {
        Write-WarnLine "Skipping group '$Name' because $CreatorEmail has no token"
        return $null
    }

    $existingGroup = Find-GroupByName -Token $creatorToken -Name $Name
    if ($existingGroup) {
        Write-Info "Group already exists: $Name"
        return $existingGroup
    }

    $createdGroup = Api -Method POST -Url "$GW/groups" -Headers (Auth $creatorToken) -Body @{
        name = $Name
        description = $Description
        maxMembers = $MaxMembers
        skillIds = @($SkillIds)
    }

    if ($createdGroup -and $createdGroup.data -and $createdGroup.data.id) {
        Write-Info "Created group '$Name'"
        return $createdGroup.data
    }

    Write-WarnLine "Group creation failed for '$Name'"
    return $null
}

function Ensure-GroupJoin {
    param(
        [long]$GroupId,
        [string]$Email,
        [hashtable]$Tokens
    )

    $token = $Tokens[$Email]
    if (-not $token) {
        Write-WarnLine "Skipping join for $Email because no token is available"
        return
    }

    $response = Api -Method POST -Url "$GW/groups/$GroupId/join" -Headers (Auth $token)
    if ($response) {
        Write-Info "$Email joined group $GroupId"
    } else {
        Write-Info "$Email may already be a member of group $GroupId"
    }
}

function Ensure-GroupMessage {
    param(
        [long]$GroupId,
        [string]$Email,
        [string]$Content,
        [hashtable]$Tokens
    )

    $token = $Tokens[$Email]
    if (-not $token) {
        Write-WarnLine "Skipping group message for $Email because no token is available"
        return
    }

    $messages = Api -Method GET -Url "$GW/groups/$GroupId/messages?page=0&size=50" -Headers (Auth $token)
    if ($messages -and $messages.data -and $messages.data.content) {
        foreach ($message in @($messages.data.content)) {
            if ($message.content -eq $Content) {
                Write-Info "Message already present in group $GroupId for $Email"
                return
            }
        }
    }

    $response = Api -Method POST -Url "$GW/groups/$GroupId/messages" -Headers (Auth $token) -Body @{
        content = $Content
    }

    if ($response -and $response.data -and $response.data.id) {
        Write-Info "Added message in group $GroupId for $Email"
    } else {
        Write-WarnLine "Failed to send message in group $GroupId for $Email"
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  SkillSync Demo Data Seeder" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$users = @(
    [pscustomobject]@{ Name = "Admin User"; Email = "admin@skillsync.com"; Password = $PASSWORD; Role = "ADMIN" },
    [pscustomobject]@{ Name = "Ananya Iyer"; Email = "mentor@skillsync.com"; Password = $PASSWORD; Role = "MENTOR" },
    [pscustomobject]@{ Name = "Rajeev Kumar"; Email = "rajeev@skillsync.com"; Password = $PASSWORD; Role = "MENTOR" },
    [pscustomobject]@{ Name = "Vikram Singh"; Email = "learner@skillsync.com"; Password = $PASSWORD; Role = "LEARNER" },
    [pscustomobject]@{ Name = "Shashank Reddy"; Email = "shashank@skillsync.com"; Password = $PASSWORD; Role = "LEARNER" }
)

$tokens = @{}

Write-Step "[1/10] Seeding categories and skills"

$categorySql = @"
INSERT INTO skill_categories (name, slug, display_order) VALUES
  ('Programming & Development', 'programming-development', 1),
  ('Data & Analytics', 'data-analytics', 2),
  ('Design', 'design', 3),
  ('Project Management', 'project-management', 4),
  ('Soft Skills', 'soft-skills', 5)
ON CONFLICT (name) DO NOTHING;
"@
Invoke-SqlNonQuery -Database "skillsync_skills" -Sql $categorySql
Write-Info "Seeded categories."

$skillsSql = @"
INSERT INTO skills (name, category_id, description, slug, is_active) VALUES
  ('Java',               1, 'Enterprise-grade OOP language for backend systems',      'java', true),
  ('Python',             1, 'Versatile language for web, AI, and scripting',          'python', true),
  ('React',              1, 'Component-based UI library for modern web apps',         'react', true),
  ('Spring Boot',        1, 'Opinionated Java framework for microservices',           'spring-boot', true),
  ('TypeScript',         1, 'Typed superset of JavaScript for scalable apps',         'typescript', true),
  ('Node.js',            1, 'Server-side JavaScript runtime',                         'nodejs', true),
  ('Machine Learning',   2, 'Building predictive models from data',                   'machine-learning', true),
  ('SQL & Databases',    2, 'Relational data modeling and query optimization',        'sql-databases', true),
  ('Data Visualization', 2, 'Turning data into actionable visual insights',           'data-visualization', true),
  ('Power BI',           2, 'Business intelligence and data visualization tool',      'power-bi', true),
  ('UI/UX Design',       3, 'User-centered design for digital products',              'ui-ux-design', true),
  ('Figma',              3, 'Collaborative interface design tool',                    'figma', true),
  ('Agile & Scrum',      4, 'Iterative project management methodology',               'agile-scrum', true),
  ('Communication',      5, 'Professional communication and presentation skills',     'communication', true),
  ('Leadership',         5, 'Team leadership and management skills',                  'leadership', true)
ON CONFLICT (name) DO NOTHING;
"@
Invoke-SqlNonQuery -Database "skillsync_skills" -Sql $skillsSql
Write-Info "Seeded skills."

docker exec $REDIS_CONTAINER redis-cli FLUSHALL 2>&1 | Out-Null
Write-Info "Flushed Redis cache."

Write-Step "[2/10] Registering or logging in demo users"

foreach ($user in $users) {
    $token = Register-Or-LoginUser -User $user
    if ($token) {
        $tokens[$user.Email] = $token
    }
}

Write-Step "[3/10] Promoting admin and creating mentor profiles"

$promoteAdminSql = @"
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@skillsync.com' AND r.name IN ('ROLE_ADMIN', 'ROLE_MENTOR')
ON CONFLICT DO NOTHING;
"@
Invoke-SqlNonQuery -Database "skillsync_auth" -Sql $promoteAdminSql
Write-Info "Promoted admin user."

$javaSkillId = Require-Value -Name "Java skill ID" -Value (Get-SkillIdBySlug -Slug "java")
$reactSkillId = Require-Value -Name "React skill ID" -Value (Get-SkillIdBySlug -Slug "react")
$springBootSkillId = Require-Value -Name "Spring Boot skill ID" -Value (Get-SkillIdBySlug -Slug "spring-boot")
$typeScriptSkillId = Require-Value -Name "TypeScript skill ID" -Value (Get-SkillIdBySlug -Slug "typescript")
$nodeJsSkillId = Require-Value -Name "Node.js skill ID" -Value (Get-SkillIdBySlug -Slug "nodejs")
$sqlDatabasesSkillId = Require-Value -Name "SQL & Databases skill ID" -Value (Get-SkillIdBySlug -Slug "sql-databases")

$javaSkillIdInt = [int]$javaSkillId
$reactSkillIdInt = [int]$reactSkillId
$springBootSkillIdInt = [int]$springBootSkillId
$typeScriptSkillIdInt = [int]$typeScriptSkillId
$nodeJsSkillIdInt = [int]$nodeJsSkillId
$sqlDatabasesSkillIdInt = [int]$sqlDatabasesSkillId

Ensure-MentorProfile -Email "mentor@skillsync.com" -Tokens $tokens `
    -Headline "Senior Java & Spring Boot Engineer" `
    -Bio "10+ years building enterprise microservices at Capgemini. Expert in Spring Boot, Hibernate, and cloud-native architecture. Passionate about mentoring junior developers." `
    -ExperienceYears 10 `
    -HourlyRate 800 `
    -SkillIds @($javaSkillIdInt, $springBootSkillIdInt, $sqlDatabasesSkillIdInt) | Out-Null

Ensure-MentorProfile -Email "rajeev@skillsync.com" -Tokens $tokens `
    -Headline "Cloud Architect & Full-Stack Lead" `
    -Bio "15 years of experience across Java, React, AWS, and system design. Led 50+ microservice deployments. Available for architecture reviews and pair programming sessions." `
    -ExperienceYears 15 `
    -HourlyRate 1500 `
    -SkillIds @($javaSkillIdInt, $reactSkillIdInt, $springBootSkillIdInt, $typeScriptSkillIdInt, $nodeJsSkillIdInt) | Out-Null

Write-Step "[4/10] Approving mentors and refreshing JWT tokens"

$approveMentorsSql = @"
UPDATE mentors SET status = 'APPROVED', approved_at = NOW(), updated_at = NOW()
WHERE status = 'PENDING';
"@
Invoke-SqlNonQuery -Database "skillsync_mentors" -Sql $approveMentorsSql
Write-Info "Approved pending mentor profiles."

$mentorRolesSql = @"
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email IN ('mentor@skillsync.com', 'rajeev@skillsync.com')
  AND r.name = 'ROLE_MENTOR'
ON CONFLICT DO NOTHING;
"@
Invoke-SqlNonQuery -Database "skillsync_auth" -Sql $mentorRolesSql
Write-Info "Granted ROLE_MENTOR to mentor users."

Refresh-AllTokens -Users $users -Tokens $tokens

Write-Step "[5/10] Resolving user and mentor IDs"

$adminId = Require-Value -Name "admin user ID" -Value (Get-SqlValue -Database "skillsync_auth" -Sql "SELECT id FROM users WHERE email='admin@skillsync.com';")
$mentorUserId = Require-Value -Name "mentor user ID" -Value (Get-SqlValue -Database "skillsync_auth" -Sql "SELECT id FROM users WHERE email='mentor@skillsync.com';")
$rajeevUserId = Require-Value -Name "rajeev user ID" -Value (Get-SqlValue -Database "skillsync_auth" -Sql "SELECT id FROM users WHERE email='rajeev@skillsync.com';")
$learnerUserId = Require-Value -Name "learner user ID" -Value (Get-SqlValue -Database "skillsync_auth" -Sql "SELECT id FROM users WHERE email='learner@skillsync.com';")
$shashankUserId = Require-Value -Name "shashank user ID" -Value (Get-SqlValue -Database "skillsync_auth" -Sql "SELECT id FROM users WHERE email='shashank@skillsync.com';")

$mentorMentorId = Require-Value -Name "Ananya mentor ID" -Value (Get-SqlValue -Database "skillsync_mentors" -Sql "SELECT id FROM mentors WHERE user_id=$mentorUserId;")
$rajeevMentorId = Require-Value -Name "Rajeev mentor ID" -Value (Get-SqlValue -Database "skillsync_mentors" -Sql "SELECT id FROM mentors WHERE user_id=$rajeevUserId;")

Write-Info "Resolved auth and mentor service IDs."

Write-Step "[6/10] Seeding sessions and reviews"

$bookingRef1 = "BK-$BOOKING_DATE-001"
$bookingRef2 = "BK-$BOOKING_DATE-002"
$bookingRef3 = "BK-$BOOKING_DATE-003"
$bookingRef4 = "BK-$BOOKING_DATE-004"
$bookingRef5 = "BK-$BOOKING_DATE-005"
$bookingRef6 = "BK-$BOOKING_DATE-006"

$sessionSql = @"
INSERT INTO sessions (mentor_id, learner_id, skill_id, booking_reference, start_at, end_at, duration_minutes, topic, status, payment_deadline_at, amount, learner_timezone, mentor_timezone, version, created_at, updated_at, accepted_at, completed_at)
VALUES
  ($mentorMentorId, $learnerUserId, $javaSkillIdInt, '$bookingRef1', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days' + INTERVAL '60 minutes', 60, 'Java Design Patterns Deep Dive', 'COMPLETED', NOW() - INTERVAL '3 days', 800.00, 'Asia/Kolkata', 'Asia/Kolkata', 0, NOW() - INTERVAL '5 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '4 days', NOW() - INTERVAL '2 days'),
  ($rajeevMentorId, $shashankUserId, $springBootSkillIdInt, '$bookingRef2', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days' + INTERVAL '90 minutes', 90, 'Microservice Architecture Review', 'COMPLETED', NOW() - INTERVAL '6 days', 2250.00, 'Asia/Kolkata', 'Asia/Kolkata', 0, NOW() - INTERVAL '8 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '7 days', NOW() - INTERVAL '5 days'),
  ($rajeevMentorId, $learnerUserId, $reactSkillIdInt, '$bookingRef3', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days' + INTERVAL '60 minutes', 60, 'React Performance Optimization', 'COMPLETED', NOW() - INTERVAL '4 days', 1500.00, 'Asia/Kolkata', 'Asia/Kolkata', 0, NOW() - INTERVAL '6 days', NOW() - INTERVAL '3 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '3 days'),
  ($mentorMentorId, $shashankUserId, $springBootSkillIdInt, '$bookingRef4', NOW() + INTERVAL '2 days', NOW() + INTERVAL '2 days' + INTERVAL '60 minutes', 60, 'Spring Boot Security Configuration', 'ACCEPTED', NOW() + INTERVAL '1 day', 800.00, 'Asia/Kolkata', 'Asia/Kolkata', 0, NOW() - INTERVAL '1 day', NOW(), NOW() - INTERVAL '12 hours', NULL),
  ($rajeevMentorId, $learnerUserId, $javaSkillIdInt, '$bookingRef5', NOW() + INTERVAL '3 days', NOW() + INTERVAL '3 days' + INTERVAL '90 minutes', 90, 'System Design Interview Prep', 'ACCEPTED', NOW() + INTERVAL '2 days', 2250.00, 'Asia/Kolkata', 'Asia/Kolkata', 0, NOW() - INTERVAL '1 day', NOW(), NOW() - INTERVAL '6 hours', NULL),
  ($rajeevMentorId, $shashankUserId, $nodeJsSkillIdInt, '$bookingRef6', NOW() + INTERVAL '5 days', NOW() + INTERVAL '5 days' + INTERVAL '60 minutes', 60, 'AWS Cloud Architecture Planning', 'PAID', NOW() + INTERVAL '4 days', 1500.00, 'Asia/Kolkata', 'Asia/Kolkata', 0, NOW() - INTERVAL '6 hours', NOW(), NULL, NULL)
ON CONFLICT (booking_reference) DO NOTHING;
"@
Invoke-SqlNonQuery -Database "skillsync_sessions" -Sql $sessionSql
Write-Info "Seeded demo sessions."

$session1Id = Require-Value -Name "session 1 ID" -Value (Get-SqlValue -Database "skillsync_sessions" -Sql "SELECT id FROM sessions WHERE booking_reference = '$bookingRef1';")
$session2Id = Require-Value -Name "session 2 ID" -Value (Get-SqlValue -Database "skillsync_sessions" -Sql "SELECT id FROM sessions WHERE booking_reference = '$bookingRef2';")
$session3Id = Require-Value -Name "session 3 ID" -Value (Get-SqlValue -Database "skillsync_sessions" -Sql "SELECT id FROM sessions WHERE booking_reference = '$bookingRef3';")
$session4Id = Require-Value -Name "session 4 ID" -Value (Get-SqlValue -Database "skillsync_sessions" -Sql "SELECT id FROM sessions WHERE booking_reference = '$bookingRef4';")
$session5Id = Require-Value -Name "session 5 ID" -Value (Get-SqlValue -Database "skillsync_sessions" -Sql "SELECT id FROM sessions WHERE booking_reference = '$bookingRef5';")
$session6Id = Require-Value -Name "session 6 ID" -Value (Get-SqlValue -Database "skillsync_sessions" -Sql "SELECT id FROM sessions WHERE booking_reference = '$bookingRef6';")

$reviewSql = @"
INSERT INTO reviews (mentor_id, learner_id, session_id, rating, comment, is_moderated, is_visible, created_at) VALUES
  ($mentorMentorId, $learnerUserId, $session1Id, 5, 'Ananya explained design patterns so clearly. The real-world examples were incredibly helpful. Highly recommended!', false, true, NOW() - INTERVAL '2 days'),
  ($rajeevMentorId, $shashankUserId, $session2Id, 4, 'Rajeev has deep architecture knowledge. Session was very informative, though we ran slightly over time.', false, true, NOW() - INTERVAL '5 days'),
  ($rajeevMentorId, $learnerUserId, $session3Id, 5, 'Excellent session on React optimization. Rajeev showed us actual profiling tools and techniques.', false, true, NOW() - INTERVAL '3 days')
ON CONFLICT DO NOTHING;
"@
Invoke-SqlNonQuery -Database "skillsync_reviews" -Sql $reviewSql
Write-Info "Seeded reviews for completed sessions."

$mentorStatsSql = @"
UPDATE mentors SET avg_rating = 5.00, total_sessions = 1, total_reviews = 1, updated_at = NOW()
WHERE user_id = $mentorUserId;

UPDATE mentors SET avg_rating = 4.50, total_sessions = 4, total_reviews = 2, updated_at = NOW()
WHERE user_id = $rajeevUserId;
"@
Invoke-SqlNonQuery -Database "skillsync_mentors" -Sql $mentorStatsSql
Write-Info "Updated mentor aggregate stats."

Write-Step "[7/10] Creating study groups"

$group1 = Ensure-Group `
    -Name "Java Spring Boot Study Circle" `
    -Description "Weekly deep-dives into Spring Boot, microservices patterns, and cloud deployment." `
    -MaxMembers 20 `
    -SkillIds @($javaSkillIdInt, $springBootSkillIdInt) `
    -CreatorEmail "learner@skillsync.com" `
    -Tokens $tokens

if ($group1 -and $group1.id) {
    Ensure-GroupJoin -GroupId ([long]$group1.id) -Email "learner@skillsync.com" -Tokens $tokens
    Ensure-GroupMessage -GroupId ([long]$group1.id) -Email "learner@skillsync.com" -Content "Welcome everyone! Let's use this space for Spring Boot deep-dives, architecture questions, and deployment discussions." -Tokens $tokens
    Ensure-GroupJoin -GroupId ([long]$group1.id) -Email "shashank@skillsync.com" -Tokens $tokens
    Ensure-GroupMessage -GroupId ([long]$group1.id) -Email "shashank@skillsync.com" -Content "Happy to join. I have been working through Spring Security lately and would love to compare implementation patterns." -Tokens $tokens
}

$group2 = Ensure-Group `
    -Name "React & TypeScript Guild" `
    -Description "Frontend engineers sharing knowledge on React 19, TypeScript patterns, and modern tooling." `
    -MaxMembers 15 `
    -SkillIds @($reactSkillIdInt, $typeScriptSkillIdInt) `
    -CreatorEmail "shashank@skillsync.com" `
    -Tokens $tokens

if ($group2 -and $group2.id) {
    Ensure-GroupJoin -GroupId ([long]$group2.id) -Email "shashank@skillsync.com" -Tokens $tokens
    Ensure-GroupMessage -GroupId ([long]$group2.id) -Email "shashank@skillsync.com" -Content "Welcome to the guild. Let's share patterns, performance tips, and code review lessons from real React projects." -Tokens $tokens
    Ensure-GroupJoin -GroupId ([long]$group2.id) -Email "learner@skillsync.com" -Tokens $tokens
    Ensure-GroupMessage -GroupId ([long]$group2.id) -Email "learner@skillsync.com" -Content "Glad to be here. I want to get better at typed component APIs and modern React debugging workflows." -Tokens $tokens
}

Write-Step "[8/10] Seeding notifications"

if (Test-TableExists -Database "skillsync_notifications" -Table "notifications") {
    $notificationColumns = Get-TableColumns -Database "skillsync_notifications" -Table "notifications"

    if (($notificationColumns -contains "channel") -and ($notificationColumns -contains "dedupe_key") -and ($notificationColumns -contains "data")) {
        $notificationSql = @"
INSERT INTO notifications (user_id, type, title, message, data, channel, dedupe_key, is_read, created_at, delivered_at) VALUES
  ($learnerUserId, 'SESSION_ACCEPTED', 'Session Confirmed!', 'Your session "Spring Boot Security" with Ananya has been confirmed.', NULL, 'IN_APP', 'demo-notif-001', false, NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour'),
  ($learnerUserId, 'SESSION_ACCEPTED', 'Session Confirmed!', 'Your session "System Design Interview Prep" with Rajeev has been confirmed.', NULL, 'IN_APP', 'demo-notif-002', true, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
  ($shashankUserId, 'SESSION_ACCEPTED', 'Session Confirmed!', 'Your session "Spring Boot Security Configuration" with Ananya has been confirmed.', NULL, 'IN_APP', 'demo-notif-003', false, NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'),
  ($rajeevUserId, 'SESSION_BOOKED', 'New Session Request', 'Shashank has booked a session "AWS Cloud Architecture Planning" with you.', NULL, 'IN_APP', 'demo-notif-004', false, NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '30 minutes'),
  ($adminId, 'MENTOR_APPLIED', 'New Mentor Application', 'Ananya Iyer has applied to become a mentor.', NULL, 'IN_APP', 'demo-notif-005', true, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),
  ($adminId, 'MENTOR_APPLIED', 'New Mentor Application', 'Rajeev Kumar has applied to become a mentor.', NULL, 'IN_APP', 'demo-notif-006', true, NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days')
ON CONFLICT (dedupe_key) DO NOTHING;
"@
        Invoke-SqlNonQuery -Database "skillsync_notifications" -Sql $notificationSql
        Write-Info "Seeded notifications with dedupe keys."
    } elseif (Test-ColumnsPresent -Database "skillsync_notifications" -Table "notifications" -Columns @("user_id", "type", "title", "message", "data", "is_read", "created_at", "delivered_at")) {
        $notificationSql = @"
INSERT INTO notifications (user_id, type, title, message, data, is_read, created_at, delivered_at) VALUES
  ($learnerUserId, 'SESSION_ACCEPTED', 'Session Confirmed!', 'Your session "Spring Boot Security" with Ananya has been confirmed.', NULL, false, NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour'),
  ($learnerUserId, 'SESSION_ACCEPTED', 'Session Confirmed!', 'Your session "System Design Interview Prep" with Rajeev has been confirmed.', NULL, true, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
  ($shashankUserId, 'SESSION_ACCEPTED', 'Session Confirmed!', 'Your session "Spring Boot Security Configuration" with Ananya has been confirmed.', NULL, false, NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'),
  ($rajeevUserId, 'SESSION_BOOKED', 'New Session Request', 'Shashank has booked a session "AWS Cloud Architecture Planning" with you.', NULL, false, NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '30 minutes'),
  ($adminId, 'MENTOR_APPLIED', 'New Mentor Application', 'Ananya Iyer has applied to become a mentor.', NULL, true, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),
  ($adminId, 'MENTOR_APPLIED', 'New Mentor Application', 'Rajeev Kumar has applied to become a mentor.', NULL, true, NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days')
ON CONFLICT DO NOTHING;
"@
        Invoke-SqlNonQuery -Database "skillsync_notifications" -Sql $notificationSql
        Write-Info "Seeded notifications."
    } else {
        Write-WarnLine "Skipping notifications seed because the notifications table columns do not match expected fields"
    }
} else {
    Write-WarnLine "Skipping notifications seed because notifications table does not exist"
}

Write-Step "[9/10] Seeding payments and mentor earnings"

if (Test-TableExists -Database "skillsync_payments" -Table "payments") {
    $paymentColumns = Get-TableColumns -Database "skillsync_payments" -Table "payments"
    $requiredPaymentColumns = @(
        "session_id", "payer_id", "payee_id", "amount", "currency", "status", "idempotency_key",
        "razorpay_order_id", "razorpay_payment_id", "razorpay_signature", "captured_amount",
        "refunded_amount", "provider_receipt", "provider_status", "created_at", "updated_at", "captured_at"
    )

    $canSeedPayments = $true
    foreach ($column in $requiredPaymentColumns) {
        if ($paymentColumns -notcontains $column) {
            $canSeedPayments = $false
            break
        }
    }

    if ($canSeedPayments) {
        $paymentSql = @"
INSERT INTO payments (session_id, payer_id, payee_id, amount, currency, status, idempotency_key, razorpay_order_id, razorpay_payment_id, razorpay_signature, captured_amount, refunded_amount, provider_receipt, provider_status, created_at, updated_at, captured_at) VALUES
  ($session1Id, $learnerUserId, $mentorUserId, 800.00, 'INR', 'CAPTURED', 'idem-001', 'order_demo_001', 'pay_demo_001', 'sig_demo_001', 800.00, 0.00, 'receipt_demo_001', 'captured', NOW() - INTERVAL '3 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
  ($session2Id, $shashankUserId, $rajeevUserId, 2250.00, 'INR', 'CAPTURED', 'idem-002', 'order_demo_002', 'pay_demo_002', 'sig_demo_002', 2250.00, 0.00, 'receipt_demo_002', 'captured', NOW() - INTERVAL '6 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
  ($session3Id, $learnerUserId, $rajeevUserId, 1500.00, 'INR', 'CAPTURED', 'idem-003', 'order_demo_003', 'pay_demo_003', 'sig_demo_003', 1500.00, 0.00, 'receipt_demo_003', 'captured', NOW() - INTERVAL '4 days', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days')
ON CONFLICT (idempotency_key) DO NOTHING;
"@
        Invoke-SqlNonQuery -Database "skillsync_payments" -Sql $paymentSql
        Write-Info "Seeded payments."
    } else {
        Write-WarnLine "Skipping payments seed because the payments table columns do not match the entity schema"
    }
} else {
    Write-WarnLine "Skipping payments seed because payments table does not exist"
}

if (Test-TableExists -Database "skillsync_payments" -Table "mentor_earnings") {
    $earningsColumns = Get-TableColumns -Database "skillsync_payments" -Table "mentor_earnings"

    if (($earningsColumns -contains "mentor_id") -and ($earningsColumns -contains "total_earned") -and ($earningsColumns -contains "pending_balance") -and ($earningsColumns -contains "available_balance") -and ($earningsColumns -contains "locked_balance") -and ($earningsColumns -contains "total_withdrawn") -and ($earningsColumns -contains "updated_at")) {
        $ananyaExists = Get-SqlValue -Database "skillsync_payments" -Sql "SELECT EXISTS (SELECT 1 FROM mentor_earnings WHERE mentor_id = $mentorMentorId);"
        $rajeevExists = Get-SqlValue -Database "skillsync_payments" -Sql "SELECT EXISTS (SELECT 1 FROM mentor_earnings WHERE mentor_id = $rajeevMentorId);"

        if ($ananyaExists -ne "t") {
            $ananyaEarningsSql = @"
INSERT INTO mentor_earnings (mentor_id, total_earned, pending_balance, available_balance, locked_balance, total_withdrawn, updated_at)
VALUES ($mentorMentorId, 800.00, 800.00, 0.00, 0.00, 0.00, NOW());
"@
            Invoke-SqlNonQuery -Database "skillsync_payments" -Sql $ananyaEarningsSql
        }

        if ($rajeevExists -ne "t") {
            $rajeevEarningsSql = @"
INSERT INTO mentor_earnings (mentor_id, total_earned, pending_balance, available_balance, locked_balance, total_withdrawn, updated_at)
VALUES ($rajeevMentorId, 3750.00, 3750.00, 0.00, 0.00, 0.00, NOW());
"@
            Invoke-SqlNonQuery -Database "skillsync_payments" -Sql $rajeevEarningsSql
        }

        Write-Info "Seeded mentor earnings using current schema."
    } elseif (($earningsColumns -contains "mentor_id") -and ($earningsColumns -contains "total_earned") -and ($earningsColumns -contains "total_pending") -and ($earningsColumns -contains "total_paid_out") -and ($earningsColumns -contains "created_at") -and ($earningsColumns -contains "updated_at")) {
        $ananyaExists = Get-SqlValue -Database "skillsync_payments" -Sql "SELECT EXISTS (SELECT 1 FROM mentor_earnings WHERE mentor_id = $mentorMentorId);"
        $rajeevExists = Get-SqlValue -Database "skillsync_payments" -Sql "SELECT EXISTS (SELECT 1 FROM mentor_earnings WHERE mentor_id = $rajeevMentorId);"

        if ($ananyaExists -ne "t") {
            $ananyaLegacySql = @"
INSERT INTO mentor_earnings (mentor_id, total_earned, total_pending, total_paid_out, created_at, updated_at)
VALUES ($mentorMentorId, 800.00, 800.00, 0.00, NOW(), NOW());
"@
            Invoke-SqlNonQuery -Database "skillsync_payments" -Sql $ananyaLegacySql
        }

        if ($rajeevExists -ne "t") {
            $rajeevLegacySql = @"
INSERT INTO mentor_earnings (mentor_id, total_earned, total_pending, total_paid_out, created_at, updated_at)
VALUES ($rajeevMentorId, 3750.00, 3750.00, 0.00, NOW(), NOW());
"@
            Invoke-SqlNonQuery -Database "skillsync_payments" -Sql $rajeevLegacySql
        }

        Write-Info "Seeded mentor earnings using legacy schema."
    } else {
        Write-WarnLine "Skipping mentor earnings seed because the table schema does not match supported shapes"
    }
} else {
    Write-WarnLine "Skipping mentor earnings seed because mentor_earnings table does not exist"
}

Write-Step "[10/10] Summary"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Demo Accounts" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  ADMIN" -ForegroundColor Yellow
Write-Host "    admin@skillsync.com / $PASSWORD"
Write-Host ""
Write-Host "  MENTORS" -ForegroundColor Yellow
Write-Host "    mentor@skillsync.com / $PASSWORD"
Write-Host "    rajeev@skillsync.com / $PASSWORD"
Write-Host ""
Write-Host "  LEARNERS" -ForegroundColor Yellow
Write-Host "    learner@skillsync.com / $PASSWORD"
Write-Host "    shashank@skillsync.com / $PASSWORD"
Write-Host ""
Write-Host "  Seeded groups:" -ForegroundColor Yellow
Write-Host "    Java Spring Boot Study Circle"
Write-Host "    React & TypeScript Guild"
Write-Host ""
Write-Host "  Session booking references:" -ForegroundColor Yellow
Write-Host "    $bookingRef1"
Write-Host "    $bookingRef2"
Write-Host "    $bookingRef3"
Write-Host "    $bookingRef4"
Write-Host "    $bookingRef5"
Write-Host "    $bookingRef6"
Write-Host ""
Write-Host "  JWTs were refreshed after role updates." -ForegroundColor Gray
Write-Host "  Re-run is safe for users, roles, sessions, reviews, notifications, and payments." -ForegroundColor Gray
Write-Host ""
