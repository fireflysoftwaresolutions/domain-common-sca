# Restricted native recovery orchestration — source candidate

Recovery is off by default. This candidate introduces a self-only server-to-server Domain receiver and orchestration through the genuine Core producer-generated `ScaRecoveryApi`. It owns no credentials, challenge store, attempts ledger, authorization store or native identity mutation. Existing legacy Java APIs remain, but the runtime security boundary denies all legacy routes.

The released Core SDK 26.10.6 does not contain the additive API. Ordinary released dependency adoption remains blocked. The hosted source fixture generates the actual Core specification and SDK, installs only a distinct candidate SDK in an isolated Maven repository, then compiles/tests this Domain source. This does not publish a parent, service or SDK or establish native unlock, installed provider, regulatory or production acceptance.

## Independent receiver and owner checks

Enable only with the `sca-recovery-receiver` profile, `SCA_RECOVERY_ENABLED=true`, explicit HTTPS issuer/JWKS, exactly the approved recovery audiences, one supported population/realm, exact ceremony-client allowlist, strict current PDP and a qualified durable audit sink/native admission implementation. There are no default issuer, workload credential, permit policy or production test-disable flags. Customers and workforce require separate deployment profiles. Master/institution/machine/delegated impersonation is unsupported.

The genuine managed Jafly 26.10.23 resource server validates signatures/issuer/audience/time and projects the principal. Domain additionally requires exact signed `client_id`, opaque issuer/subject/tenant, canonical population-specific `party_id` or `staff_actor_id` (never both), `recovery_only=true`, `recovery_purpose=PERMANENT_AUTHENTICATION_UNLOCK`, `recovery_ceremony_id`, `recovery_lock_generation`, `recovery_realm`, `jti`, typed `iat`/`exp`, optional typed `nbf`, and only the `firefly.recovery` scope. Audience must equal the configured two-service recovery set, with no business audience, ordinary session or role authority. TTL is at most 300 seconds; workforce at most 180.

Every Core hop refreshes native current owner/link/generation/ceremony admission and current PDP using the actual action/resource. Denial, indeterminate, empty result, errors, unsupported obligations, expiry and unavailable audit deny. Native context must exactly match the signed self actor. No body/header/role grants authority. Core independently verifies its own caller/current policy/native admission.

Jafly's actual `FireflyAuthenticationToken` erases credentials. The receiver delegates its real converter, then narrowly wraps the verified result in `RecoveryAuthenticationToken` retaining the decoder-validated JWT only in reactive request scope. A dedicated Core SDK `WebClient` forwards that request's bearer only to the configured HTTPS Core origin/recovery routes, rejects caller Authorization overrides, and does not follow redirects. It never mutates singleton bearer state. Credentials/tokens are absent from business DTOs and logs.

The optional owning-service `NativeSelfRecoveryPolicyImpl` is separately default-off (`SCA_RECOVERY_NATIVE_SELF_SERVICE_POLICY_ENABLED=true` enables it only alongside actual native HTTP recovery). It permits exactly the seven recovery actions for the approved purpose, revalidates the restricted self profile and exact server-derived context, and independently refreshes the actual native admission for that phase/operation. All other resources/actions/profiles and unavailable current state deny. It is product business policy for native self-unlock, not a replacement bank-wide PDP or BPO/financial authorization. The generic managed framework remains unchanged.

## Frozen Domain gateway

All paths below use `POST /api/v1/sca/recovery-operations` and bearer authentication. All endpoints opt out of generic HTTP response idempotency caching. Durable Core body keys are server-derived from the signed ceremony, and Core's generation-bound budgets remain authoritative.

| Suffix | Request JSON | Success |
| --- | --- | --- |
| root | No body | 201 `{operationId, knowledgeChallengeId, possessionChallengeId}` |
| `/{operationId}/verifications/reservations` | `{challengeId}` | 201 canonical `{operationId,challengeId,reservationId,providerChallengeId,category,expiresAt}` |
| `/{operationId}/verifications/complete` | `{challengeId,reservationId,attestation}` | 202 `{operationId,status:"ACKNOWLEDGED"}` |
| `/{operationId}/authorizations` | No body | 200 `{operationId,executionId,binding,expiresAt}` |
| `/{operationId}/authorizations/verify` | `{executionId,expiresAt}` | 200 same canonical authorization |
| `/{operationId}/executions` | `{executionId,expiresAt,outcome}` | 204 after authoritative native journal verification and Core recording |

Binding is server-derived output only. Raw passwords, PINs, TOTP codes, reusable secrets, bearer tokens and caller-provided factor verdicts are not request fields. Opaque attestation references come only from the native credential boundary. Verification acknowledgement discloses no factor acceptance or remaining budget.

Begin creates the operation and both categories through actual Core SDK calls. Reserve waits for the Core response; the native browser backend then verifies its still-ephemeral credential only after that committed reservation. Complete forwards the opaque attestation. Consumption never executes or enables a native user. The native provider performs online authorization verification and its own atomic journaled transition. Domain recording reads the authoritative native journal and rejects a caller outcome/execution mismatch; it never consumes again or invents a replacement execution after timeout. Expired/unknown reconciliation still requires valid current restricted caller/PDP and qualified native/Core reconciliation semantics.

