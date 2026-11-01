# Native recovery Domain and original-producer SDK source qualification

[Draft PR 1](https://github.com/fireflysoftwaresolutions/domain-common-sca/pull/1), renamed implementation code head `3cf19a0d2a6baa1e13ad92dc7014b91490996ac4`, passed [fresh hosted run 41268887485](https://github.com/fireflysoftwaresolutions/domain-common-sca/actions/runs/41268887485). GitHub executed PR merge checkout `2d66d92f6380e10cf8fbaecbada0c131c68efebc`, whose parents are current main `741cfd7dd9ec0459e29ecac054d97b4c639139f6` and the proposed code head. The branch head and actual tested merge source are distinct references.

This revision names nineteen concrete Domain services, adapters, HTTP/PDP/relay implementations, CQRS handlers and sagas with `Impl`. Constructors, imports, DI and test references follow those names. Conventional interfaces, DTOs, records, controllers, configurations, tests, protocol types and static utilities remain; explicit bean factory names, public schemas/actions and generated SDK sources are unchanged. Reversing the implementation identifiers reconstructs all 41 changed Java source files exactly.

The complete genuine Maven Domain reactor `verify` succeeded, including the original Domain OpenAPI producer and SDK generator. The bounded [fresh source summary artifact 13068264565](https://github.com/fireflysoftwaresolutions/domain-common-sca/actions/runs/41268887485/artifacts/13068264565) records all **23 required recovery, policy, native HTTP transport, scoped bearer, generated SDK and original-producer tests passing with zero failures, errors or skips**. Two separate Python fixture-isolation tests also passed. Source compilation and tests ran only on the hosted runner; no local Java/Maven/dependency/container execution was performed under the capacity floor.

## Exact source and generated artifact

| Item | Qualified value |
| --- | --- |
| Core producer | `cc826d27cd05dac831989ec872d78a8c17e88e8b` |
| Corrected source parent | `6442bd51733ade4d5f4583e1afe812dd2c520b40` |
| Genuine Jafly parent/BOM train | `26.10.23` |
| Candidate SDK GAV | `io.fireflysoftware:core-common-sca-mgmt-sdk:26.10.6-recovery-source-cc826d27cd05-SNAPSHOT` |
| Candidate SDK JAR SHA-256 | `178afe22b298ab08fd61873c6c772b3c7589da2094c7559fcaabc974c04f0eaf` |
| Flattened candidate descriptor SHA-256 | `d53c3f5d6d889a22178019f76000f689f1cda07a14dc0785c47ccac618e1aebe` |
| Original-producer specification SHA-256 | `16b4f6654d46b1951e33ec751cc62877b5ea9e94e6923bf714919af33c7f78cc` |

The canonical Core producer specification hash is identical to the pre-naming run, confirming that its eight-operation wire contract did not change. The fixture used source-parent relative paths only, generated the Core specification from its real application and compiled the actual SDK with the original generator. It installed only the distinct candidate SDK and its flattened effective descriptor into the owned temporary Maven repository. The modified source parent, service aggregators and released GAVs were never installed or deployed. Candidate hashes identify this particular hosted build; generated annotations/build metadata can change binary hashes on another run.

The generated `ScaRecoveryApi` contains the original five operations plus `reserveRecoveryVerification`, `completeRecoveryVerification` and `verifyRecoveryAuthorization`. Their signatures have no generic HTTP idempotency header. Generated category/outcome enums are nested in their request/response models; they are not substitutes copied from Core source enums. The original generator's `LocalDateTime` mapping was verified, and the dedicated recovery transport's actual codec tests prove explicit UTC/RFC3339 serialization and offset normalization. Legacy SDK clients retain their original codec behavior.

## Limits

This is original-producer/generated-artifact/Domain **source qualification**. It does not publish a Core or Domain SDK, adopt a newly published parent, qualify an installed native provider, establish customer/workforce factor enrollment, prove an actual unlock or deployed audit sink, or establish production/regulatory acceptance. HTTP transport doubles and policy unit fixtures are not native proof. Full native TLS/workload/Core/Domain/browser execution and response-loss reconciliation remain independent gates.

The existing legacy handler tests had mixed Mockito matchers, and legacy saga unit contexts implicitly required the framework's PostgreSQL cache without a provider. Their test-only matcher setup and explicit local Caffeine selection were corrected. No production cache fallback or authentication-disable switch was added. The original production PostgreSQL cache default remains unchanged.

See [the recovery source contract](RECOVERY_SOURCE_CONTRACT.md) for exact routes, independent admission, optional native self policy, secret-file configuration and hosted fixture reuse. `--candidate-only` stops after genuine candidate SDK installation and deliberately does not claim Domain test execution; the complete successful run above did not use that option.

## Historical pre-naming receipt

Earlier code `d32ecca07e3dfd3a2330c201e555e95b89476c89` passed [run 41252856215](https://github.com/fireflysoftwaresolutions/domain-common-sca/actions/runs/41252856215) through merge `afb3c681032520db97a368016b12a62adb5cf0c8`, with [artifact 13061229916](https://github.com/fireflysoftwaresolutions/domain-common-sca/actions/runs/41252856215/artifacts/13061229916), Core source `c91880ac092eec11ec4640a65a599a8a1d9d5c2a` and the same source-parent/Jafly train. That receipt applies only to the earlier code. It does **not** qualify the renamed implementations; the fresh receipt above does. Neither receipt establishes installed native recovery or production acceptance.
