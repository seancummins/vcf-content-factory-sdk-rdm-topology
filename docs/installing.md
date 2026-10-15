# Fixture-only deployment status — VCF Content Factory RDM Topology POC

This POC is not deployment authorization. It accepts one operator-supplied,
persisted fixture setting to inspect Java Integration SDK relationship mechanics.

## Runtime fixture setting

The adapter-instance field **Runtime Fixture JSON (lab only)** (`fixture_json`)
is a required ordinary string setting, not part of account uniqueness or a
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

Only one VM and one mapping are supported. The parser rejects unknown fields,
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

## No endpoint, credentials, or network activity

- It has no vCenter, VCF Operations Suite API, or other endpoint setting.
- It requests no credentials and opens no network connection.
- It has no connection-validation operation; Test Connection returns success
  without remote or fixture validation. Configuration, not that button,
  validates the fixture; completed collection and API evidence prove emission.
- It does not collect live inventory, create `CanonicalLun`, or emit metrics.

## Deployment prohibition

Do not upload, install, configure, or enable this POC on VCF Operations without
fresh explicit approval for a controlled deployment experiment. Compilation and
local PAK assembly do not demonstrate foreign relationship persistence or UI
presentation. That evidence requires a separately authorized test deployment.

## Local-only verification

Use the commands in the repository `README.md`. They require a locally supplied
Integration SDK JAR and do not contact VMware or VCF Operations systems.
The lifecycle tests bypass the SDK constructor using the factory's existing
test-only allocation idiom because appliance logging dependencies are absent.
They execute real `onConfigure`, SDK ResourceConfig and collector logic, not
platform startup. SDK descriptor loading is skipped without log4j-api;
descriptor DOM assertions and inspected SDK methods remain verified offline.
