# Dormant native Operations identity provider (E4.5)

This is an **offline implementation candidate**, not an activated collector,
installed feature or proof of current collector authentication/RBAC. The registered
adapter, build-9 descriptor, fixtures, discovery engine and additive topology
implementation are unchanged. No new PAK is produced.

## Binding, authentication and trust

`ops/OperationsBinding.dormant(...)` takes the real SDK `AdapterConfig`, an
explicit certificate-valid `https://<Operations-FQDN>[:port]/suite-api`, and a
separate Operations trust selection. No endpoint defaults, userinfo, IP literal,
single-label hostname, localhost, query, fragment or redirects are supported.
The proposed settings are not wired to the installed descriptor/UI.

The only credential source is `AdapterConfig.getAdapterCredentials()` and its
`AdapterCredentialConfig.getUserName()/getPassword()`. Associated vCenter and
VMware integration credentials are never read. The SDK password accessor calls
platform `Crypt`; SDK-only execution cannot decrypt its synthetic slot and fails
closed. Tests inject synthetic readers at a package-private seam, not a production
fallback. Credential association replacement rejects. Lifecycle owners must
serialize configuration replacement and discard an old binding; arbitrary
concurrent SDK-carrier mutation is not supported.

`OperationsBinding.production()` always rejects with `RUNTIME_UNQUALIFIED`.
`dormantProvider()` constructs an inert provider; only an explicit subsequent
`capture/resolve` would perform reads. No entrypoint calls these methods.

`jvm` uses the existing JVM trust context and rejects supplied material;
`enterprise_ca` consumes in-memory SDK `CertificateConfig` PEM material (up to
64 current CA certificates, 32 KiB each, unique DER, CA/keyCertSign checks).
Unresolved certificate IDs, leaf pins, multiple certificates in one PEM, and
explicit hostname/date-disable flags reject. It builds an isolated JDK trust
store, never global TLS settings or collector files. CA representation and
cluster/Cloud Proxy delivery remain empirical prerequisites. Operations and
vCenter transports and trust scopes are separate.

Public API contracts are taken from the factory's `reference/docs/operations-api.json`:

1. `POST /api/auth/token/acquire`, JSON username/password; optional authSource
   is omitted, never guessed. Validate token and epoch-millisecond validity.
2. `GET /api/resources?adapterKind=VMWARE&resourceKind=VirtualMachine&page=N&pageSize=200`.
3. `GET /api/resources/{resource-UUID}` for fresh full-key confirmation.
4. `POST /api/auth/token/release`, authenticated `Authorization: OpsToken ...`,
   **no request body**. This releases only the current token, not inventory.

No other HTTP methods/paths are allowed. No resource writes or topology API
operations exist. Cleanup uses a separate five-second budget and never masks a
primary error; `cleanupFailure()` exposes only a static sanitized category.
Secret char arrays and request byte buffers are cleared; SDK immutable Strings,
JSON Strings and TLS/JDK internal buffers cannot be securely erased. No secret,
body, ResourceKey, token, raw exception cause or suppressed exception is logged.

## Transport and bounded parsing

The narrow JDK `SSLSocket` HTTP/1.1 transport intentionally does not use global
HTTP `Authenticator`, `CookieHandler` or proxy machinery. It verifies the chain,
standard HTTPS endpoint identity **and independent dialed-host SAN** before
sending any HTTP headers or credentials. No hostname override, node key,
maintenance account or ambient cookie fallback exists.

Each request opens/closes its own connection with `Connection: close`. Supported
responses are HTTP/1.0 or 1.1 status/header framing and identity-encoded JSON with
Content-Length, simple chunked encoding or EOF framing. Duplicate headers,
Content-Length plus Transfer-Encoding, chunk extensions/trailers, folded headers,
compressed responses and interim responses fail closed. Some legal HTTP servers
may use those unsupported representations; interoperability with the designated
Operations endpoint still requires qualification, not a permissive fallback.

