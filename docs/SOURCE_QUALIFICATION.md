# Native recovery Domain and original-producer SDK source qualification

The current `Impl` naming revision requires a fresh hosted qualification against its newly pinned Core source. The receipt below applies only to the earlier, pre-naming code; it does not qualify the renamed implementations.

[Draft PR 1](https://github.com/fireflysoftwaresolutions/domain-common-sca/pull/1), code head `d32ecca07e3dfd3a2330c201e555e95b89476c89`, passed [hosted run 41252856215](https://github.com/fireflysoftwaresolutions/domain-common-sca/actions/runs/41252856215). GitHub executed its PR merge checkout `afb3c681032520db97a368016b12a62adb5cf0c8`. These are different source references: the first is the proposed branch code, the second is the actual tested merge source.

The complete genuine Maven Domain reactor `verify` succeeded, including the original Domain OpenAPI producer and SDK generator. The bounded [source summary artifact 13061229916](https://github.com/fireflysoftwaresolutions/domain-common-sca/actions/runs/41252856215/artifacts/13061229916) records all **23 required recovery, policy, native HTTP transport, scoped bearer, generated SDK and original-producer tests passing with zero failures, errors or skips**. Two separate Python fixture-isolation tests also passed. Source compilation and tests ran only on the hosted runner; no local Java/Maven/dependency/container execution was performed under the capacity floor.

## Exact source and generated artifact

| Item | Qualified value |
| --- | --- |
| Core producer | `c91880ac092eec11ec4640a65a599a8a1d9d5c2a` |
| Corrected source parent | `6442bd51733ade4d5f4583e1afe812dd2c520b40` |
| Genuine Jafly parent/BOM train | `26.10.23` |
| Candidate SDK GAV | `io.fireflysoftware:core-common-sca-mgmt-sdk:26.10.6-recovery-source-c91880ac092e-SNAPSHOT` |
| Candidate SDK JAR SHA-256 | `e1bab8b0a8ca723298d8c2915c763b964e1f05884df87259e442142265f47b1c` |
| Flattened candidate descriptor SHA-256 | `69d7b01b389ee4baed4105e6c3007c097af60f20cee6922c11860d845c69e7a0` |
| Original-producer specification SHA-256 | `16b4f6654d46b1951e33ec751cc62877b5ea9e94e6923bf714919af33c7f78cc` |

The fixture used source-parent relative paths only, generated the Core specification from its real application and compiled the actual SDK with the original generator. It installed only the distinct candidate SDK and its flattened effective descriptor into the owned temporary Maven repository. The modified source parent, service aggregators and released GAVs were never installed or deployed. Candidate hashes identify this particular hosted build; generated annotations/build metadata can change binary hashes on another run.

The generated `ScaRecoveryApi` contains the original five operations plus `reserveRecoveryVerification`, `completeRecoveryVerification` and `verifyRecoveryAuthorization`. Their signatures have no generic HTTP idempotency header. Generated category/outcome enums are nested in their request/response models; they are not substitutes copied from Core source enums. The original generator's `LocalDateTime` mapping was verified, and the dedicated recovery transport's actual codec tests prove explicit UTC/RFC3339 serialization and offset normalization. Legacy SDK clients retain their original codec behavior.

## Limits

This is original-producer/generated-artifact/Domain **source qualification**. It does not publish a Core or Domain SDK, adopt a newly published parent, qualify an installed native provider, establish customer/workforce factor enrollment, prove an actual unlock or deployed audit sink, or establish production/regulatory acceptance. HTTP transport doubles and policy unit fixtures are not native proof. Full native TLS/workload/Core/Domain/browser execution and response-loss reconciliation remain independent gates.

The existing legacy handler tests had mixed Mockito matchers, and legacy saga unit contexts implicitly required the framework's PostgreSQL cache without a provider. Their test-only matcher setup and explicit local Caffeine selection were corrected. No production cache fallback or authentication-disable switch was added. The original production PostgreSQL cache default remains unchanged.

See [the recovery source contract](RECOVERY_SOURCE_CONTRACT.md) for exact routes, independent admission, optional native self policy, secret-file configuration and hosted fixture reuse. `--candidate-only` stops after genuine candidate SDK installation and deliberately does not claim Domain test execution; the complete successful run above did not use that option.
