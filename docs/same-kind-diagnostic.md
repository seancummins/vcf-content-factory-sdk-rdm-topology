# Same-kind read-only diagnostic

This development candidate retains `vcfcf_rdm_topology_poc` and all existing
mapping/LUN keys. `RdmTopologyDispatcher` implements the actual SDK interface
directly. Its fixture route lazily delegates to unchanged
`RdmTopologyPocAdapter`; the diagnostic route never constructs or calls that
delegate or its publishing SDK base. Diagnostic collect/discover results are
empty real SDK payloads. Only sanitized adapter status and counts are returned.

## Configuration

All descriptor fields are ordinary nonunique optional strings with **no
defaults**. Runtime validation is conditional, not optional:

- Existing accounts: retain exact `fixture_json`. Omitted mode and exact
  `execution_mode=fixture` remain supported. SDK-expanded exact empty mode is
  also supported only after full fixture validation, with no associated target
  credential object or UUID reference. Populated diagnostic settings or target
  credential objects are rejected. Empty/null known optional diagnostic fields
  are accepted; duplicate/unique/unknown fields are rejected even if blank.
  Invalid fixture JSON fails closed. Existing absent/exact-fixture UUID-only
  acceptance remains unchanged.
- New diagnostic account: `execution_mode=read_only_diagnostic`, no populated
  `fixture_json`, all ten settings below, and associated
  `rdm_vcenter_readonly` credential (username plus secret password).
- Raw null, whitespace, case variants, unknown or duplicate `execution_mode`
  are rejected. Only exact empty receives the validated fixture exception;
  no input is normalized or rewritten. Actual UI serialization and the failing
  collector callback remain controlled-lab evidence. Diagnostic mode always
  requires the exact explicit value; it is never selected by a default.

| Setting | Required diagnostic value |
|---|---|
| `vcenter_endpoint` | Strict HTTPS URL ending `/sdk` |
| `trust_mode` | `jvm` or `enterprise_ca` |
| `discovery_scope` | `single_vm_current_host` |
| `ops_endpoint` | Certificate-valid FQDN HTTPS URL ending `/suite-api` |
| `ops_trust_mode` | `jvm` or `enterprise_ca` |
| `target_vcenter_uuid` | Exact private vCenter instance UUID |
| `target_vm_moid` | Exact private scoped VM MOID |
| `target_disk_key` | Exact positive virtual-disk key |
| `diagnostic_enabled` | Initially `false`; literal booleans only |
| `authorize_once` | Initially `false`; literal booleans only |

Do not paste real values into Git, logs, terminal arguments or review documents.
Operations authentication uses only the SDK-injected per-instance identity;
the vCenter credential never substitutes for it. Missing decryption, identity,
permissions or routing fail closed. No node-local files/environment fallback.

`jvm` uses a private context with legitimate JVM trust roots and accepts no
additional certificate material. `enterprise_ca` requires actual CA PEMs in
SDK `ResourceConfig.getTrustedCertificates()` with strict validity/hostname
flags; opaque certificate IDs or leaf-only trust are rejected. Both contexts
read the same instance certificate collection: when material is supplied both
endpoints must use `enterprise_ca`, containing the required trusted roots.
Descriptor loading proves neither supported UI CA upload nor propagation to
all collectors. Qualify a supported platform delivery path before execution;
do not add CA filenames, disable TLS or install roots manually per node.

## Lifecycle and upgrade safety

Mode and SDK instance ID/UUID are latched per object; in-place owner/mode
changes and invalid refreshes retire the old route without fallback. Valid
same-owner refreshed carriers retain exact SDK objects/credential flags.
Fixture callbacks are serialized and fenced before/after dispatch; stop waits
for the finite fixture callback rather than claiming preemptive interruption.
Diagnostic stop/discard cancels its worker. Authorization is one-shot per Java
object, not durable across failover/restart. Configuration-only Test never
arms/configures the active route, decrypts, connects or proves discovery.
It now rejects invalid unsaved fixtures unlike the old null-tester callback.

