# How to Run SkillSync

Step-by-step guide to get the full SkillSync platform running locally.

**IMPORTANT: Startup order matters.** Docker infra (PostgreSQL, Redis, RabbitMQ) must be healthy before any backend service starts. If PostgreSQL is not ready, services will crash immediately with `Connection to localhost:5432 refused`.

---

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java JDK | 17 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 20+ | `node -v` |
| Docker Desktop | Latest | `docker compose version` |
| PowerShell | 5.1+ | `$PSVersionTable.PSVersion` |

**Before anything else:** Open Docker Desktop and wait for it to fully start (system tray icon should be green/stable).

---

## Fast Local Workflow (Recommended)

PowerShell helper scripts handle startup ordering, readiness checks, and logging automatically:

```powershell
cd C:\Users\LENOVO\Specializations\Capgemini\SkillSync

# First time only: build Maven artifacts + install npm deps
.\scripts\setup-local.ps1

# Start everything (Docker infra + Config Server + Eureka + all services + gateway + frontend)
.\scripts\start-local.ps1

# Fresh-machine shortcut: setup + start in one command
.\scripts\start-local.ps1 -Bootstrap

# If Docker infra is ALREADY running from a previous session:
.\scripts\start-local.ps1 -SkipInfra

# Stop all script-managed services (keeps Docker infra running)
.\scripts\stop-local.ps1

# Stop everything including Docker infra
.\scripts\stop-local.ps1 -StopInfra
```

Logs are written to `runtime-logs\` (one `.out.log` and `.err.log` per service).

**When to use `-SkipInfra`:** Only if you see PostgreSQL, Redis, RabbitMQ, etc. already running in Docker Desktop from a previous session. If unsure, run without the flag — it will just start what is not already running.

---

## Manual Workflow (Step by Step)

Use this if the scripts do not work, or if you want to see each service start in its own terminal.

### Step 1 - Start Infrastructure (Docker)

**Docker Desktop must be running first.** Then:

```bash
cd infra
docker compose up -d
```

This starts 7 containers (n8n is optional — if port 5678 is busy, the rest still work fine):

| Service | Port | UI / Access |
|---------|------|-------------|
| PostgreSQL 15 | 5432 | `psql -h localhost -U skillsync` (password: `skillsync`) |
| Redis 7 | 6379 | `redis-cli` |
| RabbitMQ 3 | 5672 / 15672 | http://localhost:15672 (`skillsync` / `skillsync`) |
| Zipkin | 9411 | http://localhost:9411 |
| n8n | 5678 | http://localhost:5678 (`admin` / `skillsync`) |
| Prometheus | 9090 | http://localhost:9090 |
| Grafana | 3001 | http://localhost:3001 (`admin` / `skillsync`) |

Wait until PostgreSQL and RabbitMQ show `healthy` (they have health checks configured):

```bash
docker compose ps
```

PostgreSQL and RabbitMQ should show `healthy`. The others show `running` (no health check configured). If n8n fails due to port conflict, that is fine — it is not required for core functionality.

---

## Step 2 - First-Time Build

On a fresh clone, you must install the shared `common` module into your local Maven cache before any service can compile:

```bash
cd backend
mvn -DskipTests install
```

This builds all modules and installs `common` (shared DTOs, exceptions, etc.) into `~/.m2/repository`. You only need to do this once, or again if `common` module code changes.

Also install frontend dependencies:

```bash
cd frontend
npm install
```

## Step 3 - Start Config Server

Open a terminal and run:

```bash
cd backend
mvn spring-boot:run -pl config-server
```

Wait for: `Started ConfigServerApplication on port 8888`

Verify: http://localhost:8888/auth-service/default should return JSON config.

---

## Step 4 - Start Eureka Server

Open a new terminal:

```bash
cd backend
mvn spring-boot:run -pl eureka-server
```

Wait for: `Started EurekaServerApplication on port 8761`

Verify: http://localhost:8761 should load the Eureka dashboard.

---

## Step 5 - Start All Business Services

Open a new terminal for each service or use tabs. Start them in any order — they will register with Eureka automatically.

**Prerequisite:** Step 2 (first-time build) must have completed so the `common` module is in your local Maven cache.

```bash
cd C:\Users\LENOVO\Specializations\Capgemini\SkillSync\backend

