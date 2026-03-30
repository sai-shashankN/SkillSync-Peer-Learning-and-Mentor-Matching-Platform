# SkillSync

SkillSync is a peer learning and mentor matching platform built as a Capgemini portfolio project. It combines a Spring Boot microservices backend, a React single-page frontend, event-driven integrations, and production-oriented observability and deployment infrastructure.

## Architecture Overview

- Spring Boot 3.2.5 microservices in a Maven multi-module monorepo with 14 backend modules
- Spring Cloud 2023.0.1 for Eureka discovery, centralized configuration, and API Gateway routing
- PostgreSQL 15 with one database per service and Flyway-managed migrations
- RabbitMQ topic-based eventing for asynchronous workflows
- Redis-backed caching with `@Cacheable`
- React 19, TypeScript, and Vite for the frontend SPA
- n8n for workflow automation and integration orchestration
- Prometheus, Grafana, and Zipkin for metrics, dashboards, and distributed tracing

## Tech Stack

| Category | Stack |
| --- | --- |
| Backend | Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.1, Spring Security, Spring Data JPA, Flyway, Maven |
| Frontend | React 19, TypeScript, Vite, React Router, TanStack Query, Zustand, Tailwind CSS |
| Infrastructure | PostgreSQL 15, Redis 7, RabbitMQ 3 Management, Docker, Docker Compose, Nginx |
| Observability | Spring Boot Actuator, Micrometer, Prometheus, Grafana, Zipkin |
| CI/CD | GitHub Actions, Dockerfiles per service, OWASP Dependency Check |

## Prerequisites

- Java 17
- Node 20
- Maven 3.9+
- Docker and Docker Compose

## Local Scripts

For day-to-day local work, you can use the PowerShell helpers instead of opening a terminal per service:

```powershell
# One-time bootstrap for local Maven and npm dependencies
.\scripts\setup-local.ps1

# Start infra, backend services, gateway, and frontend
.\scripts\start-local.ps1

# Fresh-machine shortcut: setup + start
.\scripts\start-local.ps1 -Bootstrap

# Stop the processes started by the script
.\scripts\stop-local.ps1

# Stop the script-managed processes and Docker infra
.\scripts\stop-local.ps1 -StopInfra
```

The scripts write logs to `runtime-logs/` and only stop the processes they started.

## Quick Start

```bash
# Optional one-time bootstrap for local shared modules and frontend deps
powershell -ExecutionPolicy Bypass -File .\scripts\setup-local.ps1

# 1. Start infrastructure
cd infra && docker-compose up -d

# 2. Start Config Server
cd ../backend && mvn spring-boot:run -pl config-server

# 3. Start Eureka Server
mvn spring-boot:run -pl eureka-server

# 4. Start business services (in any order)
mvn spring-boot:run -pl auth-service
mvn spring-boot:run -pl user-service
mvn spring-boot:run -pl mentor-service
mvn spring-boot:run -pl skill-service
mvn spring-boot:run -pl session-service
mvn spring-boot:run -pl payment-service
mvn spring-boot:run -pl review-service
mvn spring-boot:run -pl group-service
mvn spring-boot:run -pl notification-service
mvn spring-boot:run -pl audit-service

# 5. Start API Gateway
mvn spring-boot:run -pl api-gateway

# 6. Start frontend
cd ../frontend && npm install && npm run dev
```

If you are starting services manually on a fresh machine, run the bootstrap first or add `-am` when starting modules so Maven also builds shared modules such as `common`.

## Production Deployment

```bash
mvn clean package -DskipTests -f backend/pom.xml
# Build Docker images
docker-compose -f infra/docker-compose.prod.yml up -d
```

The production compose profile runs infrastructure, observability, all Spring services, n8n, and the frontend container on a shared Docker network using the `docker` Spring profile.

## Service Endpoints

| Component | Port | Notes |
| --- | --- | --- |
| Eureka Server | 8761 | Service registry |
| Config Server | 8888 | Centralized configuration |
| API Gateway | 8091 | Entry point for frontend and clients |
| Auth Service | 8081 | Authentication and authorization |
| User Service | 8082 | Learner and profile domain |
| Mentor Service | 8083 | Mentor onboarding and discovery |
| Skill Service | 8084 | Skills catalog and matching |
| Session Service | 8085 | Session booking and scheduling |
| Payment Service | 8086 | PayPal Sandbox payment workflows |
| Review Service | 8087 | Reviews, ratings, badges |
| Group Service | 8088 | Peer study groups |
| Notification Service | 8089 | Notifications and websocket support |
| Audit Service | 8090 | Audit logs and admin visibility |
| Frontend | 80 | Production SPA behind Nginx |
| Grafana | 3001 | `admin` / `skillsync` |
| Prometheus | 9090 | Metrics scraping and query UI |
| Zipkin | 9411 | Distributed tracing UI |
| n8n | 5678 | Workflow automation |
| RabbitMQ Management | 15672 | Messaging management UI |

## API Documentation

Gateway routes are exposed through the API Gateway on port `8091`:

- `/auth/**` -> `auth-service`
- `/users/**` -> `user-service`
- `/mentors/**` -> `mentor-service`
- `/skills/**` -> `skill-service`
- `/sessions/**` -> `session-service`
- `/payments/**` -> `payment-service`
- `/reviews/**` -> `review-service`
- `/groups/**` -> `group-service`
- `/notifications/**` -> `notification-service`
- `/audit/**` -> `audit-service`

Swagger UI is available per Spring service at `/swagger-ui.html` when the service is running locally.

## Observability

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001` with credentials `admin` / `skillsync`
- Zipkin: `http://localhost:9411`
- Spring Boot metrics: `/actuator/prometheus` on each backend service

Grafana provisioning includes:

- `SkillSync - Service Health`
- `SkillSync - Business KPIs`
- `SkillSync - Alerts`

## Project Structure

```text
SkillSync/
|-- .github/
|   `-- workflows/
|       `-- ci.yml
|-- backend/
|   |-- api-gateway/
|   |-- audit-service/
|   |-- auth-service/
|   |-- common/
|   |-- config-server/
|   |-- eureka-server/
|   |-- group-service/
|   |-- mentor-service/
|   |-- notification-service/
|   |-- payment-service/
|   |-- review-service/
|   |-- session-service/
|   |-- skill-service/
|   |-- user-service/
|   |-- owasp-suppressions.xml
|   `-- pom.xml
|-- frontend/
|   |-- src/
|   |-- Dockerfile
|   `-- nginx.conf
|-- infra/
|   |-- docker-compose.yml
|   |-- docker-compose.prod.yml
|   |-- grafana/
|   |-- prometheus/
|   `-- init-databases.sql
|-- n8n/
|-- BLUEPRINT.md
|-- PLAN.md
`-- README.md
```

## Key Features

- Mentor matching based on skill interests and mentor availability
- Session booking workflows with PayPal Sandbox-backed payment integration
- Peer study groups and collaborative learning flows
- Review, rating, and badge system for reputation building
- Admin analytics, approval flows, and audit visibility
- Real-time notifications and websocket delivery
- n8n-powered workflow automation for integration and operational tasks

## CI/CD and Quality

- GitHub Actions workflow for backend verification, frontend type-check/build, and Docker image builds
- Service-level Dockerfiles for all deployable backend modules plus the frontend
- OWASP Dependency Check plugin configured in the backend parent POM with a shared suppressions file
- Docker production profile for bringing up the full stack with observability enabled
