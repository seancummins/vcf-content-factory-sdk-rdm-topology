# RDM Topology SDK POC

This is a deterministic-fixture proof of the Java Integration SDK mechanics
for the initial edge:

```text
VMWARE::VirtualMachine -> RdmMapping
```

It never opens a network connection, reads vCenter, calls the Suite API, emits
metrics, or creates `CanonicalLun`. The only fixture relation is additive:
the foreign VM is the parent and the pack-owned `RdmMapping` is the child.

The VM key preserves Suite API identifier names, values, and uniqueness flags.
It requires `VMEntityObjectID` and `VMEntityVCID` to be marked unique, so a
bare MOID or VM display name cannot form an edge.

Local validation (the SDK JAR stays outside this repository):

```bash
VCFCF_SDK_JAR=/home/scummins/Documents/vmware-sdk/vrops-adapters-sdk-2.2.jar \
  PYTHONPATH=src python3 -m vcfcf_managementpacks validate-sdk content/sdk-adapters/rdm-topology-poc
VCFCF_SDK_JAR=/home/scummins/Documents/vmware-sdk/vrops-adapters-sdk-2.2.jar \
  bash content/sdk-adapters/rdm-topology-poc/scripts/run-tests.sh
```

Compilation and a PAK build do not prove that Operations will retain or render
the foreign edge. A separately approved deployment experiment is required.

Protected CI candidate preparation, approval pins and external controls are
documented in [ci/README.md](ci/README.md). Release automation remains disabled
pending the listed administrator and release-owner approvals.
