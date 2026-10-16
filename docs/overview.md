# Overview — VCF Content Factory RDM Topology POC

## What's in the Pack

VCF Content Factory RDM Topology POC. For the version, see [docs/README.md](README.md) (regenerated on every build) or the GitHub release.

Default fixture proof of additive `VMWARE::VirtualMachine → RdmMapping → CanonicalLun`
topology. Version-1 fixtures remain mapping-only. Optional same-kind
[read-only diagnostics](same-kind-diagnostic.md) read scoped vSphere/Suite API
data without publication. There is no production collector, storage-array
dependency, resource deletion or production lifecycle behavior.

### Resource Kinds (2)

- **RDM Mapping** (`RdmMapping`)
- **Canonical LUN** (`CanonicalLun`), globally shaped qualified NAA key;
  single-authority development POC, not empirical global deduplication proof.

## Cross-Adapter Notes

Foreign VM parent retains its exact Suite API-derived key and additive semantics.
Both local resources belong to this adapter; local mapping→LUN uses
`parent(mapping,lun).buildDelta(false)`, never `childForeign` or full-set replacement.

## Known Limitations

See [fixture contract and limitations](canonical-lun-fixture.md). Local-edge
persistence, shared-LUN convergence in Operations and cleanup are not live-tested.