Limits: connect/read 10/30 seconds, total request at most 40 seconds, capture
120 seconds, cleanup 5 seconds, 20,000 caller-visible VMs / 100 pages / 1,000
requested targets. Request JSON is at most 32 KiB. Response at most 4 MiB;
header/chunk-line metadata at most 64 KiB / 512 lines / 8 KiB per line. JSON is
strict UTF-8, depth 24, 100,000 values, 16 KiB per string and 64 characters per
number. Duplicate JSON members, malformed escapes/surrogates and coerced boolean
flags reject. No remote links are followed.

Successful response payloads total at most 32 MiB per capture; selected complete
key text at most 2 MiB characters. Only requested scoped keys are retained across
pages; other keys are fully validated then discarded, retaining bounded UUID and
scope sets for duplicate/coverage checks. One bounded JSON page is materialized
at a time. These are conservative fail-closed collector memory limits, not
pagination truncation or partial success.

A daemon worker enforces caller request deadlines even during DNS/slow-drip;
timeout/cancellation closes the socket and makes that transport unusable rather
than spawning further request workers on it. A platform DNS call itself may not
be interruptible; at most that transport's one daemon can remain until the JVM
resolver returns. Cleanup may consequently fail after a timed-out transport;
this is reported separately. No retry or alternate identity is attempted.

## Scoped keys and snapshot lifecycle

`capture(vcenterUuid, requestedMoids)` scans inventory once, authenticates once,
and refreshes each requested resource by UUID. Use this bounded batch interface
for a future collection, not repeated whole-inventory scans per VM. `resolve`
is only the singleton convenience form. No cross-cycle inventory/token cache.

Both required names must be exact and uniquely flagged: `VMEntityVCID` equals
the supplied vCenter UUID and `VMEntityObjectID` equals the supplied `vm-N`.
Matching is exact, not case-normalized, display-name or bare-MOID based.
Duplicate resource UUIDs or duplicate scoped identities reject. Every returned
VM key is validated, including other-vCenter candidates; malformed possible
matches cannot silently disappear. Each requested detail must reproduce the
complete listing key, including original identifier order, names, values,
uniqueness flags and display name. Any difference fails closed.

The returned immutable `DiscoveryGraph.VmKey` preserves the complete supported
key. Explicit empty nonunique strings are retained; missing/null values reject,
because the existing SDK drops null rather than preserving a faithful key.
Names, flags and unknown additional identifiers are never invented or dropped.
Snapshots expire 120 seconds after capture begins and reject a different scope.
An explicit `Snapshot.resolver()` bridge feeds only E4.2's in-memory graph; it
does not publish resources/relationships. Rich static diagnostics remain on the
provider; the existing graph interface maps errors to its coarser categories.

Page number, page size, total count, no duplicates, exact coverage and fresh
details are checked. This cannot prove globally atomic inventory under churn or
RBAC completeness. NOT_FOUND means absent from the validated caller-visible
result, not absent globally; it cannot justify stale deletion.

## Offline tests and remaining qualification

Run with the legitimately supplied local SDK (never copied/bundled):

```bash
VCFCF_SDK_JAR=/path/to/authorized/vrops-adapters-sdk-2.2.jar \
  bash scripts/run-ops-provider-tests.sh
```

The standalone runner compiles Java 11, creates only ephemeral synthetic CA/server
keys and uses localhost-only TLS servers. `--no-network-tests` excludes sockets.
Synthetic tests cannot prove platform Crypt, injected principal privileges,
collector/Cloud Proxy FQDN routing, managed CA propagation, target API framing or
resource-read scope. No non-platform runtime dependency or download is introduced;
only JDK classes and the platform-provided proprietary Operations SDK are needed.

Smallest E4.6: separately authorize an instrumented, read-only in-process
qualification of injected decryption/authentication, strict endpoint/trust,
caller-visible exact VM keys and placement. Keep publication disabled, preserve
the installed build-9 instance, and design any diagnostic hook/migration explicitly
before installation. No production activation or topology persistence claim
follows from this offline provider.
