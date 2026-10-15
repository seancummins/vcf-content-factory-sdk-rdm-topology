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

Required ordinary setting: `fixture_json`, type `string`, `identType="2"`
(non-identifying), no default. See [configuration contract](docs/installing.md).
No endpoint, credential kind, or credential reference is declared. Generated
reference data is retained separately in `REFERENCE.generated.md` for review.

---

## Object Types

### RDM Mapping

**Identity tuple**: `vcenter_instance_uuid`, `vm_instance_uuid`,
`virtual_disk_key` (all uniqueness-bearing). No CanonicalLun is implemented.

---
