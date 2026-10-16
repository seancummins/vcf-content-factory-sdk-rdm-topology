# VCF Content Factory RDM Topology POC — Reference

Generated from `describe.xml` and `resources.properties`.

## Adapter

| Field | Value |
|---|---|
| Adapter Kind | `vcfcf_rdm_topology_poc` |
| Tier | 2 (Java SDK) |
| Monitoring Interval | 5 minutes |
| License Required | No |

## Lab instance configuration

Runtime-required fixture setting: `fixture_json`, type `string`, `identType="2"`
(non-identifying), no default; optional in the combined descriptor. See
[configuration contract](docs/installing.md). Missing `execution_mode` preserves
fixture behavior. An explicit [read-only diagnostic](docs/same-kind-diagnostic.md)
adds optional endpoint/configuration fields and credential association, without
making credentials mandatory for fixture accounts. Generated
reference data is retained separately in `REFERENCE.generated.md` for review.

---

## Object Types

### RDM Mapping

**Identity tuple**: `vcenter_instance_uuid`, `vm_instance_uuid`,
`virtual_disk_key` (all uniqueness-bearing). Unchanged for version-1/2 fixtures.

### Canonical LUN

Version-2 only: one unique `identity_token`, `scsi-vpd83:naa:<full-lowercase-payload>`.
No VM, host, vCenter, disk key or adapter-instance scope. See the
[qualification contract](docs/canonical-lun-fixture.md). Local mapping → LUN
edges use explicit additive SDK deltas, not foreign-resource stitching.

---
