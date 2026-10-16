# Fixture deployment status — VCF Content Factory RDM Topology POC

This POC is not deployment authorization. It accepts one operator-supplied,
persisted fixture setting to inspect Java Integration SDK relationship mechanics.
Missing `execution_mode` preserves this route. See the separately authorized
[same-kind diagnostic procedure](same-kind-diagnostic.md) for the isolated,
publication-free route; never retarget an existing fixture account.

## Runtime fixture setting

The adapter-instance field **Runtime Fixture JSON (lab only)** (`fixture_json`)
is runtime-required in fixture mode (optional in union descriptor metadata),
an ordinary string setting, not part of account uniqueness or a
credential. Supply one object with exactly `foreign_vm` and `mapping`:

```json
{"foreign_vm":{"adapterKindKey":"VMWARE","resourceKindKey":"VirtualMachine","name":"synthetic-vm","resourceIdentifiers":[{"identifierType":{"name":"VMEntityObjectID","isPartOfUniqueness":true},"value":"vm-1001"},{"identifierType":{"name":"VMEntityVCID","isPartOfUniqueness":true},"value":"11111111-1111-4111-8111-111111111111"}]},"mapping":{"vcenter_instance_uuid":"11111111-1111-4111-8111-111111111111","vm_instance_uuid":"22222222-2222-4222-8222-222222222222","virtual_disk_key":"2000"}}
```

Synthetic example only. Replace `foreign_vm` with the **complete exact**
Suite API-derived VM ResourceKey, preserving all identifiers and boolean
uniqueness flags. Both `VMEntityObjectID` and `VMEntityVCID` must be unique;
the latter must exactly match `mapping.vcenter_instance_uuid`. The mapping
uses the verified VM **instance UUID**, not its Operations UUID/MOID, and a
canonical positive 32-bit virtual-disk key encoded as a string. An optional
`VMEntityInstanceUUID` identifier must agree with the mapping. Do not drop
additional native identifiers or use names as identity evidence.

Every identifier must retain an explicit string `value`. An exactly empty
string is supported only with `isPartOfUniqueness: false`; it remains empty,
not omitted or replaced. Null, omitted and whitespace-only values are rejected.
All uniqueness-bearing values remain nonblank. Names must be distinct even
ignoring case because the SDK would reject a colliding entry. The fixture
array preserves response order; the SDK ResourceKey itself sorts identifiers
by name without changing their names, values or flags. SDK key matching ignores
nonunique values, but that does not justify dropping their entries or coercing
null/absence to empty: ResourceKey refuses null-valued entries.

Version-1 supports one VM and one mapping. Optional version-2 supports a bounded
complete attachment set with qualified CanonicalLun evidence; see
[version-2 contract](canonical-lun-fixture.md). The parser rejects unknown fields,
duplicate keys/identifier names, missing flags, invalid/coerced values,
conflicting scope and malformed JSON. Limits: 32,768 input characters, 4,096
per string, depth 12 and 512 parsed values. Validate the complete exact key
and unique VM against approved prior evidence before configuration; this
offline fixture cannot establish live existence or detect omitted unknown
identifiers. Real fixture JSON belongs outside Git, PAKs, logs and reports.

Invalid or absent configuration fails closed; failed reconfiguration clears
the previous target and stale resource/config pairs cannot emit edges. Before
changing target, stop collection and clean up the old POC instance/mapping;
obsolete-resource lifecycle is not implemented. Reuse the controlled lab
baseline, stop conditions and POC-only cleanup plan.

Instance-managed configuration is expected to follow collector placement and
failover, but actual UI round-trip/field length, platform initialization and
failover delivery remain unverified. Stop if the field is absent, truncated,
rewritten or requires credentials. No node-local file/env fallback exists.

## Fixture mode: no endpoints, credentials, or network activity

- Fixture mode requires no endpoint or credential configuration and opens no
  network connection. Diagnostic mode instead requires explicit vCenter and
  Operations endpoints, an Operations-managed read-only vCenter credential,
  the separate SDK-injected per-instance Operations identity, and strict
  certificate trust; see the [diagnostic configuration](same-kind-diagnostic.md).
- Test Connection validates the supplied configuration, including fixture JSON
  in fixture mode, and rejects invalid configuration. It does not connect,
  decrypt credentials, arm diagnostics, or prove live discovery or collection.
  Completed collection and API evidence are needed to establish emission.
- Fixture mode does not collect live inventory or emit numerical metrics. Version-2 creates
  CanonicalLun from validated evidence and emits explicit fixture-provenance properties.

## Deployment prohibition

Do not upload, install, configure, or enable this POC on VCF Operations without
fresh explicit approval for a controlled deployment experiment. Compilation and
local PAK assembly do not demonstrate foreign relationship persistence or UI
presentation. That evidence requires a separately authorized test deployment.

## Local-only verification

Use the commands in the repository `README.md`. They require a locally supplied
Integration SDK JAR and do not contact VMware or VCF Operations systems.
Actual SDK lifecycle and descriptor-loading qualification has passed with the
supplied SDK and eight checksum-verified external platform libraries, without
logging/platform shims or constructor bypass on the qualified paths. This
includes original fixture and dispatcher lifecycle routes through both public
constructors; see the [E4.9.3 qualification](configuration-compatibility-e493.md).
Older constructor-bypass unit tests remain supplementary only. SDK logger
appender errors in the offline harness are recorded limitations, not a logging
qualification pass. These results do not prove live collector startup or
upgrade recovery, Operations UI serialization, managed credential/trust
delivery, networking, or failover behavior; those require separately authorized
lab evidence. Previously recorded E2 live topology observations remain distinct
from this offline qualification.
