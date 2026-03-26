The SkillSync frontend (React + Vite at localhost:5173) shows content for a split second after login then goes BLANK. Debug this.

Context:
- Gateway is at localhost:8091, frontend proxies /api -> gateway via vite.config.ts
- Login works (JWT token is returned and stored)
- After login, the dashboard page loads briefly then goes completely blank
- This likely means a React component is crashing (unhandled error in rendering)

Steps:
1. Read frontend/src/App.tsx to understand the route structure and error boundaries
2. Read the dashboard components: features/dashboard/LearnerDashboard.tsx, features/dashboard/MentorDashboard.tsx, features/dashboard/AdminDashboard.tsx
3. Look for common crash causes:
   - Accessing .data on undefined (API response shape mismatch)
   - Calling .toFixed(), .map(), .filter() on undefined/null
   - Missing null checks on API responses
4. Check all service files (services/*.ts) and compare the TypeScript types vs actual API response shapes
5. Specifically check: the mentor search was just changed from `ApiResponse<PagedResponse<MentorSummary>>` to `PagedResponse<MentorSummary>` — but the MentorSummary type has fields like `rating`, `name`, `skills`, `isAvailable` that the actual API response does NOT return (it returns `avgRating`, `userId`, `skillIds`, no `name`/`isAvailable`)

The actual mentor search API response looks like:
```json
{"content":[{"id":1,"userId":10,"headline":"Expert in Java","hourlyRate":500.00,"avgRating":0.00,"totalSessions":0,"totalReviews":0,"skillIds":[]}],"page":0,"size":6,"totalElements":4,"totalPages":1,"last":true}
```

Note: the response has `avgRating` not `rating`, `skillIds` not `skills`, no `name` field, no `isAvailable` field.

6. Check if LearnerDashboard.tsx or MentorDiscoveryPage.tsx calls `.rating.toFixed(1)` or `.name` or `.skills.map()` on mentor objects — these would crash because those fields are undefined in the actual response
7. Find ALL such mismatches and fix them — make the frontend resilient with null checks or field mappings
8. Also check if there's an ErrorBoundary that might be catching and blanking the screen
9. Check the auth flow — after login, what component renders? Does it try to fetch /users/me? Does that response match expectations?

Fix all issues you find. Make the dashboard load successfully after login.
