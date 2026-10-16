# RDM Topology SDK POC

This is a deterministic-fixture proof of the Java Integration SDK mechanics
for the topology (version-1 fixtures retain only the initial edge):

```text
VMWARE::VirtualMachine -> RdmMapping -> CanonicalLun
```

Default fixture collection never opens a network connection, reads vCenter or
calls the Suite API. A separately opted-in [read-only diagnostic](docs/same-kind-diagnostic.md)
can read scoped vCenter/Operations data but emits no inventory, relationships,
metrics or properties. Version-2 fixtures create a pack-owned
`CanonicalLun` after full LU-associated NAA5/6 descriptor qualification.
Both edges are additive; no replacement/removal calls are emitted.

The VM key preserves Suite API identifier names, values, and uniqueness flags.
It requires `VMEntityObjectID` and `VMEntityVCID` to be marked unique, so a
bare MOID or VM display name cannot form an edge.

Build 5 replaces compiled runtime identities with the required ordinary
adapter-instance setting `fixture_json` (string, nonunique `identType="2"`).
There is no fallback fixture. Operations supplies this setting with the
instance configuration; no collector-node files or environment variables are
read by the adapter. See [configuration and limitations](docs/installing.md).
Build 7 adds the explicitly versioned [CanonicalLun fixture contract](docs/canonical-lun-fixture.md).
One instance is the inventory authority; cross-instance deduplication is unproven.

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
