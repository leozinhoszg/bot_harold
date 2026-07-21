# API Bridge Bot - Technical Specification

> Version: 1.0
> Stack: Java 21 + Spring Boot 3 + SQLite + Redis
> Objective: Consume one or more third-party APIs, process the returned data, and automatically publish messages to Telegram chats.

---

# 1. Project Overview

The system is an integration service responsible for periodically polling external APIs, detecting new or modified records, persisting history, and publishing formatted notifications to Telegram.

The architecture must be modular so new APIs can be added without changing the core application.

---

# 2. Technology Stack

| Component | Technology |
|------------|------------|
| Language | Java 21 LTS |
| Framework | Spring Boot 3 |
| Build | Maven |
| Database | SQLite |
| Cache | Redis |
| HTTP Client | Spring WebClient |
| Scheduling | Spring Scheduler |
| Telegram | Telegram Bot API |
| Serialization | Jackson |
| Logging | SLF4J + Logback |
| Testing | JUnit 5 + Mockito + Testcontainers |

---

# 3. Architecture

```
                    Third Party APIs
                          │
                  HTTPS / REST / JSON
                          │
                          ▼
                  Api Client Layer
                          │
                          ▼
                  Processing Engine
         ┌────────────────┼────────────────┐
         ▼                ▼                ▼
   Rule Engine      Redis Cache      SQLite Storage
         │                │                │
         └────────────────┴────────────────┘
                          │
                          ▼
                  Telegram Publisher
                          │
                          ▼
                  Telegram Bot API
                          │
                          ▼
                     Telegram Chat
```

---

# 4. Core Modules

## Configuration

Responsibilities:

- Load environment variables
- Load API credentials
- Configure Redis
- Configure SQLite
- Configure Telegram
- Configure Scheduler

---

## API Client

Responsibilities:

- Execute HTTP requests
- OAuth2 support
- Bearer Token
- API Key
- Basic Auth
- Timeout
- Retry
- Pagination
- Rate limiting

Output:

```java
ApiResponse<T>
```

---

## Processing Engine

Responsibilities:

- Validate response
- Normalize data
- Remove duplicates
- Compare with cache
- Detect changes
- Generate notification events

This module should be completely independent from Telegram.

---

## Redis Cache

Purpose:

Avoid duplicated notifications.

Possible keys:

```
last_hash

last_timestamp

last_id

api_status

processing_lock
```

TTL configurable.

---

## SQLite Repository

Responsibilities

Persist

- Full API responses
- Notification history
- Processing logs
- Errors
- Statistics

---

## Telegram Module

Responsibilities

- Send Message
- Edit Message
- Delete Message
- Markdown formatting
- HTML formatting
- Retry failed sends

Future support:

- Images
- Documents
- Inline Buttons
- Polls

---

# 5. Project Structure

```
src/main/java

config/

controller/

scheduler/

client/

service/

repository/

entity/

dto/

mapper/

telegram/

cache/

exception/

util/

metrics/

validation/
```

---

# 6. Domain Model

## ApiIntegration

Represents one external API.

Properties

```
id

name

url

authentication

enabled

schedule

chatId
```

---

## ApiResponse

```
status

httpCode

timestamp

payload

headers
```

---

## Notification

```
id

title

message

type

createdAt

telegramMessageId
```

---

# 7. Database Schema

## integrations

| Column | Type |
|---------|------|
| id | INTEGER |
| name | TEXT |
| url | TEXT |
| auth_type | TEXT |
| token | TEXT |
| enabled | BOOLEAN |
| cron | TEXT |
| chat_id | TEXT |

---

## api_history

| Column | Type |
|---------|------|
| id | INTEGER |
| integration_id | INTEGER |
| hash | TEXT |
| payload | TEXT |
| created_at | DATETIME |

---

## notifications

| Column | Type |
|---------|------|
| id | INTEGER |
| integration_id | INTEGER |
| telegram_message_id | TEXT |
| message | TEXT |
| status | TEXT |
| created_at | DATETIME |