The union descriptor retains `isCredentialRequired="false"`; old fixture
accounts require no credentials. Optional association/UI saving and unchanged
fixture collection after a shared-kind upgrade must still be verified live.
A PAK upgrade replaces code/metadata for **every** same-kind instance; it is
not nondisruptive merely because identity is preserved.

## Conditional operator procedure — not authorized now

1. Retain exact build-9 PAK/checksum, recoverable private fixture configuration,
   existing instance identities, topology/resource counts, health and collector
   placement. Obtain separate upgrade and single read-only-attempt approval.
2. Pause fixture collection for the approved shared-kind upgrade. Install only
   a reviewed, checksum-verified development candidate using the supported UI;
   do not uninstall first. Verify actual installed version and unchanged fixture
   accounts, collection, resource keys and additive topology.
3. Create a **new same-kind** account with explicit diagnostic mode, dedicated
   read-only vCenter credential, the ten settings, both flags false and qualified
   managed trust. Verify actual injected Operations identity/RBAC and collector
   reachability; no inherited admin or guessed local endpoint.
4. On explicit one-attempt authorization set both flags true. Permit one
   collection; record placement/time and sanitized `PASS`, `GRAPH`,
   `READ_ONLY_GRAPH_QUALIFIED`, counts **1/1/1**. Validate Connection and
   Collecting/Data Receiving alone do not prove discovery.
5. Repeat on the same object to confirm no new API run/publication. Verify no
   new resources, relationships, metrics, properties or events from diagnostic
   execution. Disarm both flags and stop before restart/failover.

Stop on any publication, fixture regression, changed placement, ambiguous or
partial graph, TLS/auth/RBAC/routing error, unqualified trust or unexpected UI
serialization. Do not weaken matching, trust or privileges to pass.

Cleanup: stop/disarm only the diagnostic instance. Separately approved removal
may delete only that instance and its dedicated unshared credential after
reference checks; never uninstall the shared pack, delete VMware/mapping/LUN
objects or injected/shared credentials. Unexpected upgrade regression requires
stopping affected collection and a supported, explicitly approved recovery;
do not assume a build-9 downgrade is safe. Verify counts/topology after async
reconciliation.

## Offline qualification boundaries

Historical E4.9 qualification: `scripts/run-dispatcher-tests.sh` used the supplied SDK and seven genuine
external platform libraries, no logging/platform shims. It validates real SDK
configuration/payloads, actual combined descriptor loading and bare facade
`describe()` using a synthetic SDK `ADAPTER_HOME` layout. Delegate-seam tests
do not claim full platform fixture lifecycle execution.

At that checkpoint, optional `--fixture-lifecycle` attempted actual unchanged fixture construction;
with the seven loader libraries it reported missing platform
`com.vmware.vrops.secure.connection.ConnectionCategory` (exit 2). That is an
existing SDK `CustomTrustManager` dependency, not bundled or substituted here.
Old fixture callback tests retain their explicitly limited constructor-bypass
approach; neither result proves collector/UI/upgrade behavior.

E4.9.1/E4.9.2 qualification added the verified, external
`vrops-trustmanager-3.0-SNAPSHOT.jar` to the eight-library test classpath,
resolving that blocker without a shim. E4.9.2 passes **348 dispatcher assertions**
and **948 actual SDK fixture lifecycle assertions**, including original and
dispatcher no-argument and collector-constructor routes. The dispatcher now
exposes the exact public `(String, Integer)` constructor required by collector
reflection and lazily forwards both arguments to the original fixture adapter.
Diagnostic mode still constructs no fixture adapter and emits no publication
payloads. Offline qualification does not establish real collector upgrade,
credential injection, UI serialization or networking behavior. Any controlled
upgrade retry or diagnostic execution requires separate explicit authorization;
follow the conditional operator procedure above without changing the existing
fixture account's mode or identity.

