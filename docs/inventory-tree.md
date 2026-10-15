# Inventory Tree — VCF Content Factory RDM Topology POC

> Generated from `describe.xml` v0.0.0.4. Do not edit — regenerated on every build.

## Traversal Tree



> \* = identifying (unique) key

## Resource Kinds Reference

| Kind | Display Label | Identifying Keys | Parent(s) |
|------|--------------|-----------------|-----------|
| `RdmMapping` | RDM Mapping | `vcenter_instance_uuid` *, `vm_instance_uuid` *, `virtual_disk_key` * | — |

## Cross-MP Relationships

These edges are created at collection time via the Suite API and never appear in `describe.xml` — they are declared explicitly in `adapter.yaml` (`cross_mp_edges`) so this generated docset doesn't silently omit them. *Italic* endpoints belong to a foreign management pack; `code` endpoints are owned by this adapter.

| Parent | Child | Description |
|--------|-------|-------------|
| *VMWARE VirtualMachine* (foreign, VMWARE) | `RdmMapping` | Fixture-only additive relationship. The foreign VM ResourceKey preserves the Suite API-derived identifier names, values, and uniqueness flags; the POC does not create or modify VMWARE resources. |