---

## logs

| Column | Type |
|---------|------|
| id | INTEGER |
| level | TEXT |
| message | TEXT |
| stacktrace | TEXT |
| created_at | DATETIME |

---

# 8. Processing Flow

```
Scheduler

↓

Acquire Lock (Redis)

↓

Call External API

↓

Validate Response

↓

Generate Hash

↓

Hash Changed?

├── NO
│      Exit
│
└── YES

↓

Save Response

↓

Generate Notification

↓

Send Telegram Message

↓

Persist Notification

↓

Update Cache

↓

Release Lock
```

---

# 9. Scheduler

Each integration has its own schedule.

Examples

```
Every minute

Every 30 seconds

Cron

Manual execution

Webhook trigger (future)
```

---

# 10. Configuration

application.yml

```yaml
spring:

  datasource:
    url: jdbc:sqlite:data/database.db

redis:
  host: localhost
  port: 6379

telegram:
  token:
  defaultChat:

integrations:
  pollingThreads: 5
```

---

# 11. Error Handling

External API unavailable

- Retry
- Exponential Backoff
- Log

Telegram unavailable

- Retry
- Queue message

Redis unavailable

- Continue using SQLite

SQLite locked

- Retry transaction

Unexpected Exception

- Log
- Alert
- Continue processing

---

# 12. Logging

Log every important operation.

Examples

```
Polling Started

Polling Finished

API Response Time

Cache Hit

Cache Miss

Notification Sent

Notification Failed

Retry Started

Retry Finished

Database Saved

Processing Time
```

---

# 13. Security

Requirements

- HTTPS only
- Environment Variables
- Secret masking
- Connection timeout
- Input validation
- Output sanitization
- SQL Injection protection
- Secure HTTP Headers

---

# 14. Performance Goals

Maximum API response

```
< 2 seconds
```

Telegram delivery

```
< 3 seconds
```

Processing

```
< 500ms
```

Redis lookup

```
< 20ms
```

---

# 15. Future Improvements

- Kafka
- RabbitMQ
- Web Dashboard
- Prometheus Metrics
- Grafana
- Swagger UI
- OAuth Login
- REST Administration API
- Webhooks
- Discord Integration
- Slack Integration
- Microsoft Teams Integration
- Email Notifications

---

# 16. Coding Standards

- Java 21
- SOLID
- Clean Architecture
- Dependency Injection
- Constructor Injection only
- No Static Business Logic
- Immutable DTOs
- Record classes whenever possible
- Repository Pattern
- Service Layer
- Domain Driven Naming

---

# 17. Testing Strategy

Unit Tests

Coverage target

```
>90%
```

Integration Tests

- SQLite
- Redis
- Telegram Mock
- HTTP Mock

Load Tests

- Multiple APIs
- Large payloads
- Network failures

---

# 18. Deliverables

## Phase 1

- Project bootstrap
- Configuration
- SQLite
- Redis

---

## Phase 2

- API Client
- Retry
- Authentication

---

## Phase 3

- Processing Engine
- Cache

---

## Phase 4

- Telegram Publisher

---

## Phase 5

- Persistence

---

## Phase 6

- Tests
- Documentation
- Docker

---

## Phase 7

- Production Deployment

---

# 19. Non-Functional Requirements

- High availability
- Horizontal scalability
- Stateless application
- Graceful shutdown
- Observability
- Structured logging
- Docker-ready
- Cloud-ready
- Environment-based configuration
- Minimal external dependencies

---

# 20. Success Criteria

The project is considered complete when:

- ✅ Supports multiple third-party APIs.
- ✅ Prevents duplicate notifications using Redis.
- ✅ Persists all processed data in SQLite.
- ✅ Publishes formatted Telegram messages reliably.
- ✅ Recovers gracefully from API, Redis, or Telegram failures.
- ✅ Can onboard a new API integration with minimal configuration and no core code changes.
- ✅ Is fully documented, tested, containerized, and production-ready.