# Terminal 1
mvn spring-boot:run -pl auth-service

# Terminal 2
mvn spring-boot:run -pl user-service

# Terminal 3
mvn spring-boot:run -pl mentor-service

# Terminal 4
mvn spring-boot:run -pl skill-service

# Terminal 5
mvn spring-boot:run -pl session-service

# Terminal 6
mvn spring-boot:run -pl payment-service

# Terminal 7
mvn spring-boot:run -pl review-service

# Terminal 8
mvn spring-boot:run -pl group-service

# Terminal 9
mvn spring-boot:run -pl notification-service

# Terminal 10
mvn spring-boot:run -pl audit-service
```

Each service creates its own PostgreSQL database and tables on first startup via Flyway migrations. If a service fails to connect to PostgreSQL, go back to Step 1 and confirm `docker compose ps` shows postgres as `healthy`.

### Service Ports

| Service | Port |
|---------|------|
| auth-service | 8081 |
| user-service | 8082 |
| mentor-service | 8083 |
| skill-service | 8084 |
| session-service | 8085 |
| payment-service | 8086 |
| review-service | 8087 |
| group-service | 8088 |
| notification-service | 8089 |
| audit-service | 8090 |

---

## Step 6 - Start API Gateway

Open a new terminal:

```bash
cd backend
mvn spring-boot:run -pl api-gateway
```

Wait for: `Started ApiGatewayApplication on port 8091`

Verify: `http://localhost:8091/actuator/health` should return `{"status":"UP"}` when the gateway configuration allows direct health access.

Check Eureka at http://localhost:8761 - all 11 services should be registered.

---

## Step 7 - Start Frontend

Open a new terminal:

```bash
cd frontend
npm install    # only needed first time
npm run dev
```

The frontend starts on http://localhost:3000.

It proxies API calls to the gateway:
- `/api/*` -> `http://localhost:8091`
- `/ws/*` -> `http://localhost:8089` (WebSocket)

---

## Step 8 - Use the App

Open http://localhost:3000 in your browser.

### Create accounts to test all 3 roles

1. Register a learner at `/register`.
2. Register a mentor, then apply as mentor from the profile page.
3. Create an admin account by promoting a registered user in PostgreSQL:

```sql
-- Connect to auth database
psql -h localhost -U skillsync -d skillsync_auth

-- After registering a user, promote them to admin:
UPDATE users SET roles = 'ADMIN,LEARNER' WHERE email = 'your-admin@email.com';
```

### Key Flows to Try

| Flow | Steps |
|------|-------|
| Learner books a session | Browse mentors -> View profile -> Book session -> Pay with Razorpay test card -> View in session history |
| Mentor manages sessions | Login as mentor -> Accept/reject pending sessions -> Mark sessions complete -> View earnings |
| Admin dashboard | Login as admin -> View analytics -> Approve mentor applications -> Manage users -> View audit logs |
| Study groups | Create a group -> Other users join -> Send messages |
| Notifications | Bell icon in navbar -> Dropdown shows notifications -> Mark as read |

### Razorpay Test Card

For payment testing:
- Card: `4111 1111 1111 1111`
- Expiry: Any future date
- CVV: Any 3 digits
- OTP: Use the Razorpay test OTP flow

---

## Step 9 - Run E2E Test Script

Once all services and the gateway are up, run the automated API test suite:

```powershell
.\scripts\test-e2e.ps1
```

This runs 18 tests covering the full user workflow:
- Health checks for all 11 services
- Register and login (learner + mentor)
- Profile read/update
- Browse skills and mentors (public endpoints)
- Mentor application
- Notifications and unread count
- Study group creation, messaging
- User preferences

