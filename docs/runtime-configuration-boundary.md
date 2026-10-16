# Dormant native discovery runtime boundary (E4.3)

This is an offline implementation contract, **not installed configuration**.
The build-9 descriptor, fixture parser, entrypoint and PAK are unchanged.
`RuntimeDiscoveryBinding` is not called by the active adapter. Do not change the
installed fixture instance to these settings. No new PAK is built.

## Proposed configuration

Future owned adapter-instance ResourceConfig (adapter and kind both
`vcfcf_rdm_topology_poc`) supplies exactly three ordinary, nonunique identifiers:

| Key | Value |
|---|---|
| `vcenter_endpoint` | Strict HTTPS URL ending `/sdk`, no userinfo/query/fragment |
| `trust_mode` | `jvm` or `enterprise_ca` |
| `discovery_scope` | `caller_visible_inventory` |

There is no TLS-insecure mode. Caller-visible inventory is an explicit scope,
not proof of complete vCenter permissions or authority to delete absent objects.
Inherited E4.2 ceilings remain: 128 SOAP requests/120-second cycle, 64-object
pages, 20,000 attachments, bounded XML/body size and secondary cleanup budget.
Transport connect/read limits are 10/30 seconds. No user-defined filters are
invented. These values are not configuration fields in build 9.

ResourceConfig.getResourceCredential() must reference proposed credential kind
`rdm_vcenter_readonly`, exactly `username` (not password) and `password`
(`isPassword=true`). `CredentialConfig.getCredenialFields()` is the actual SDK
spelling. `CredentialFieldConfig.getValue()` decrypts password fields using
platform `com.vmware.vcops.security.Crypt`; the authorized SDK exposes the call
but does not supply that platform implementation. Missing linkage/decryption
fails closed with a static authentication code. Offline tests inject a
package-private synthetic reader, **not a production decryption replacement**.
No VMware integration credentials, ambient files, environment or fallback
accounts are read. The binding rechecks owned-resource/credential association
and mutable field metadata before leasing credentials; normal SDK lifecycle
must serialize configuration replacement against acquisition.

Credentials are acquired just before an explicitly requested discovery, retained
only in an AutoCloseable lease, and char arrays erased after use/close. Java SDK
immutable decrypted Strings and SOAP serialization buffers cannot be securely
erased; no stronger memory-erasure guarantee is claimed. Object summaries and
exceptions contain fixed codes, no endpoint, account, raw inventory or causes.

## Trust

`jvm` uses default JVM trust and refuses supplied certificate material to avoid
silently ignoring enterprise configuration. `enterprise_ca` parses actual
ResourceConfig.getTrustedCertificates()/CertificateConfig.getCertificateString()
PEM in memory. Only bounded, valid, unique CA certificates with appropriate key
usage are accepted; false hostname/date enforcement flags reject. The resulting
private SSLContext trusts only that explicitly supplied CA set. E4.2 transport
retains strict chain, dates and independent dialed-host SAN verification.

Certificate IDs alone are opaque references, not trust material: any unresolved
getTrustedCertificateIds() rejects rather than reading collector-local stores.
Runtime representation, platform accepted-leaf versus CA behavior, ID-to-material
association and trust propagation/failover still require platform qualification.
No trust-all manager, node-local file or global SSL modification is introduced.

## Operations identity boundary

ScopedOperationsResolver.offline accepts synthetic/trusted offline complete
snapshots, expected vCenter UUID, collection generation and monotonic age budget
(at most 120 seconds). It retains immutable complete ordered VM keys, including
actual uniqueness flags and supported empty nonunique values, through the E4.2
resolver. Scope/generation/future/expired/incomplete/unqualified snapshots reject.
Duplicate VM candidates are ambiguous, not first-match wins. Provider failures
map to sanitized authentication/connection/incomplete codes.

Snapshot authentication/routing flags are **offline provider inputs**, not a
verification mechanism, user configuration or production authorization gate.
`production()` always fails closed. There is no HTTP Suite API implementation.
Injected AdapterConfig.getAdapterCredentials() exists, but password decryption,
strict authenticated transport, least-privilege RBAC and collector/Cloud Proxy
endpoint routing are not proven for this adapter. No endpoint/token scheme or
localhost fallback is guessed. A production provider must establish these
contracts before enabling this boundary; the test snapshot path must not be
accepted as a substitute for real authentication.

## Offline checks and next boundary

Run `VCFCF_SDK_JAR=<authorized-local-SDK> bash scripts/run-runtime-tests.sh` from
this independent repository. Tests use actual SDK configuration carriers plus
synthetic credential values and an ephemeral CA; no VMware API is contacted.
The existing `scripts/run-discovery-tests.sh` remains standalone and unchanged.
The new production code requires only JDK/platform SDK classes; no runtime
download, Python, subprocess, extra JAR, fixture file or storage-array API.

Next implementation should qualify strict injected-identity authentication,
RBAC/routing and Operations-managed CA delivery on every intended collector
role, then add the narrow read-only provider and lifecycle binding. Publication,
production descriptor migration, shared-LUN ownership and cleanup remain off.
