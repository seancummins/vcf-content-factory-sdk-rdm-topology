# E4.6 read-only runtime diagnostic (unregistered candidate)

This composes the native Operations provider and target-scoped vCenter SOAP
engine into an immutable in-memory graph. It does **not** activate automatic
collection or publish resources, metrics, properties, events or relationships.
The installed build-9 adapter, active descriptor, fixture mode and identities
remain unchanged. No new PAK is built or installed by E4.6.

## SDK execution boundary

`diagnostic.ReadOnlyDiagnosticAdapter` implements the public legacy SDK
`AdapterInterface3` directly. It deliberately does not extend `AdapterBase` or
the factory base: those automatically append system properties, adapter metrics
and sometimes certificate events. Every diagnostic `collect()` returns an empty
SDK `CollectResult` with sanitized adapter status only. `discover()` is empty;
`checkCertificate()` fetches/accepts nothing and returns `NOT_SUPPORTED`.

`configure()` reads configuration and credential **metadata**, never passwords
or network data. `test(TestParam)` validates the supplied configuration without
reconfiguring/arming the runtime and returns a fixed configuration-only result:
**not proof of target connectivity, discovery or injected authentication**.
Actual UI/lifecycle acceptance remains untested. Bare `describe()` reads only
the candidate classpath descriptor, not injected state. Its SDK loader requires
platform Commons Configuration classes absent from the supplied SDK-only test
classpath; it fails with a static sanitized error there. No dependency is
fabricated or downloaded to make that offline load appear successful.

The candidate descriptor and localization file are in `docs/diagnostic/`, not
the active POC build inputs. They declare only the diagnostic instance kind and
vCenter credential kind, no VM/mapping/LUN resource kinds or topology. Future
separately authorized diagnostic packaging must register the **distinct** kind
`vcfcf_rdm_discovery_diagnostic`, entry point
`com.vcfcf.adapters.rdmtopologypoc.diagnostic.ReadOnlyDiagnosticAdapter`, put
`diagnostic-describe.xml` at its JAR classpath root and supply its localization.
Do not replace the installed POC entry point/descriptor or reuse its instance.
Full platform descriptor loading and packaging are still prerequisites, not an
installation-readiness claim.

## Minimum configuration (future separate diagnostic instance only)

All ordinary fields are persisted nonunique adapter-instance identifiers, not
credentials or resource identities. Values below are synthetic placeholders.

| Field | Required value/contract |
|---|---|
| `vcenter_endpoint` | `https://vc.example.invalid/sdk` |
| `ops_endpoint` | `https://ops.example.invalid/suite-api`; explicit certificate-valid FQDN, never guessed localhost |
| `trust_mode` | `jvm` or `enterprise_ca`, vCenter trust policy |
| `ops_trust_mode` | `jvm` or `enterprise_ca`, independent Operations trust policy |
| `discovery_scope` | Exactly `single_vm_current_host` |
| `target_vcenter_uuid` | Exact expected vCenter `about.instanceUuid`, nonzero UUID |
| `target_vm_moid` | Exact VM MOR value, interpreted only with expected vCenter UUID |
| `target_disk_key` | Exact positive signed-int RDM virtual-disk key |
| `diagnostic_enabled` | Explicit lowercase `false` until live execution separately authorized |
| `authorize_once` | Explicit lowercase `false` until one attempt separately authorized |

Associate an Operations-managed `rdm_vcenter_readonly` credential with required
nonpassword `username` and password-typed `password` fields. These are the only
target credentials; do not read or copy the VMware integration credential.
The separate Operations identity comes **only** from the collector-injected
`AdapterConfig.getAdapterCredentials()`. Both password accessors rely on actual
platform decryption, deliberately not mocked as production capabilities.
There is no fallback identity, node-local file, environment credential, external
process or runtime download.

Enterprise trust requires actual SDK `CertificateConfig` CA PEM material,
valid dates/key usage and hostname/date enforcement. ID-only references and
accepted leaf certificates are not resolved or silently trusted. The SDK
instance carrier exposes one CA-material list; administrators must approve the
bundle for both endpoint policies when both use enterprise trust. Each endpoint
has its own TLS context and strict hostname verification. If separate unrelated
bundles cannot be delivered safely through that carrier, stop and qualify the
managed trust-delivery contract; do not synchronize files on collector nodes.
JVM trust is usable only if each endpoint already chains to its approved store.
Actual platform CA delivery, collector movement and FIPS compatibility remain
unverified.

## Run semantics and evidence

Before any credential read or request, `collect()` requires valid configuration
and **both** flags true, then consumes its in-memory grant. The Operations
provider authenticates, completes bounded caller-visible VM pagination, obtains
the exact complete scoped VMware ResourceKey, freshly rechecks it and releases
its authentication token. Token release does not mutate monitored inventory.
Primary Operations errors remain primary; cleanup-only failure has the distinct
`OPERATIONS_CLEANUP` stage and prevents PASS.

SOAP then verifies the actual vCenter UUID and retrieves only the configured VM
MOR, its current host's SCSI LUN inventory and a VM host/changeVersion fence.
No ContainerView or unrelated VM/host traversal is used. This first diagnostic
requires **exactly one RDM on the target VM**, matching the configured disk key;
it does not silently choose one from multiple mappings. Exact current-host
backing UUID join, qualified LU-associated NAA and canonical agreement are
required. Any missing/quarantined/conflicting/partial result prevents PASS.
All returned foreign identifiers, flags, order and supported empty nonunique
values are preserved in memory, never rendered in status.

