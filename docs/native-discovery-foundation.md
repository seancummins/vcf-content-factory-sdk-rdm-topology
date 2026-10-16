# Native discovery foundation (E4.2)

This is **inert, offline-tested Java code**, not an enabled collector. No adapter
entry point, descriptor, configuration, credentials, existing fixture behavior or
relationship publication has changed. Build 9 remains the last development PAK;
no new PAK is necessary to test this foundation.

## Boundary and classes

`discovery.VimDiscoveryClient` consumes a fixed `Transport` and an explicit
`DiscoveryGraph.VmResolver`, and returns immutable `DiscoveryGraph` DTOs. It does
not import SDK resource collections, metric writers or relationship builders.
No SDK mutable ResourceKey is exposed. The eventual publisher must reconstruct
the exact SDK foreign key from the immutable ordered identifier entries.

`HttpsSoapTransport` uses JDK HTTPS, an explicitly supplied SSLContext, standard
chain verification and independent configured-host SAN verification. Its
`trust(Collection<X509Certificate>)` builds explicit enterprise CA trust **in
memory**; it does not implement platform certificate-review/configuration binding.
No global trust changes, node-local files, redirect following or hostname waiver
exist. The fixed path is HTTPS `/sdk`, with bounded connect/read settings, request
and response bytes, plus response wall-time checks. The SOAPAction declares the
6.5 vim25 compatibility baseline (`"urn:vim25/6.5"`), following the inspected
pyVmomi version-namespace convention; target compatibility remains a live proof.

`SecureSoap` bounds a StAX pass before hardened namespace-aware DOM construction:
DTD/entities/external resolution/XInclude are disabled, with explicit bytes,
depth, nodes, attributes and text limits. SOAP envelope/body/operation namespaces
and required typed arrays are checked. Fault bodies and exception causes are
never included in diagnostics.

`VimDiscoveryClient` performs RetrieveServiceContent/Login/CreateContainerView,
paged RetrievePropertiesEx/ContinueRetrievePropertiesEx, bulk current-host SCSI
inventory and a bulk VM instance/changeVersion/current-host fence. It attempts
CancelRetrievePropertiesEx, DestroyView and Logout on completion or failure/session retirement, using a separate
bounded cleanup allowance; cleanup failure is secondary. A failed client is
retired, not reused. Caller-supplied password arrays are cleared; XML requests
necessarily materialize credentials transiently in JVM memory, never disk/logs.

Defaults are implementation budgets, not VMware limits: 4 MiB/response, 10,000
objects/query, 64 pages/query, 128 cycle requests, 120-second cycle and three
cleanup requests within a separate 30-second allowance. Timeouts bound individual
blocking I/O. A per-request daemon watchdog disconnects an active connection at
the remaining cycle deadline or thread interruption, and is retired in finally.
Read chunks and request boundaries check both conditions. Cleanup temporarily
clears and then restores interruption to attempt bounded session retirement;
the final over-budget response is rejected, not published. Provider-specific
socket cancellation behavior still requires collector-runtime qualification.

## Identity and qualification

`OfflineResolver` requires a declared complete deterministic Operations candidate
snapshot. It resolves **exact vCenter instance UUID plus VM MOID**, retaining the
entire foreign identifier list, original order, values and boolean uniqueness
flags. Empty nonunique strings are supported; null/absent values, duplicate names,
missing required unique flags, unscoped/ambiguous candidates fail closed. Display
names do not participate. No Operations authentication or credential-reuse client
is guessed here.

Mapping identity remains vCenter UUID + VM instance UUID + positive disk key.
The graph rejects conflicting scoped VM/instance identities and duplicate mapping
keys. A current-host exact backing lunUuid/ScsiLun.uuid join is mandatory; matching
another host is never substituted. Failed host/LUN qualification retains the
independently resolved VM/mapping with a static unresolved status.

Intrinsic LUN qualification requires one distinct complete LU-associated binary
VPD83 NAA5/6 payload, with namespace, namespaceId bounds, header/length/reserved
bits/association/type validation and full canonical-name agreement. The actual
pyVmomi serializer was inspected offline: DurableName.data is repeated signed
decimal byte elements, not guessed base64. Equivalent durable/alternate NAA
claims deduplicate. Port/target designators do not supply LU identity; malformed
or conflicting NAA claims fail closed. Opaque SCSI UUIDs are not decoded into NAA.

Canonical identity is unchanged: `scsi-vpd83:naa:<full-lowercase-payload>`, with no
VM/host/vCenter suffix. Automatic provenance is `vsphere-soap-intrinsic`, **not**
independent storage-side corroboration; no synthetic storage_reference is created.
Cross-observation UUID/token conflicts and descriptive consensus failures quarantine
affected LUN associations. Optional descriptions and positive checked capacity
are observations, not identity. Single-authority token equality does not prove
cross-vCenter assignment uniqueness or safe multi-writer publication.

## Tests and dependencies

```bash
bash scripts/run-discovery-tests.sh --no-network-tests
bash scripts/run-discovery-tests.sh
```

The first runs deterministic parser/protocol/graph tests without opening sockets.
The second additionally creates an ephemeral test CA and CA-signed localhost
server with keytool, and tests successful explicit CA trust, rejected default
trust and rejected wrong hostname. It contacts loopback only. All keys are
synthetic test artifacts removed by the script; no enterprise trust or inventory
is loaded. This test-only use of keytool/executables is not deployed behavior.

Production compiles with `javac --release 11` and uses JDK java.base/java.xml only.
No generated vim25 bindings, JAX-WS/SAAJ provider, third-party library, runtime
download, Python, container or array API is introduced. Platform SDK/framework
remain compile/provided dependencies of the existing adapter, not new bundled
libraries. No independently licensed managed-adapter source was copied.

## Remaining integration prerequisites

The smallest next increment is reviewed Operations-managed endpoint/credential/
trust binding plus strict read-only Suite API transport for the existing resolver
interface, still without topology publication. Verify injected-identity RBAC,
Cloud Proxy reachability and certificate trust on the supported collector/JDK/FIPS
matrix. Prove actual SOAP compatibility and runtime classloader providers under
separate read-only lab authorization. Publication/adoption, credential-required
migration, LUN multi-writer ownership, stale cleanup, collector relocation and
live local-edge persistence remain separate work; no deletion is inferred from
empty or partial caller-visible snapshots.
