# E4.9.3 — Fixture configuration compatibility

## Cold-start defect and correction

The combined dispatcher descriptor expanded the legacy instance from one
identifier (`fixture_json`) to twelve optional strings with no defaults.
The genuine SDK's `ResourceKindIdentifiers.verifyResourceIdentifiers` can
synchronize a legacy key against an expanded registered template: it preserves
the fixture and fills missing or null template values with exact empty strings.
A test template derived from the actual descriptor produces twelve nonunique
identifiers, including `execution_mode=""` and ten empty diagnostic settings.

Using actual entry/framework JARs from preserved PAKs and the genuine SDK,
that SDK-produced carrier configures and collects with build 9, but build 11
rejects it as `INVALID_CONFIGURATION`; build 12 reports
`reason=EXECUTION_MODE_EMPTY`. This reproduces a cold-start compatibility
regression independently of the separate partial-update defect.

Build 13 interprets exact empty mode as fixture only after all existing fixture
validation succeeds: one valid fixture, known/nonunique/nonduplicate fields,
no populated diagnostic settings, and no associated target credential object
or unresolved credential UUID reference. Raw null, whitespace, case variants,
unknown modes, duplicate/unique/unknown fields, mixed diagnostic settings and
invalid fixtures still fail closed. In particular, populated `false` diagnostic
flags are not empty values. The dispatcher never invokes SDK synchronization
or rewrites an incoming carrier to make it valid.

The new credential-reference guard applies only to the newly accepted empty
mode. Previously accepted absent-mode and exact `fixture` UUID-only carriers
retain their acceptance policy. Credential objects remain rejected for every
fixture form. SDK-injected Operations identity is distinct from an associated
target credential; its existing behavior is unchanged. The empty form's
credential reference is included in the full-source mutation guard.

Both public constructors, adapter kind, descriptor, resource identities and
additive relationships remain unchanged. Explicit diagnostic mode still uses
its existing strict credential/TLS validation and empty publication results.
The compatibility exception cannot select, authorize or arm diagnostic work.

## Partial-update defect retained from build 12

Both SDK `setAdapterInstResourceId(Integer, String)` and
`setAdapterInstResourceId(Integer, UUID, ResourceKey)` deliberately produce an
`AdapterConfig` without an instance `ResourceConfig`. `AdapterCache.update`
retains the last full instance settings and processes monitoring, stopped/
removed-resource and attribute-policy updates. `AdapterBase.applyConfiguration`
skips the instance `onConfigure` callback when the incoming instance is absent.

The original fixture adapter accepts full configuration followed by such an
update and continues collection. Build 11 rejected it and retired its route.
The retained build-12 correction forwards matching updates unchanged only on
an already validated, active fixture route, checking instance ID and supplied
UUID/key against the retained full source before and after delegation. Cold,
stopped, retired, conflicting-owner and diagnostic updates fail closed, as do
instance-less credential, custom-configuration and extended/trust updates.
No full configuration is synthesized from a partial update.

## What the live evidence does and does not establish

The supplied account-15903 log at `2026-10-16T02:57:22.346Z` contains only
`RDM_DISPATCHER INVALID_CONFIGURATION`; it identifies no carrier shape or
rejection phase. The SDK-supported cold and partial carrier defects are
reproduced, but the actual failing callback and restoration remain unconfirmed.

The SDK synchronization caller chain verified offline is cache resource lookup
(`AdapterCache.getMonitoringResource` / `getStoppedResource`) against registered
resource-key templates. The experiment derives a template from descriptor
metadata; it does not prove that the collector automatically performs this
conversion before initial `configure`. The SDK's own full configure path does
not perform that conversion: a fixture-only absent mode stays absent before
and after configuration. Java serialization also preserves absent, empty and
null distinctly. No collector/UI serialization claim is inferred from these
findings, and raw null is not accepted on that basis.

Failures retain `INVALID_CONFIGURATION` with a fixed `reason=UPPERCASE_CODE`
for carrier/ownership, identifiers, mode, mixed settings, fixture parsing,
unsupported updates, delegate construction/configuration or platform linkage.
Input values, credential contents, dynamic class names and exception payloads
are not copied into that status.

## Offline evidence and limits

The preserved packaged comparison covers 86 synthetic cold-start scenarios
through both public constructors on builds 9, 11 and 12 (516 attempts), plus
12 separate full-then-partial sequences. It includes optional value states,
malformed fields, fixture variants, kinds/identities, associated and injected
credentials, and SDK-produced expanded keys. Every process exits 0; individual
rejections and carrier-construction exceptions are recorded as test outcomes,
not successful configurations. Both build-11 and build-12 cold acceptance is
66 of 172 attempts; build 12 separately restores the partial sequences.

The final shim-free source runner passes 343 existing dispatcher assertions,
325 partial/rejection assertions, 392 cold-start assertions and 3486 actual
fixture lifecycle assertions. Six scenarios (legacy mapping or version-2 LUN,
each absent, explicit fixture or SDK-expanded empty mode) run through four
routes (original/dispatcher, each no-argument or collector constructor).
It checks repeated collection, exact keys/additive edges/properties, partial
monitoring, stop/remove/restore, attribute policies, retained raw source,
credential-reference mutation, no-input-rewriting and stop/discard. Empty SDK
resource-result maps also exclude per-resource metrics, properties and events;
embedded/explicit discovery and resource status payloads remain empty on
rejected/diagnostic routes.

The runner uses actual SDK/platform libraries, no logging or platform shims.
`ADAPTER_HOME` and `ALIVE_BASE` point into its disposable temporary directory.
Five genuine SDK ERROR-level messages report the missing appliance root file appender;
these are recorded offline logging limits, not missing classes or failed
fixture assertions. No replacement logger or platform shim is introduced.
The older standalone fixture suite uses its documented test-only logging and
constructor-bypass approach and remains supplementary evidence.

SDK/platform JARs are local-only proprietary inputs, external to Git and PAKs;
they are not reproducible from this repository. Their relevant bytecode findings
are summarized above. These offline tests do not establish UI serialization,
collector upgrade, managed credential/trust delivery, appliance persistence,
live authentication or networking.

## Human-controlled retry and smallest missing evidence

Verify the reviewed candidate's recorded SHA-256. Preserve builds 9–12 and the
private current configuration, instance identity and fresh collection baseline.
Obtain explicit approval for a controlled same-kind upgrade/retry, follow the
supported platform procedure and verify the actual installed version. Retain
account 15903 and its exact fixture, mode, credentials and resource identities;
do not clear/re-save optional settings speculatively. Do not uninstall, delete
inventory, restart collectors or change trust to test this candidate.

Check a new collection timestamp, status and exact keys/additive relationships.
Retained `ubuntu2404 → mapping 2001` inventory alone proves no new collection.
If configuration still fails, stop the retry and capture the fixed reason code.

The smallest useful additional evidence is the failing callback's sanitized
shape: instance resource present/absent; identifier key names, uniqueness flags
and value states only (`absent`, `null`, `empty`, `populated`); presence of target
credential object/UUID reference, injected credentials, custom or extended config;
and whether supplied ID/UUID/key agree with the existing account. Include
installed version, collector placement and callback time. Exclude fixture,
endpoint, identity and credential values, tokens and exception payloads.
Collecting this live evidence requires separate authorization and was not attempted.