All tests should show PASS. If you see 401 errors on tests after registration, the gateway likely needs a restart to pick up code changes. If you see `Connection refused` errors, check that Docker infra and all backend services are running.

---

## Observability Dashboards

Once services are running, check:

| Dashboard | URL | Credentials |
|-----------|-----|-------------|
| Eureka | http://localhost:8761 | none |
| RabbitMQ | http://localhost:15672 | skillsync / skillsync |
| Zipkin | http://localhost:9411 | none |
| Prometheus | http://localhost:9090 | none |
| Grafana | http://localhost:3001 | admin / skillsync |
| n8n | http://localhost:5678 | admin / skillsync |

### Grafana Dashboards

Grafana auto-loads 3 dashboards:
1. Service Health - up/down, request rate, latency, error rate, JVM memory
2. Business KPIs - HTTP requests by service, DB connection pools, cache hit ratio
3. Alerts - webhook failures, payment verification issues

---

## Shutting Down

```bash
# Stop frontend: Ctrl+C in the frontend terminal

# Stop each backend service: Ctrl+C in each terminal

# Stop Docker infrastructure:
cd infra
docker compose down

# To also delete all data volumes:
docker compose down -v
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Connection to localhost:5432 refused` | PostgreSQL is not running. Run `cd infra && docker compose up -d` and wait for postgres to show `healthy` in `docker compose ps` |
| Service cannot connect to Config Server | Config Server (8888) must start before all other backend services |
| Service not showing in Eureka | Wait 30s for registration, then check `runtime-logs/<service>.err.log` |
| Frontend shows network errors | Ensure API Gateway (8091) is running and services are registered in Eureka |
| Port already in use | Find the process: `netstat -ano \| findstr :<PORT>`, then kill it: `taskkill /PID <PID> /F` |
| n8n container fails to start | Port 5678 is occupied. This is non-blocking — n8n is optional |
| Flyway migration error | Check service logs. If schema is corrupted, drop and recreate the database: `docker exec -it infra-postgres-1 psql -U skillsync -c "DROP DATABASE skillsync_<service>;"` then restart the service |
| RabbitMQ connection refused | RabbitMQ takes about 30s to start. Wait for `healthy` status in `docker compose ps` |
| `Asia/Calcutta` timezone error | Already fixed via `backend/.mvn/jvm.config`. If it reappears, verify the file contains `-Duser.timezone=Asia/Kolkata` |
| E2E tests all show 401 | The gateway is running old code. Restart it: stop the gateway process, then `cd backend && mvn spring-boot:run -pl api-gateway` |
| `common` module not found during build | Run `cd backend && mvn -DskipTests install` to install shared modules into local Maven cache |

### Startup Order Checklist

If anything goes wrong, verify this exact order:

1. Docker Desktop is running
2. `docker compose up -d` in `infra/` — wait for postgres and rabbitmq to be `healthy`
3. Config Server on 8888
4. Eureka Server on 8761
5. All 10 business services (8081-8090) — any order among themselves
6. API Gateway on 8091
7. Frontend on 3000

---

## Quick Reference - All Ports

| Port | Service |
|------|---------|
| 3000 | Frontend (Vite dev) |
| 3001 | Grafana |
| 5432 | PostgreSQL |
| 5672 | RabbitMQ (AMQP) |
| 5678 | n8n |
| 6379 | Redis |
| 8091 | API Gateway |
| 8081 | Auth Service |
| 8082 | User Service |
| 8083 | Mentor Service |
| 8084 | Skill Service |
| 8085 | Session Service |
| 8086 | Payment Service |
| 8087 | Review Service |
| 8088 | Group Service |
| 8089 | Notification Service |
| 8090 | Audit Service |
| 8761 | Eureka Server |
| 8888 | Config Server |
| 9090 | Prometheus |
| 9411 | Zipkin |
| 15672 | RabbitMQ Management UI |
