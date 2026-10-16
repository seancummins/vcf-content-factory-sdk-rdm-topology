# Build 9 — CanonicalLun fixture contract

This is a credential-free, single-inventory-authority development POC. It makes
no API calls, reads no node-local files/environment, and performs no resource or
relationship deletion. A fixture asserts provenance; offline validation verifies
its internal consistency, not truthful acquisition or current live inventory.
Only use approved read-only evidence. No production/global-deduplication claim.

## Compatible versioning

Build-6 unversioned `{foreign_vm,mapping}` input is unchanged: one mapping and
its existing additive foreign VM parent, **no CanonicalLun**. Build 7 adds:

```json
{
  "schema_version": "2",
  "attachments": [{
    "foreign_vm": {
      "adapterKindKey": "VMWARE", "resourceKindKey": "VirtualMachine", "name": "synthetic-vm",
      "resourceIdentifiers": [
        {"identifierType":{"name":"VMEntityObjectID","isPartOfUniqueness":true},"value":"vm-1001"},
        {"identifierType":{"name":"VMEntityVCID","isPartOfUniqueness":true},"value":"11111111-1111-4111-8111-111111111111"}
      ]
    },
    "mapping": {"vcenter_instance_uuid":"11111111-1111-4111-8111-111111111111","vm_instance_uuid":"22222222-2222-4222-8222-222222222222","virtual_disk_key":"2000"},
    "canonical_lun": {
      "provenance":"vsphere-soap-fixture",
      "backing_lun_uuid":"synthetic-opaque-scsi-uuid", "scsi_lun_uuid":"synthetic-opaque-scsi-uuid",
      "canonical_name":"naa.60000000000000000000000000000001",
      "storage_reference":"0x60000000000000000000000000000001",
      "descriptors":[{"namespace":"naa","namespace_id":"1","data_hex":"0103001060000000000000000000000000000001"}],
      "properties":{"vendor":"synthetic-vendor","model":"synthetic-model","capacity_blocks":"1024","capacity_block_size":"512"}
    }
  }]
}
```

All values are synthetic. Preserve the **complete actual** foreign ResourceKey,
including every identifier, exact value (explicit nonunique empty strings allowed)
and boolean flag. Existing scoped VM validation remains unchanged. Neither a
name nor a bare MOID proves VM identity. The mapping tuple must be proven by
the same scoped VM observation used to qualify its current-host RDM LUN.

Root, attachment and descriptor fields are exact; unknown/duplicate fields fail.
Maximum 8 attachments, subject also to existing 32,768-character/512-value,
4,096-character-string/depth-12 bounds; 8 large attachments may exceed them.
Reject duplicate mapping keys, conflicting scoped VM instance UUIDs, opaque SCSI
UUIDs mapping to different tokens, or incompatible descriptive properties for
one token. Complete validation finishes before emitting any resources/edges.

## Qualification and properties

`backing_lun_uuid == scsi_lun_uuid` is exact opaque equality, never UUID-to-NAA
conversion. `canonical_name` must be a complete `naa.` NAA5/6; `storage_reference`
a complete `0x` NAA5/6. Only prefix/hexadecimal case is presentation-insensitive.
Each supplied descriptor must have NAA namespace, a signed/unsigned VMware byte
`namespace_id` (-128..255 decimal string, not designator type), and full even
hex bytes including VPD83 header. Do not omit contradictory/malformed NAA claims.
Non-NAA descriptors cannot supply identity; retain them only in private original
evidence, not this narrowed schema.

Header checks preserve binary code set 1, designator type 3, reserved bits zero,
exact declared length and LU association 0. Complete port/target associations
1/2 are not LU identity; reserved 3 fails. Require exactly one distinct eligible
NAA5 (8-byte) or NAA6 (16-byte) payload agreeing completely with canonical and
operator reference. Equivalent duplicate descriptors collapse; conflicting
eligible payloads fail. Canonical-name similarity or a `qualified:true` flag
cannot replace descriptor proof.

Only optional verified vSphere fields are allowed in `properties`: vendor,
model, revision, serial_number, lun_type, device_type, capacity_blocks and
capacity_block_size. All are strings; capacity dimensions must both be present,
canonical positive decimal integers with signed-64-bit product bounds. No path,
host display name, array volume/pool name or array endpoint becomes a global
property. Missing descriptions may be omitted; malformed supplied values fail.
Repeated same-token descriptions require exact consensus, including presence;
no winner or silent merge. Properties are declared string `isProperty=true` and
emitted only on the unique owned LUN with `identity|provenance=vsphere-soap-fixture`.

Build 9 corrects descriptor compatibility: `identity` and `storage` are explicit,
noninstanced `ResourceGroup` elements, and each `ResourceAttribute` declares only
its leaf key (for example `format`). The actual SDK rejects `|` and `:` in an
individual attribute or group key. Full emitted `MetricKey` paths remain
`identity|format`, `storage|vendor`, and the other existing group/leaf paths;
fixture schema, resource identities and additive relationships are unchanged.
The offline tests execute the supplied SDK's actual attribute/group loaders on
the corrected hierarchy and reject all eleven original flattened keys plus
colon-bearing keys. A test-only no-op Log4j API supplies logging linkage only;
it is excluded from the PAK and is not evidence of a full Operations install.
Opaque UUID is retained for validation but is not the global key or published alias.

## Resource and edge output

`CanonicalLun` has one unique identifier:
`identity_token=scsi-vpd83:naa:<full-lowercase-payload>`; no VM, disk, host,
vCenter or instance identifier. Within this fixture authority, emit each owned
key once. Two mappings can share one LUN; distinct payloads cannot merge.
Foreign VM→mapping retains `parentForeign(...).build()`; local mapping→LUN uses
`parent(...).buildDelta(false)`. No clears, removals or VMware-owned resources
are emitted. Repeated input yields stable resource/edge payloads.

## Secure operator preparation and deployment boundary

Use the factory's offline fixture-preparation procedure provided with E3.5 to
combine the ignored build-6 VM fixture, E3.4 private SOAP evidence and independent
storage reference. It must verify scoped VM/mapping correspondence and complete
qualification, then run the actual Java parser before writing an exclusive
ignored mode-0600 output. Do not hand-copy identifiers from fingerprints, print
payloads or place actual lab evidence in this repository/PAK/docs/logs.

Supply the resulting JSON through the existing Operations-managed `fixture_json`
instance setting only after separate installation/reconfiguration approval.
Configuration is cluster-independent in design; collector failover is untested.
Do not retarget a running instance: obsolete edges/resources are not reconciled.
Preserve the existing E2 VM parent and capture baseline topology before changing
the lab. First live experiment must separately prove local-edge API/UI visibility,
one-LUN convergence, repeated collection and later VMware-cycle persistence,
preserved VMware topology, and conservative owned-edge cleanup. Cross-host,
cross-vCenter, temporal identity stability and collision controls remain future
evidence, not claims of this fixture POC.