No runtime dependencies are downloaded. SDK/platform JARs stay external to Git
and PAK inputs. A local candidate is not installation or persistence proof.

## Historical E4.9 development candidate

`vcfcf_sdk_rdm_topology_poc.0.0.0.10.pak`, SHA-256
`df136333f9ea8ec4ad24b3da6cff489fa4825d49cc02854003327fa909b6314a`.
Local build/structural gate: **0 BLOCKING, 4 WARNING, 21 INFO**, exit 0,
against the approved Synology 1.0.0.30 artifact (SHA-256
`beaa8555ab3d8648fcb7dda6c26e2e4a295f0f2f854db467468060dd048480a1`).
Warnings remain credential-field count, instance-identifier count and two
traversal-structure differences; none is suppressed or waived. Outer/inner/JAR
integrity and same-kind entrypoint inspected; only the entry JAR plus factory
base JAR are packaged, no SDK/Log4j/Commons platform classes. Exact build-9 PAK
remains unchanged. Candidate packaging is not a release or installation approval.

## E4.9.2 development candidate

`vcfcf_sdk_rdm_topology_poc.0.0.0.11.pak`, SHA-256
`250d58894c624178863779f44d9fcea5a89c9f5e9774fa4b102251f0313392ce`.
The constructor correction preserves the same adapter kind, descriptor,
resource keys and additive relationships. Explicit structural comparison against
the same approved reference reports **0 BLOCKING, 4 WARNING, 21 INFO**, exit 0;
the findings are not suppressed. This local candidate is not installation
authorization. The existing build-9 and build-10 artifacts remain unchanged.

## E4.9.3 build-12 partial-update compatibility

Build 12 corrects a reproduced SDK contract regression: after valid full fixture
configuration, an instance-less `AdapterConfig` update can refresh monitoring
and policy state while retaining the cached instance configuration. The dispatcher
now forwards such matching-owner updates unchanged to the original SDK adapter,
keeping its validated full-source guard. Cold, stopped, retired, diagnostic,
conflicting-owner and credential/custom/extended-configuration updates remain
closed. Optional-mode acceptance is unchanged.

The shim-free runner passes **348 existing dispatcher**, **336 new carrier and
fixed-reason**, and **2324 actual fixture lifecycle assertions**. Both public
constructors, descriptor, resource identities and additive topology remain
unchanged; diagnostic publication stays empty. The supplied account-15903
failure does not identify its callback shape, so restored live collection and
its precise live cause remain unconfirmed. See
[the E4.9.3 evidence and controlled retry](configuration-compatibility-e493.md)
for fixed rejection diagnostics and the minimum sanitized evidence to obtain
only under separate live-operation authorization.

## E4.9.3 build-13 cold-start compatibility

Genuine SDK schema synchronization can expand a legacy fixture-only key into
twelve nonunique identifiers, with exact empty `execution_mode`. Preserved
packaged build 9 accepts that carrier; builds 11/12 reject it. Build 13 adds
only the fully validated exact-empty fixture exception described above and
retains the build-12 partial-update correction. The actual collector's use of
schema synchronization before the failing callback is unproven; this is a
reproduced SDK-supported compatibility fix, not live recovery evidence.

The final genuine-SDK runner passes **343 dispatcher**, **325 partial/rejection**,
**392 cold-start**, and **3486 actual fixture lifecycle assertions**, covering
six scenarios and four constructor/delegate routes. The associated target
credential UUID guard applies to the new empty form; absent/exact-fixture
UUID-only acceptance is preserved. Strict diagnostic selection/publication
and constructor/descriptor/resource identities remain unchanged. See the
[E4.9.3 evidence and controlled retry](configuration-compatibility-e493.md)
for tested boundaries, the offline SDK logging limitation and smallest missing
sanitized callback evidence. Separate live-operation approval remains required.
