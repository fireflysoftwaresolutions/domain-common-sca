<a href="https://fireflysoftware.io/openplatform/">
<picture>
  <source media="(max-width: 640px) and (prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-narrow-dark.svg">
  <source media="(max-width: 640px)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-narrow-light.svg">
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-dark.svg">
  <img src="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-light.svg" alt="Firefly OpenPlatform - Domain Common - Strong Customer Authentication" width="100%">
</picture>
</a>

# Firefly OpenPlatform - Domain Common - Strong Customer Authentication

Developed by the Firefly Software Foundation. [dev@fireflysoftware.io](mailto:dev@fireflysoftware.io)

[Website](https://fireflysoftware.io/) · [OpenPlatform](https://fireflysoftware.io/openplatform/) · [Module catalogue](https://fireflysoftware.io/openplatform/modules/)

> Domain-layer microservice that orchestrates Strong Customer Authentication (SCA) operations — initiating challenges, verifying attempts, and maintaining a full audit trail — by composing calls to the core SCA management platform.

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Module Structure](#module-structure)
- [API Endpoints](#api-endpoints)
- [Domain Logic](#domain-logic)
- [Dependencies](#dependencies)
- [Configuration](#configuration)
- [Running Locally](#running-locally)
- [Testing](#testing)

## Overview

The additive native account-recovery candidate is default-off and self-only. Its original-producer SDK and Domain source are [hosted-qualified](docs/SOURCE_QUALIFICATION.md); installed native unlocking and production acceptance remain separate gates. See [the exact recovery contract and configuration](docs/RECOVERY_SOURCE_CONTRACT.md).

`domain-common-sca` is a reactive Spring Boot microservice that sits at the **domain layer** of the Firefly platform. Its responsibility is to provide a clean, business-oriented API for Strong Customer Authentication flows, shielding consumers from the lower-level complexity of the core SCA management system (`core-common-sca-mgmt`).

The service handles the full SCA lifecycle: a caller initiates an operation (triggering automatic challenge generation and delivery), the user submits a verification code, and the service records the attempt, updates the operation status, and writes an immutable audit event — all as a single compensatable saga. It also exposes read-only endpoints to inspect operation state, individual attempts, and the complete status-change history.

Being part of the domain tier, this service enforces business invariants (operation types, challenge delivery channels, attempt recording) and decouples the application tier from infrastructure concerns. It does not own any persistent state of its own; all data is managed by `core-common-sca-mgmt` and accessed through its generated SDK.

## Architecture

The service uses **CQRS with Saga orchestration**. Write flows are modelled as commands that are dispatched through a `CommandBus` to dedicated `CommandHandler` implementations. Read flows are modelled as queries dispatched through a `QueryBus`. Transactional write flows that span multiple downstream API calls are implemented as compensatable sagas using the Firefly Orchestration framework (`fireflyframework-starter-domain`).

```
HTTP Client
    │
    ▼
ScaController  (domain-common-sca-web)
    │  dispatches via
    ▼
ScaServiceImpl  ──── CommandBus ────► CommandHandler
(domain-common-sca-core)               │
    │                                  ├─ InitiateScaOperationHandler ──► SagaEngine ──► InitiateScaSaga
    │                                  ├─ VerifyScaAttemptHandler      ──► SagaEngine ──► VerifyScaSaga
    │                                  └─ GenerateScaChallengeHandler  ──► ScaChallengeControllerApi
    │
    └──────────── QueryBus ─────────► QueryHandler
                                       ├─ GetScaOperationHandler       ──► ScaOperationControllerApi
                                       ├─ GetScaAttemptsHandler        ──► ScaChallengeControllerApi
                                       │                                    + ScaAttemptControllerApi
                                       └─ GetScaStatusHistoryHandler   ──► ScaOperationHistoryControllerApi

All API clients are created by ScaMgmtClientFactory (domain-common-sca-infra)
and connect to core-common-sca-mgmt (default: http://localhost:8040).

Events are published to Kafka topic "domain-layer" via the Firefly EDA module.
```

## Module Structure

| Module | Purpose |
|--------|---------|
| `domain-common-sca-interfaces` | Shared DTO/request model classes and validation annotations used across `-web` and `-core` |
| `domain-common-sca-core` | Domain logic: commands, queries, command/query handlers, saga classes, and the `ScaService` interface with its default implementation |
| `domain-common-sca-infra` | Infrastructure integration: `ScaMgmtClientFactory` that instantiates the five `core-common-sca-mgmt` API clients, and `ScaMgmtProperties` for externalised connection configuration |
| `domain-common-sca-web` | Spring Boot application entry point, `ScaController` REST layer, HTTP request DTOs, and the OpenAPI/Swagger configuration |
| `domain-common-sca-sdk` | Client SDK for this service, generated from the service's own OpenAPI specification using the OpenAPI Generator Maven plugin (webclient / reactive flavour) |

## API Endpoints

All endpoints are under the base path `/api/v1/sca/operations` and produce/consume `application/json`.

| Method | Path | Description | Success Status |
|--------|------|-------------|----------------|
| `POST` | `/api/v1/sca/operations` | Initiate a new SCA operation; automatically generates and delivers the first challenge | `201 Created` |
| `GET` | `/api/v1/sca/operations/{operationId}` | Retrieve an existing SCA operation by its UUID | `200 OK` |
| `POST` | `/api/v1/sca/operations/{operationId}/challenge` | Generate or regenerate a challenge for an existing operation | `201 Created` |
| `POST` | `/api/v1/sca/operations/{operationId}/verify` | Verify a user's SCA attempt; updates operation status and records an audit event | `200 OK` |
| `GET` | `/api/v1/sca/operations/{operationId}/attempts` | Retrieve all attempts for the latest challenge on the operation (page 0, size 10) | `200 OK` |
| `GET` | `/api/v1/sca/operations/{operationId}/status-history` | Retrieve the full status-change history for the operation (page 0, size 20, ordered by `eventTime DESC`) | `200 OK` |

### Request Bodies

**`POST /api/v1/sca/operations`** — `InitiateScaOperationRequest`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `operationType` | `String` | Yes | Operation category (e.g. `LOGIN`, `TRANSFER`, `PAYMENT`) |
| `resourceId` | `UUID` | Yes | Unique identifier of the resource being protected |
| `channel` | `String` | Yes | Delivery channel (e.g. `MOBILE`, `WEB`) |

**`POST /api/v1/sca/operations/{operationId}/challenge`** — `GenerateChallengeRequest`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `challengeType` | `ChallengeType` | Yes | One of `OTP`, `PUSH`, `BIOMETRIC` |

**`POST /api/v1/sca/operations/{operationId}/verify`** — `VerifyAttemptRequest`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `challengeId` | `UUID` | Yes | Unique identifier of the challenge being answered |
| `verificationCode` | `String` | Yes | Verification code submitted by the user |

## Domain Logic

### Commands

#### `InitiateScaOperationCommand`
Carries `operationType` (String), `resourceId` (UUID), and `channel` (String). Handled by `InitiateScaOperationHandler`, which dispatches the `initiate-sca` saga with a 30-second timeout.

#### `GenerateScaChallengeCommand`
Carries `operationId` (UUID) and `challengeType` (`ChallengeType` enum). Handled directly by `GenerateScaChallengeHandler` without a saga — calls `ScaChallengeControllerApi.createChallenge` with a 15-second timeout.

#### `VerifyScaAttemptCommand`
Carries `operationId` (UUID), `challengeId` (UUID), and `verificationCode` (String). Handled by `VerifyScaAttemptHandler`, which dispatches the `verify-sca` saga with a 30-second timeout.

### Queries

| Query | Handler | Caching | Description |
|-------|---------|---------|-------------|
| `GetScaOperationQuery` | `GetScaOperationHandler` | Cacheable, TTL 30 s | Fetches a single operation from `ScaOperationControllerApi` |
| `GetScaAttemptsQuery` | `GetScaAttemptsHandler` | Not cached | Resolves the most recent challenge for the operation then fetches attempts |
| `GetScaStatusHistoryQuery` | `GetScaStatusHistoryHandler` | Cacheable, TTL 60 s | Fetches the status-change history from `ScaOperationHistoryControllerApi` |

### Saga: `initiate-sca`

Orchestrated by `InitiateScaSaga`. Executed when a new SCA operation is requested.

| Step ID | Action | Retry | Compensation |
|---------|--------|-------|--------------|
| `createScaOperation` | Calls `ScaOperationControllerApi.createOperation` to create an operation with status `PENDING`; stores result in context as `CTX_OPERATION_ID` | 3 retries, 500 ms backoff | `compensateCreateOperation`: updates operation status to `CANCELLED` |
| `generateChallenge` | Depends on `createScaOperation`; calls `ScaChallengeControllerApi.createChallenge`; stores result as `CTX_CHALLENGE_ID` | 2 retries | — |
| `sendChallenge` | Depends on `generateChallenge`; calls `ScaOperationControllerApi.triggerSCA` to deliver the challenge to the user | — | — |

If `generateChallenge` or `sendChallenge` fails, `compensateCreateOperation` is invoked to cancel the created operation, preventing orphaned pending operations.

### Saga: `verify-sca`

Orchestrated by `VerifyScaSaga`. Executed when a user submits a verification code.

| Step ID | Action | Retry | Compensation |
|---------|--------|-------|--------------|
| `validateAttempt` | Calls `ScaAttemptControllerApi.createAttempt` to record the attempt; stores result as `CTX_ATTEMPT_ID` | 2 retries, 500 ms backoff | `compensateValidateAttempt`: calls `ScaAttemptControllerApi.updateAttempt` to mark the attempt as failed |
| `updateStatus` | Depends on `validateAttempt`; updates the operation status to `VERIFIED` or `FAILED` based on `attempt.success` | — | — |
| `recordAudit` | Depends on `updateStatus`; writes an audit event of type `VERIFIED` or `FAILED` via `ScaAuditControllerApi` | — | — |

If `updateStatus` or `recordAudit` fails, `compensateValidateAttempt` is invoked to explicitly mark the recorded attempt as failed, ensuring data consistency.

## Dependencies

### Upstream (consumes)

| SDK / Artifact | Purpose |
|----------------|---------|
| `core-common-sca-mgmt-sdk` (`io.fireflysoftware`) | The primary downstream dependency. Provides five API clients instantiated by `ScaMgmtClientFactory`: `ScaOperationControllerApi` (create, update, trigger, get operations), `ScaChallengeControllerApi` (create and list challenges), `ScaAttemptControllerApi` (create, update, list attempts), `ScaOperationHistoryControllerApi` (list status-change history), and `ScaAuditControllerApi` (create audit events). |
| `fireflyframework-starter-domain` | Provides the CQRS infrastructure (`CommandBus`, `QueryBus`, `CommandHandler`, `QueryHandler`) and the Saga Orchestration engine (`SagaEngine`, `@Saga`, `@SagaStep`, `SagaResult`). |
| `fireflyframework-web` | Shared web utilities, exception handling, and common Spring WebFlux configuration for domain-layer services. |
| `fireflyframework-utils` / `fireflyframework-validators` | Cross-cutting utility classes and custom Bean Validation constraints used across modules. |

### Downstream (consumed by)

The `domain-common-sca-sdk` module packages this service's OpenAPI specification into a reactive WebClient-based SDK. Any application-layer or composite-domain service that needs to trigger SCA verification or query SCA state should depend on `domain-common-sca-sdk` rather than calling the REST API directly.

## Configuration

All properties are defined in `domain-common-sca-web/src/main/resources/application.yaml`.

| Property | Default | Description |
|----------|---------|-------------|
| `spring.application.name` | `domain-common-sca` | Service name used in logs, traces, and the service registry |
| `server.port` | `8041` (override with `SERVER_PORT`) | HTTP port the service listens on |
| `server.address` | `localhost` (override with `SERVER_ADDRESS`) | Network interface the server binds to |
| `server.shutdown` | `graceful` | Enables graceful shutdown on SIGTERM |
| `spring.threads.virtual.enabled` | `true` | Enables Java virtual threads for improved throughput |
| `api-configuration.core-platform.sca-mgmt.base-path` | `http://localhost:8040` | Base URL for the `core-common-sca-mgmt` service |
| `firefly.cqrs.enabled` | `true` | Activates the CQRS command and query bus infrastructure |
| `firefly.cqrs.command.timeout` | `30s` | Global command handler timeout |
| `firefly.cqrs.command.metrics-enabled` | `true` | Enables Micrometer metrics on command handling |
| `firefly.cqrs.command.tracing-enabled` | `true` | Enables distributed tracing on command handling |
| `firefly.cqrs.query.timeout` | `15s` | Global query handler timeout |
| `firefly.cqrs.query.caching-enabled` | `true` | Enables response caching for query handlers |
| `firefly.cqrs.query.cache-ttl` | `15m` | Default cache TTL for query results |
| `firefly.saga.performance.enabled` | `true` | Enables saga performance metrics collection |
| `firefly.eda.enabled` | `true` | Enables the Event-Driven Architecture (EDA) module |
| `firefly.eda.default-publisher-type` | `KAFKA` | Default event publisher backend |
| `firefly.eda.publishers.kafka.default.default-topic` | `domain-layer` | Kafka topic for domain events |
| `firefly.eda.publishers.kafka.default.bootstrap-servers` | `localhost:9092` | Kafka broker address |
| `firefly.stepevents.enabled` | `true` | Enables saga step-level event emission |
| `springdoc.api-docs.path` | `/v3/api-docs` | Path for the OpenAPI JSON/YAML spec |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | Path for the Swagger UI |
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | Actuator endpoints exposed over HTTP |

### Profile overrides

| Profile | Logging behaviour | API docs |
|---------|-------------------|----------|
| `dev` | `io.fireflysoftware` at `DEBUG`, root at `INFO` | Enabled |
| `testing` | `io.fireflysoftware` at `DEBUG`, root at `INFO` | Enabled |
| `prod` | `io.fireflysoftware` at `INFO`, root and Spring at `WARN` | Disabled |

## Running Locally

```bash
# Prerequisites: Java 25, Maven 3.9+, a running core-common-sca-mgmt instance on port 8040,
# and a Kafka broker on localhost:9092 (or update application.yaml accordingly).

# Build all modules
mvn clean install -DskipTests

# Start the service
mvn spring-boot:run -pl domain-common-sca-web
```

Server port: **8041**

Once started, the Swagger UI is available at `http://localhost:8041/swagger-ui.html` and the OpenAPI spec at `http://localhost:8041/v3/api-docs`.

To point at a different `core-common-sca-mgmt` instance, set the environment variable or pass a property override:

```bash
SERVER_PORT=9041 mvn spring-boot:run -pl domain-common-sca-web \
  -Dspring-boot.run.arguments="--api-configuration.core-platform.sca-mgmt.base-path=http://sca-mgmt-host:8040"
```

## Testing

```bash
mvn clean verify
```

The test suite is structured in two layers:

**Unit tests (`domain-common-sca-web`)** — `ScaControllerTest` uses `WebTestClient` bound directly to the controller with a mocked `ScaService`. It covers all six endpoints for both success and error propagation paths, verifying HTTP status codes and that the correct `ScaService` method is called with the correct arguments. Tests use JUnit 5 and Mockito.

**Integration tests (`domain-common-sca-core`)** — Each command handler, query handler, and saga has a dedicated test class. Saga tests (`InitiateScaSagaTest`, `VerifyScaSagaTest`) boot a minimal Spring context (`TestScaCoreApplication`) with `@MockBean` replacements for all five `core-common-sca-mgmt` API clients, then execute sagas through the real `SagaEngine` using `StepVerifier` from Project Reactor's test library. These tests verify both the happy path (all steps succeed, `SagaResult.isSuccess()` is true, all step IDs are present) and the compensation path (a step fails, `SagaResult.isFailed()` is true, the compensated step ID is reported). Handler tests (`GenerateScaChallengeHandlerTest`, `GetScaOperationHandlerTest`, `GetScaAttemptsHandlerTest`, `GetScaStatusHistoryHandlerTest`, `InitiateScaOperationHandlerTest`, `VerifyScaAttemptHandlerTest`) follow the same pattern with mocked API clients and `StepVerifier` assertions.
