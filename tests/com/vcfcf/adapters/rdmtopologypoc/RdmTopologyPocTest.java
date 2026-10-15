package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.Relationships;
import com.integrien.alive.common.adapter3.ResourceKey;
import com.integrien.alive.common.adapter3.config.ResourceIdentifierConfig;

import java.util.List;

/** Dependency-free focused tests; run with scripts/run-tests.sh. */
public final class RdmTopologyPocTest {

    private RdmTopologyPocTest() { }

    public static void main(String[] args) {
        preservesForeignVmIdentifierFlags();
        scopesSameMoidToOwningVcenter();
        repeatedCollectionIsPayloadIdempotent();
        rejectsUnresolvedForeignVm();
        createsCorrectLocalMappingIdentity();
        neverMutatesVmwareTopology();
        System.out.println("RdmTopologyPocTest: PASS (6 tests)");
    }

    private static void preservesForeignVmIdentifierFlags() {
        ForeignVmIdentity vm = new ForeignVmIdentity(List.of(
                new ForeignVmIdentity.Identifier("VMEntityObjectID", "vm-71", true),
                new ForeignVmIdentity.Identifier("VMEntityVCID", "vc-a", true),
                new ForeignVmIdentity.Identifier("VMEntityName", "duplicate-name", false)));
        ResourceKey key = vm.toResourceKey("display-only");
        check("VMWARE".equals(key.getAdapterKind()), "foreign adapter kind");
        check("VirtualMachine".equals(key.getResourceKind()), "foreign resource kind");
        checkIdentifier(key, "VMEntityObjectID", "vm-71", true);
        checkIdentifier(key, "VMEntityVCID", "vc-a", true);
        checkIdentifier(key, "VMEntityName", "duplicate-name", false);
    }

    private static void scopesSameMoidToOwningVcenter() {
        ForeignVmIdentity inVcenterA = fixtureVm("vm-71", "vc-a");
        ForeignVmIdentity inVcenterB = fixtureVm("vm-71", "vc-b");
        ResourceKey keyA = inVcenterA.toResourceKey("same-name");
        ResourceKey keyB = inVcenterB.toResourceKey("same-name");
        check(!keyA.equals(keyB), "same MOID across vCenters must not share a key");
        checkIdentifier(keyA, "VMEntityVCID", "vc-a", true);
        checkIdentifier(keyB, "VMEntityVCID", "vc-b", true);
    }

    private static void repeatedCollectionIsPayloadIdempotent() {
        Relationships first = RdmTopologyRelationships.build(
                RdmTopologyPocAdapter.ADAPTER_KIND, RdmTopologyFixture.foreignVm(),
                "fixture-rdm-vm", RdmTopologyFixture.mapping());
        Relationships second = RdmTopologyRelationships.build(
                RdmTopologyPocAdapter.ADAPTER_KIND, RdmTopologyFixture.foreignVm(),
                "fixture-rdm-vm", RdmTopologyFixture.mapping());
        Relationships.RelationshipItem firstItem = onlyItem(first);
        Relationships.RelationshipItem secondItem = onlyItem(second);
        check(firstItem.getParent().equals(secondItem.getParent()),
                "repeated collection foreign parent payload");
        check(firstItem.getChildren().equals(secondItem.getChildren()),
                "repeated collection local child payload");
        check(firstItem.isAdd() && secondItem.isAdd(), "repeated collection is additive");
    }

    private static void rejectsUnresolvedForeignVm() {
        expectIllegalArgument(() -> new ForeignVmIdentity(List.of(
                new ForeignVmIdentity.Identifier("VMEntityObjectID", "vm-71", true),
                new ForeignVmIdentity.Identifier("VMEntityVCID", "vc-a", false))),
                "missing uniqueness-bearing vCenter identity");
        expectIllegalArgument(() -> RdmTopologyRelationships.build(
                RdmTopologyPocAdapter.ADAPTER_KIND, null, "display",
                RdmTopologyFixture.mapping()), "unresolved foreign VM");
    }

    private static void createsCorrectLocalMappingIdentity() {
        ResourceKey key = RdmTopologyFixture.mapping().toResourceKey(
                RdmTopologyPocAdapter.ADAPTER_KIND);
        check(RdmTopologyPocAdapter.ADAPTER_KIND.equals(key.getAdapterKind()),
                "local adapter kind");
        check("RdmMapping".equals(key.getResourceKind()), "local resource kind");
        checkIdentifier(key, "vcenter_instance_uuid",
                "11111111-1111-1111-1111-111111111111", true);
        checkIdentifier(key, "vm_instance_uuid",
                "22222222-2222-2222-2222-222222222222", true);
        checkIdentifier(key, "virtual_disk_key", "2000", true);
    }

    private static void neverMutatesVmwareTopology() {
        Relationships.RelationshipItem edge = onlyItem(RdmTopologyRelationships.build(
                RdmTopologyPocAdapter.ADAPTER_KIND, RdmTopologyFixture.foreignVm(),
                "fixture-rdm-vm", RdmTopologyFixture.mapping()));
        check("VMWARE".equals(edge.getParent().getAdapterKind()),
                "VMWARE is relationship parent only");
        check(edge.isAdd(), "foreign relationship must be additive");
        check(!edge.isClearFirst(), "foreign relationship must not clear VMWARE children");
        check(edge.getChildren().size() == 1, "one local mapping child");
        ResourceKey child = edge.getChildren().iterator().next();
        check(RdmTopologyPocAdapter.ADAPTER_KIND.equals(child.getAdapterKind()),
                "child is pack-owned, not VMWARE-owned");
    }

    private static ForeignVmIdentity fixtureVm(String moid, String vcenterId) {
        return new ForeignVmIdentity(List.of(
                new ForeignVmIdentity.Identifier("VMEntityObjectID", moid, true),
                new ForeignVmIdentity.Identifier("VMEntityVCID", vcenterId, true)));
    }

    private static Relationships.RelationshipItem onlyItem(Relationships relationships) {
        check(relationships.getRelationshipItems().size() == 1, "exactly one relationship");
        return relationships.getRelationshipItems().iterator().next();
    }

    private static void checkIdentifier(ResourceKey key, String name, String value,
            boolean unique) {
        for (ResourceIdentifierConfig identifier : key.getIdentifiers()) {
            if (name.equals(identifier.getKey())) {
                check(value.equals(identifier.getValue()), name + " value");
                check(unique == identifier.isPartOfUniqueness(), name + " uniqueness");
                return;
            }
        }
        throw new AssertionError("missing identifier " + name);
    }

    private static void expectIllegalArgument(Runnable action, String context) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException: " + context);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
