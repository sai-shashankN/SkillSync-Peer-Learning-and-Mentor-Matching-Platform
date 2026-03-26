#!/usr/bin/env bash
set -euo pipefail

echo "Checking Eureka registrations..."

eureka_response="$(curl -sf http://localhost:8761/eureka/apps)"

services=(
  "AUTH-SERVICE"
  "USER-SERVICE"
  "MENTOR-SERVICE"
  "SKILL-SERVICE"
  "SESSION-SERVICE"
  "PAYMENT-SERVICE"
  "REVIEW-SERVICE"
  "GROUP-SERVICE"
  "NOTIFICATION-SERVICE"
  "AUDIT-SERVICE"
)

for service in "${services[@]}"; do
  if grep -q "$service" <<<"$eureka_response"; then
    echo "Registered: $service"
  else
    echo "Missing Eureka registration: $service" >&2
    exit 1
  fi
done

declare -A ports=(
  [auth-service]=8081
  [user-service]=8082
  [mentor-service]=8083
  [skill-service]=8084
  [session-service]=8085
  [payment-service]=8086
  [review-service]=8087
  [group-service]=8088
  [notification-service]=8089
  [audit-service]=8090
)

for service in "${!ports[@]}"; do
  port="${ports[$service]}"
  health_response="$(curl -sf "http://localhost:${port}/actuator/health")"
  if grep -q '"status":"UP"' <<<"$health_response"; then
    echo "Health OK: $service"
  else
    echo "Health check failed: $service" >&2
    exit 1
  fi
done