New diagnostic SOAP uses direct no-proxy TLS sockets rather than the unchanged
E4.2 URLConnection transport, avoiding JVM-global proxy/authenticator/cookie
handlers. E4.6 also corrects E4.5's unconnected default TLS socket: it consulted
the global SOCKS ProxySelector. Both diagnostic transports now connect an
explicit `Socket(Proxy.NO_PROXY)` and layer strict TLS before writing HTTP.
Both JVM-mode binders also construct private TLS contexts from the default
TrustManagerFactory/JVM roots, rather than inheriting mutable global
`SSLContext.getDefault()` (which another integration could replace). Synthetic
tests install a permissive default and verify that both binders still reject
the untrusted test CA. This does not claim immunity to administrator changes to
the JVM trust store or installed security providers.
The original E4.2 default transport is not silently represented as hardened;
it is not used by this diagnostic.

Execution has a 150-second total caller deadline; per-request bounds, XML/JSON
size/complexity limits and finite API pagination remain in force. Cancellation
closes active request sockets; SOAP cleanup has an independent maximum
30-second budget and Operations token cleanup 5 seconds. The outer worker can
therefore retire after its caller returns. Uninterruptible JVM DNS/platform
accessors may take longer; Java cannot forcibly terminate them. No second
worker can start on that object, even after timeout or configuration refresh.
Configuration mutation/replacement, stop/discard and stale snapshots fail closed;
no partial graph is emitted. Retired generations cannot overwrite new status.

One-shot consumption is **per Java object, not durable cluster authorization**.
A process restart/new object while persisted flags remain true could repeat the
read-only attempt. After one result, an operator must stop that separate
diagnostic instance and reset both flags false before restart or placement
changes. Repeated collection on the same object performs no additional request
and retains the terminal sanitized summary/counts.

Expected terminal evidence is only:

```text
RDM_DIAGNOSTIC state=PASS stage=GRAPH code=READ_ONLY_GRAPH_QUALIFIED vms=1 mappings=1 luns=1
```

Failure reports contain static stages/codes and zero success counts. `NOT_FOUND`
means no match in successfully enumerated caller-visible Operations scope, not
global absence. PASS proves a qualified graph for that attempt, not RBAC
completeness, resource publication, topology persistence, failover or global LUN
deduplication. Cloud Proxy routing is not inferred from a generic connection
failure. The safe report is `OPERATIONS/UNREACHABLE`, not an invented routing
root cause.

## Operator procedure — not authorized for execution by E4.6

1. Obtain separate approval for a distinct diagnostic package/instance and
   actual read-only lab requests. First qualify its candidate descriptor loader,
   SDK linkage and packaging offline; there is no E4.6 PAK to install now.
2. From the intended collector role, establish routing to both explicit FQDNs,
   approved CA delivery and vCenter inventory-read privileges for the selected
   VM/current host. Confirm the injected Operations principal can read complete
   VMware VM keys; do not assign assumed administrator rights or copy secrets.
3. Only after that approval, configure the **new diagnostic instance**, not the
   working POC. Enter exact target values privately; do not paste a ResourceKey,
   fixture JSON or password into ordinary fields. Initially set both flags false.
4. Validate configuration. Its success is configuration-only; certificate UI
   review/delivery and Save/Enable behavior require platform verification. Stop
   if only leaf/ID trust reaches the SDK or the UI cannot persist valid settings.
5. On explicit one-attempt authorization, set both flags true on the separate
   diagnostic instance and permit one SDK `collect()` invocation. Wait up to
   150 seconds for the sanitized terminal status; do not infer execution from
   Validate Connection, Collecting or Data Receiving alone.
6. Stop that instance immediately after the first terminal result; reset both
   flags false. Record only the sanitized stage/code/counts, collector role and
   timestamps. Do not dump serialized config, passwords, response bodies or graph.
7. Confirm no additional resource/relationship/metric/property/event payload was
   returned. Platform creation of the diagnostic **adapter instance itself** is
   a separately authorized configuration action, not graph publication. No
   VMware-owned resource or relationship may be changed. If unexpected output or
   inventory changes occur, stop; do not run cleanup against VMware inventory.

## Offline tests and remaining prerequisites

From the factory root:

```bash
VCFCF_SDK_JAR=/home/scummins/Documents/vmware-sdk/vrops-adapters-sdk-2.2.jar \
  bash content/sdk-adapters/rdm-topology-poc/scripts/run-diagnostic-tests.sh
```

Use `--no-network-tests` to omit synthetic localhost sockets. Tests use real SDK
carriers and empty SDK results; shared composition exercises the actual provider
and SOAP decoder with synthetic wire replies, plus actual localhost TLS servers.
Synthetic credential seams do not prove real platform decryption/injection.
There is no fake platform `Crypt` class or real credential retrieval.

Still unqualified: actual SDK descriptor loading with platform dependencies,
candidate package registration, UI lifecycle, managed CA serialization/delivery,
injected decryption/token/RBAC, real gateway HTTP compatibility, per-placement
routing and supported collector JDK/FIPS/classloader behavior. E4.6 is offline
preparation only, not authorization for installation or runtime qualification.