## Optional actual native HTTP adapters

`NativeRecoveryPort` and `RecoveryAuditPort` remain product-owned ports. Their defaults are unqualified and refuse. Optional `native-http.enabled=true` selects real HTTPS RPC transport, requires provider profile `firefly-keycloak-native-v1`, and still checks actual admission/committed audit responses. This is a protocol implementation, not evidence of accepted deployment.

| Environment | Required value |
| --- | --- |
| `SCA_RECOVERY_TRUSTED_ISSUER`, `SCA_RECOVERY_TRUSTED_JWKS_URI` | Explicit HTTPS trust |
| `SCA_RECOVERY_REALM`, `SCA_RECOVERY_POPULATION` | One customer/HUMAN or workforce/STAFF profile |
| `SCA_RECOVERY_AUDIENCES` | Exact approved Domain/Core recovery audience set |
| `SCA_RECOVERY_ALLOWED_CLIENTS` | Exact native ceremony clients |
| `SCA_RECOVERY_MAX_LIFETIME_SECONDS` | Approved bound, no more than 300/180 |
| `SCA_RECOVERY_CORE_ORIGIN` | HTTPS origin only, no path/query/userinfo |
| `SCA_RECOVERY_NATIVE_HTTP_ENABLED` | Explicit `true` only for the installed owner protocol |
| `SCA_RECOVERY_NATIVE_RPC_URI` | Exactly `<issuer>/firefly-recovery/rpc` |
| `SCA_RECOVERY_NATIVE_WORKLOAD_USERNAME` | Distinct Domain workload identity |
| `SCA_RECOVERY_NATIVE_WORKLOAD_PASSWORD_FILE` | Absolute regular non-symlink, owner-only secret file, at least 32 characters |
| `SCA_RECOVERY_NATIVE_TIMEOUT_SECONDS` | 1–10 seconds |
| `SCA_RECOVERY_NATIVE_PROVIDER_PROFILE` | `firefly-keycloak-native-v1` |

RPC uses configured workload Basic authentication over verified TLS and a private JSON `actorAssertion` copied only from the validated request JWT. No unsigned actor header is accepted. `/admit` receives signed-derived self binding comparison expectations, operation ID and phase-specific `DOMAIN_CREATE`, `DOMAIN_ISSUE_CHALLENGE`, `DOMAIN_RESERVE_VERIFICATION`, `DOMAIN_COMPLETE_VERIFICATION`, `DOMAIN_CONSUME`, `DOMAIN_VERIFY_AUTHORIZATION` or `DOMAIN_RECORD_EXECUTION`. Native returns binding, nonblank owner/link/lock attestation references, validUntil and the same action. Only record may use an existing exact journal after unlock. `/journal` verifies the same execution and binding. No generic Admin REST enable call exists.

`/audit` is a native security-audit collector, not new Domain business persistence. It receives only nullable validated actor assertion and `DOMAIN_BOUNDARY_ADMITTED`/`DOMAIN_BOUNDARY_REFUSED`; target/operation IDs, request bodies, proofs and raw errors are absent. Admission waits for nonempty native `AuditReceipt(auditId,committedAt)` following commit. Logging/no-op collectors do not qualify. Anonymous cryptographic/shape refusals also require safe durable audit. Audit unavailability returns safe 503 and cannot admit work.

## Hosted source qualification

The `recovery-sdk-domain-source.yml` workflow pins exact Core and corrected parent6442 source. `tooling/prepare_recovery_sdk_fixture.py` verifies one parent/BOM Jafly23 train, overlays only source-parent relative paths, gives the ephemeral Core SDK a distinct candidate version, retains original provided/transitive Core sibling versions for reactor resolution, and uses the original producer/generator. It verifies all eight actual success statuses/bearer operations and absence of the generic cache header. A real flattened effective candidate SDK descriptor removes released-parent inheritance before isolated install. No modified released GAV is installed or deployed.

The original generator keeps `java8-localdatetime`; a recovery-only transport codec explicitly normalizes its Core Instant fields to UTC and serializes RFC3339 with an offset. It does not change legacy SDK clients. The Domain ephemeral dependency-management overlay selects that candidate and runs the real reactor. Tests exercise actual generated signatures/date types/codecs, scoped concurrent bearer isolation, current admission denials, unavailable capabilities/audit, journal mismatch, default-off routing and original Domain OpenAPI. Unit transport doubles are labeled and do not qualify a native provider, durable production sink or unlock. The bounded summary preserves source/spec/JAR/descriptor hashes and required report counts; credentials, tokens, request bodies, Maven repository and raw logs are not uploaded.

The existing legacy saga tests explicitly select the framework's local Caffeine provider for their unit context; this neither changes the runtime PostgreSQL default nor qualifies strict cache or native recovery state. Recovery authority remains in current native admission and Core persistence.

Local Java/Maven/native/database/container execution is forbidden under the resource floor. Lightweight fixture-script tests and whitespace/XML checks are separate from hosted qualification. Native browser/Core persistence/Domain RPC closure, TLS/workload registration, customer/workforce enrollment, post-unlock journal reconciliation and production acceptance remain independent required gates.
