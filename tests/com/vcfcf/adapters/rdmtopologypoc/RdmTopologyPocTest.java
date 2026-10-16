package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.Relationships;
import com.integrien.alive.common.adapter3.ResourceKey;
import com.integrien.alive.common.adapter3.config.ResourceIdentifierConfig;
import com.integrien.alive.common.adapter3.describe.ResourceIdentifierDescribe;
import com.integrien.alive.common.adapter3.describe.ResourceAttributeDescribe;
import com.integrien.alive.common.adapter3.describe.ResourceGroupDescribe;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Dependency-free focused tests; run with scripts/run-tests.sh. */
public final class RdmTopologyPocTest {

    private RdmTopologyPocTest() { }

    public static void main(String[] args) {
        System.out.println("Descriptor loader tests execute actual SDK with TEST-ONLY no-op logging linkage; not platform installation proof");
        preservesForeignVmIdentifierFlags();
        scopesSameMoidToOwningVcenter();
        repeatedCollectionIsPayloadIdempotent();
        rejectsUnresolvedForeignVm();
        createsCorrectLocalMappingIdentity();
        neverMutatesVmwareTopology();
        declaresCredentialFreeAdapterInstance();
        declaresLegalCanonicalPropertyHierarchy();
        RuntimeFixtureTest.run();
        CanonicalLunTest.run();
        System.out.println("RdmTopologyPocTest: PASS (8 topology/descriptor tests + runtime fixture matrix)");
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

    private static void declaresCredentialFreeAdapterInstance() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(describeXml());
            NodeList credentialKindContainers = document.getElementsByTagNameNS(
                    "http://schemas.vmware.com/vcops/schema", "CredentialKinds");
            check(credentialKindContainers.getLength() == 1,
                    "one optional diagnostic credential kinds container declared");
            NodeList credentialKinds = document.getElementsByTagNameNS(
                    "http://schemas.vmware.com/vcops/schema", "CredentialKind");
            check(credentialKinds.getLength() == 1, "one diagnostic credential kind declared");

            NodeList resourceKinds = document.getElementsByTagNameNS(
                    "http://schemas.vmware.com/vcops/schema", "ResourceKind");
            for (int index = 0; index < resourceKinds.getLength(); index++) {
                Element resourceKind = (Element) resourceKinds.item(index);
                if (RdmTopologyPocAdapter.ADAPTER_KIND.equals(resourceKind.getAttribute("key"))) {
                    check("7".equals(resourceKind.getAttribute("type")),
                            "adapter instance resource kind");
                    check("rdm_vcenter_readonly".equals(resourceKind.getAttribute("credentialKind")),
                            "optional diagnostic credential kind reference");
                    check("false".equals(resourceKind.getAttribute("isCredentialRequired")),
                            "credential-free instance declaration");
                    NodeList settings = resourceKind.getElementsByTagNameNS(
                            "http://schemas.vmware.com/vcops/schema", "ResourceIdentifier");
                    check(settings.getLength() == 12, "union configuration settings");
                    Element declaration = (Element) settings.item(0);
                    check("fixture_json".equals(declaration.getAttribute("key"))
                            && "string".equals(declaration.getAttribute("type"))
                            && "2".equals(declaration.getAttribute("identType"))
                            && "false".equals(declaration.getAttribute("required")),
                            "non-identifying conditionally required string configuration");
                    try {
                        Class.forName("org.apache.logging.log4j.LogManager");
                        ResourceIdentifierDescribe setting = new ResourceIdentifierDescribe();
                        check(setting.load(settings.item(0)), "actual SDK descriptor loader accepts fixture setting");
                        check("string".equals(setting.getType()) && Integer.valueOf(2).equals(setting.getIdentType())
                                && !setting.isRequired(), "SDK conditionally required configuration contract");
                    } catch (ClassNotFoundException unavailable) {
                        System.out.println("SKIP: SDK descriptor load requires unavailable log4j-api; DOM contract checked");
                    }
                    return;
                }
            }
            throw new AssertionError("missing adapter instance resource kind");
        } catch (Exception error) {
            throw new AssertionError("cannot parse describe.xml", error);
        }
    }

    private static void declaresLegalCanonicalPropertyHierarchy() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(describeXml());
            NodeList kinds = document.getElementsByTagNameNS("*", "ResourceKind");
            Element lun = null;
            for (int i = 0; i < kinds.getLength(); i++) {
                Element kind = (Element) kinds.item(i);
                if ("CanonicalLun".equals(kind.getAttribute("key"))) lun = kind;
            }
            check(lun != null, "CanonicalLun descriptor exists");
            NodeList groups = lun.getElementsByTagNameNS("*", "ResourceGroup");
            check(groups.getLength() == 2, "explicit identity and storage groups");
            Set<String> paths = new HashSet<>();
            for (int i = 0; i < groups.getLength(); i++) {
                Element group = (Element) groups.item(i);
                check(group.getParentNode() == lun && "false".equals(group.getAttribute("instanced")),
                        "direct noninstanced group");
                String prefix = group.getAttribute("key");
                check(!prefix.contains("|") && !prefix.contains(":"), "legal SDK group key");
                NodeList leaves = group.getElementsByTagNameNS("*", "ResourceAttribute");
                for (int j = 0; j < leaves.getLength(); j++) {
                    Element leaf = (Element) leaves.item(j);
                    String key = leaf.getAttribute("key");
                    check(!key.contains("|") && !key.contains(":"), "legal SDK attribute key");
                    check("true".equals(leaf.getAttribute("isProperty")), "declared as property");
                    check(paths.add(prefix + "|" + key), "unique full property path");
                }
                try {
                    Class.forName("org.apache.logging.log4j.LogManager");
                    ResourceGroupDescribe actual = new ResourceGroupDescribe();
                    check(actual.load(group), "actual SDK loads group and all child attributes");
                } catch (ClassNotFoundException unavailable) {
                    System.out.println("SKIP: actual SDK group load needs log4j-api; DOM path parity checked");
                }
            }
            check(paths.equals(Set.of("identity|format", "identity|canonical_name", "identity|provenance",
                    "identity|assessment", "storage|vendor", "storage|model", "storage|revision",
                    "storage|serial_number", "storage|lun_type", "storage|device_type", "storage|capacity_bytes")),
                    "all eleven full emission paths unchanged");
            String complete = CanonicalLunTest.LUN.replace("\"vendor\":", "\"revision\":\"synthetic\","
                    + "\"serial_number\":\"synthetic\",\"lun_type\":\"disk\",\"device_type\":\"disk\",\"vendor\":");
            RuntimeFixture fixture = RuntimeFixture.parse(CanonicalLunTest.v2(
                    CanonicalLunTest.attachment(RuntimeFixtureTest.JSON, complete)));
            check(paths.equals(fixture.lun.properties.keySet()), "descriptor full paths exactly match Java emissions");
            try {
                Class.forName("org.apache.logging.log4j.LogManager");
                Element bad = document.createElementNS("http://schemas.vmware.com/vcops/schema", "ResourceAttribute");
                bad.setAttribute("nameKey", "22"); bad.setAttribute("dataType", "string");
                Set<String> invalidKeys = new HashSet<>(paths);
                invalidKeys.add("identity:format");
                for (String key : invalidKeys) {
                    bad.setAttribute("key", key);
                    check(!new ResourceAttributeDescribe().load(bad), "actual SDK rejects illegal individual attribute key");
                }
                Element badGroup = (Element) groups.item(0).cloneNode(true);
                for (String key : List.of("identity|group", "identity:group")) {
                    badGroup.setAttribute("key", key);
                    check(!new ResourceGroupDescribe().load(badGroup), "actual SDK rejects illegal individual group key");
                }
            } catch (ClassNotFoundException unavailable) {
                System.out.println("SKIP: actual SDK negative loader needs log4j-api; exact rule checked by factory tests");
            }
        } catch (Exception error) { throw new AssertionError("CanonicalLun descriptor/emission mismatch", error); }
    }

    private static File describeXml() {
        File local = new File("describe.xml");
        if (local.isFile()) {
            return local;
        }
        File factoryRoot = new File("content/sdk-adapters/rdm-topology-poc/describe.xml");
        if (factoryRoot.isFile()) {
            return factoryRoot;
        }
        throw new AssertionError("describe.xml is not available from the test working directory");
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
