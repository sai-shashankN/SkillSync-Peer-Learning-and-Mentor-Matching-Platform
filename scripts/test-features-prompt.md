You are testing the SkillSync application. All services are running, gateway is on port 8091.

Write and execute a PowerShell script called `scripts/test-features.ps1` that tests every major feature by role. The script should:

1. Use `Invoke-WebRequest -UseBasicParsing` for all HTTP calls (PS5.1 compatible)
2. When parsing response content, check if `$r.Content` is `[byte[]]` before calling `[System.Text.Encoding]::UTF8.GetString()` — if it's already a string, use it directly
3. NEVER use `ConvertFrom-Json -Depth` (PS5.1 doesn't support -Depth)
4. Print PASS/FAIL for each test with the test name

Test accounts (all password: Demo@1234):
- admin@skillsync.com (ROLE_ADMIN + ROLE_MENTOR + ROLE_LEARNER)  
- priya.mentor@skillsync.com (ROLE_MENTOR + ROLE_LEARNER)
- arjun.learner@skillsync.com (ROLE_LEARNER)

Gateway base URL: http://localhost:8091

Tests to run (in order):

**Auth Tests:**
1. POST /auth/login with admin — expect 200, accessToken in response
2. POST /auth/login with mentor — expect 200
3. POST /auth/login with learner — expect 200
4. GET /auth/validate with admin token — expect 200

**Profile Tests (use user-service via gateway):**
5. GET /users/me with admin token — expect 200, email matches
6. GET /users/me with mentor token — expect 200
7. GET /users/me with learner token — expect 200

**Skills Tests (public):**
8. GET /skills — expect 200, array with 12+ skills
9. GET /skills/1 — expect 200, skill object

**Mentor Tests:**
10. GET /mentors — expect 200 (public listing)
11. GET /mentors/me with mentor token — expect 200, mentor profile
12. GET /mentors/me with learner token — expect 400 or 404 (not a mentor)

**Study Groups Tests:**
13. GET /groups with learner token — expect 200
14. GET /groups with mentor token — expect 200

**Notifications Tests:**
15. GET /notifications with learner token — expect 200
16. GET /notifications with admin token — expect 200

**Admin Tests:**
17. GET /users with admin token — expect 200 (admin can list users)
18. GET /users with learner token — expect 403 (learner cannot list users)

Print a summary at the end: X/N tests passed.

IMPORTANT: 
- Do NOT use `-Depth` parameter on `ConvertFrom-Json` anywhere
- Handle the byte[]/string content type properly for PS5.1
- Use `$ErrorActionPreference = "Continue"` so one failure doesn't stop all tests
- Make the script robust — catch exceptions per-test
