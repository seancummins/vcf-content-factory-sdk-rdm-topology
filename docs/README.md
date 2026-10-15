# VCF Content Factory RDM Topology POC — Documentation

> Generated index. The SVG diagram and per-kind table are regenerated on every
> build; prose sections (overview, installing) are hand-curated.

## Contents

| Section | Description |
|---------|-------------|
| [Overview](overview.md) | What's in the pack, resource kinds, cross-adapter notes |
| [Installing & Configuring](installing.md) | Prerequisites, configuration fields, step-by-step guide |
| [Inventory Tree](inventory-tree.md) | Traversal spec, per-kind table with identifying keys |
| [Metrics Reference](../REFERENCE.md) | Full metrics and properties reference (generated) |

## Inventory Tree

![Inventory Tree](inventory-tree.svg)

## Cross-MP Relationships

These edges are created at collection time via the Suite API and never appear in `describe.xml` — they are declared explicitly in `adapter.yaml` (`cross_mp_edges`) so this generated docset doesn't silently omit them. *Italic* endpoints belong to a foreign management pack; `code` endpoints are owned by this adapter.

| Parent | Child | Description |
|--------|-------|-------------|
| *VMWARE VirtualMachine* (foreign, VMWARE) | `RdmMapping` | Fixture-only additive relationship. The foreign VM ResourceKey preserves the Suite API-derived identifier names, values, and uniqueness flags; the POC does not create or modify VMWARE resources. |

## Quick Reference

- **Adapter kind:** `vcfcf_rdm_topology_poc`
- **Version:** 0.0.0.6
- **Traversal spec:** (none)
- **Resource kinds:** 1
- **Cross-MP relationships:** 1 (see [Cross-MP Relationships](#cross-mp-relationships) below)
