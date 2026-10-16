package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.Relationships;
import com.integrien.alive.common.adapter3.ResourceKey;
import com.integrien.alive.common.adapter3.config.ResourceConfig;
import com.integrien.alive.common.adapter3.config.ResourceIdentifierConfig;
import com.vcfcf.adapter.VcfCfAdapter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Uses real SDK configs and the adapter lifecycle; no platform calls. */
final class RuntimeFixtureTest {
    static final String VC = "11111111-1111-1111-1111-111111111111";
    static final String VM = "22222222-2222-2222-2222-222222222222";
    static final String JSON = "{\"foreign_vm\":{\"adapterKindKey\":\"VMWARE\","
            + "\"resourceKindKey\":\"VirtualMachine\",\"name\":\" synthetic display \","
            + "\"resourceIdentifiers\":[{\"identifierType\":{\"name\":\"VMEntityObjectID\","
            + "\"isPartOfUniqueness\":true},\"value\":\"vm-71\"},"
            + "{\"identifierType\":{\"name\":\"VMEntityVCID\",\"isPartOfUniqueness\":true},"
            + "\"value\":\"" + VC + "\"},{\"identifierType\":{\"name\":\"VMEntityName\","
            + "\"isPartOfUniqueness\":false},\"value\":\" same name \"}]},"
            + "\"mapping\":{\"vcenter_instance_uuid\":\"" + VC + "\","
            + "\"vm_instance_uuid\":\"" + VM + "\",\"virtual_disk_key\":\"2000\"}}";

    static void run() {
        sdkIdentifierValueContract();
        List<String> invalid = new ArrayList<>();
        invalid.add(null); invalid.add(""); invalid.add("{}"); invalid.add("[]");
        invalid.add(JSON + " trailing"); invalid.add(JSON.substring(0, JSON.length()-1));
        invalid.add(JSON.replace("\"foreign_vm\":", "\"mapping\":{},\"foreign_vm\":"));
        invalid.add(JSON.replace("\"isPartOfUniqueness\":true", "\"isPartOfUniqueness\":\"true\""));
        invalid.add(JSON.replace("\"isPartOfUniqueness\":true", "\"isPartOfUniqueness\":false"));
        invalid.add(JSON.replace("\"isPartOfUniqueness\":true", "\"wrong\":true"));
        invalid.add(JSON.replace("VMEntityVCID", "missingScopedIdentity"));
        invalid.add(JSON.replace("VMEntityObjectID", "missingMoid"));
        invalid.add(JSON.replace("VMWARE", "OTHER"));
        invalid.add(JSON.replace("VirtualMachine", "HostSystem"));
        invalid.add(JSON.replace("\"vm-71\"", "\"71\""));
        invalid.add(JSON.replace("\"vm-71\"", "\" vm-71\""));
        invalid.add(JSON.replace("\"virtual_disk_key\":\"2000\"", "\"virtual_disk_key\":2000"));
        for (String disk : List.of("0", "-1", "02000", "1.0", "2147483648", "x", "")) {
            invalid.add(JSON.replace("\"2000\"", "\"" + disk + "\""));
        }
        invalid.add(JSON.replace("\"vcenter_instance_uuid\":\"" + VC, "\"vcenter_instance_uuid\":\"" + VM));
        invalid.add(JSON.replace("VMEntityName", "VMEntityVCID"));
        invalid.add(JSON.replace("VMEntityName", "vmentityvcid"));
        invalid.add(JSON.replace("VMEntityName", "VMEntityInstanceUUID"));
        invalid.add(JSON.replace("VMEntityName", "VMEntityInstanceUUID").replace(" same name ", VC));
        invalid.add(JSON.replace(VC, "00000000-0000-0000-0000-000000000000"));
        invalid.add(JSON.replace(" same name ", "bad\\nvalue"));
        invalid.add(JSON.replace(" same name ", "bad\\uD800"));
        invalid.add(JSON.replace(" same name ", "bad\\x00"));
        // Empty is not null, absence, whitespace, or an identity-bearing value.
        invalid.add(JSON.replace("\"value\":\" same name \"", "\"value\":null"));
        invalid.add(JSON.replace(",\"value\":\" same name \"", ""));
        invalid.add(JSON.replace(" same name ", " "));
        invalid.add(JSON.replace(" same name ", "").replace("\"isPartOfUniqueness\":false", "\"isPartOfUniqueness\":true"));
        invalid.add(JSON.replace("\"value\":\"vm-71\"", "\"value\":\"\""));
        invalid.add(JSON.replace("\"value\":\"" + VC + "\"", "\"value\":\"\""));
        invalid.add("[".repeat(20) + "true" + "]".repeat(20));
        invalid.add(" ".repeat(32769));
        invalid.add(JSON.replace("\"mapping\":", "\"extra\":false,\"mapping\":"));
        for (String candidate : invalid) {
            RdmTopologyPocAdapter adapter = offlineAdapter();
            adapter.onConfigure(null, account(JSON));
            RuntimeFixture before = current(adapter);
            List<ResourceConfig> resources = new ArrayList<>();
            adapter.enumerateResources(resources::add);
            rejects(() -> adapter.onConfigure(null, account(candidate)));
            rejects(() -> adapter.enumerateResources(ignored -> { throw new AssertionError("emitted after invalid config"); }));
            rejects(() -> adapter.getCollector().collectRelationships(before, resources.get(0)));
        }
        RdmTopologyPocAdapter adapter = offlineAdapter();
        adapter.onConfigure(null, account(JSON));
        RuntimeFixture initial = current(adapter);
        check(initial.vmName.equals(" synthetic display "), "display name exact preservation");
        check(initial.vm.identifiers().get(2).value().equals(" same name "), "identifier exact preservation");
        check(!initial.vm.identifiers().get(2).partOfUniqueness(), "nonunique flag preserved");
        RuntimeFixture extraUnique = RuntimeFixture.parse(JSON.replace("\"isPartOfUniqueness\":false", "\"isPartOfUniqueness\":true"));
        check(extraUnique.vm.identifiers().get(2).partOfUniqueness(), "additional actual unique flag preserved");
        RuntimeFixture.parse(JSON.replace("VMEntityName", "VMEntityInstanceUUID").replace(" same name ", VM));
        List<ResourceConfig> resources = new ArrayList<>();
        adapter.enumerateResources(resources::add);
        check(resources.size() == 1, "one configured mapping");
        Relationships.RelationshipItem edge = adapter.getCollector().collectRelationships(initial, resources.get(0))
                .getRelationshipItems().iterator().next();
        check(edge.isAdd() && !edge.isClearFirst(), "additive configured relationship");
        adapter.onConfigure(null, account(JSON));
        Relationships.RelationshipItem again = adapter.getCollector().collectRelationships(current(adapter), resources.get(0))
                .getRelationshipItems().iterator().next();
        check(edge.getParent().equals(again.getParent()) && edge.getChildren().equals(again.getChildren()), "stable payload");
        rejects(() -> adapter.getCollector().collectRelationships(initial, resources.get(0)));
        rejects(() -> adapter.getCollector().collectRelationships(current(adapter),
                new ResourceConfig(new ResourceKey("wrong", "RdmMapping", "OTHER"))));
        adapter.onConfigure(null, account(JSON.replace(VC, "33333333-3333-3333-3333-333333333333")));
        rejects(() -> adapter.getCollector().collectRelationships(current(adapter), resources.get(0)));
        List<ResourceConfig> other = new ArrayList<>(); adapter.enumerateResources(other::add);
        check(!resources.get(0).getResourceKey().equals(other.get(0).getResourceKey()), "same MOID other vcenter scoped");
        ResourceConfig duplicate = account(JSON);
        duplicate.getResourceIdentifiers().add(new ResourceIdentifierConfig("fixture_json", "different", false));
        rejects(() -> adapter.onConfigure(null, duplicate));
        rejects(() -> adapter.onConfigure(null, (ResourceConfig) null));
        ResourceConfig identifying = new ResourceConfig(new ResourceKey("instance", RdmTopologyPocAdapter.ADAPTER_KIND,
                RdmTopologyPocAdapter.ADAPTER_KIND));
        identifying.addResourceIdentifier(new ResourceIdentifierConfig("fixture_json", JSON, true));
        rejects(() -> adapter.onConfigure(null, identifying));
        runtimeHasNoNodeLocalInputs();
        preservesSixIdentifierEmptyValues();
        System.out.println("RuntimeFixtureTest: PASS (" + invalid.size() + " invalid-input lifecycle cases + valid/idempotent/reconfiguration guards)");
    }

    private static void preservesSixIdentifierEmptyValues() {
        String extra = ",{\"identifierType\":{\"name\":\"zEmpty\",\"isPartOfUniqueness\":false},\"value\":\"\"}"
                + ",{\"identifierType\":{\"name\":\"aEmpty\",\"isPartOfUniqueness\":false},\"value\":\"\"}"
                + ",{\"identifierType\":{\"name\":\"OtherIdentity\",\"isPartOfUniqueness\":true},\"value\":\"exact other identity\"}";
        String input = JSON.replace("}]},\"mapping\"", "}" + extra + "]},\"mapping\"");
        RdmTopologyPocAdapter adapter = offlineAdapter();
        adapter.onConfigure(null, account(input));
        RuntimeFixture fixture = current(adapter);
        check(fixture.vm.identifiers().size() == 6, "six identifiers retained");
        check(fixture.vm.identifiers().get(3).name().equals("zEmpty")
                && fixture.vm.identifiers().get(4).name().equals("aEmpty"), "fixture input order retained");
        List<ResourceConfig> resources = new ArrayList<>(); adapter.enumerateResources(resources::add);
        check(resources.size() == 1, "one mapping with empty nonidentity values");
        Relationships.RelationshipItem first = adapter.getCollector().collectRelationships(fixture, resources.get(0))
                .getRelationshipItems().iterator().next();
        check(first.getParent().getIdentifiers().size() == 6, "complete foreign ResourceKey retained");
        for (ForeignVmIdentity.Identifier source : fixture.vm.identifiers()) {
            ResourceIdentifierConfig target = first.getParent().getIdentifiers().stream()
                    .filter(id -> id.getKey().equals(source.name())).findFirst().orElseThrow();
            check(source.value().equals(target.getValue())
                    && source.partOfUniqueness() == target.isPartOfUniqueness(), "exact field roundtrip");
        }
        check(first.isAdd() && !first.isClearFirst(), "empty-value relationship remains additive");
        adapter.onConfigure(null, account(input));
        Relationships.RelationshipItem second = adapter.getCollector().collectRelationships(current(adapter), resources.get(0))
                .getRelationshipItems().iterator().next();
        check(first.getParent().equals(second.getParent()) && first.getChildren().equals(second.getChildren()),
                "empty-value repeated collection stable");
        for (String value : new String[] {null, "", " "}) {
            rejects(() -> new ForeignVmIdentity.Identifier("OtherIdentity", value, true));
        }
        rejects(() -> new ForeignVmIdentity.Identifier("NonIdentity", null, false));
        rejects(() -> new ForeignVmIdentity.Identifier("NonIdentity", " ", false));
        System.out.println("Six-identifier empty-string exact roundtrip/repeated collection checks: PASS");
    }

    /** Exercise the actual supplied SDK, not a stub or inferred wire contract. */
    private static void sdkIdentifierValueContract() {
        ResourceIdentifierConfig nullEntry = new ResourceIdentifierConfig("null-entry", null, false);
        check(nullEntry.getValue() == null, "identifier config stores null distinctly");
        ResourceKey key = new ResourceKey("display", "VirtualMachine", "VMWARE");
        check(!key.addIdentifier(nullEntry), "ResourceKey refuses null value");
        check(key.getIdentifiers().isEmpty(), "null entry is not preserved by ResourceKey");
        check(key.addIdentifier(new ResourceIdentifierConfig("zEmpty", "", false)), "empty nonunique accepted");
        check(key.addIdentifier(new ResourceIdentifierConfig("aIdentity", "scope", true)), "unique value accepted");
        check(key.getIdentifiers().size() == 2 && key.getIdentifiers().get(1).getValue().equals(""),
                "empty entry retained exactly and SDK sorts identifiers");
        check(key.getIdentifiers().get(0).getKey().equals("aIdentity"), "SDK orders names ignoring case");
        check(!key.addIdentifier(new ResourceIdentifierConfig("ZEMPTY", "conflict", false)),
                "SDK rejects case-insensitive duplicate names");
        ResourceKey populated = new ResourceKey("other display", "VirtualMachine", "VMWARE");
        populated.addIdentifier(new ResourceIdentifierConfig("aIdentity", "scope", true));
        populated.addIdentifier(new ResourceIdentifierConfig("zEmpty", "populated", false));
        check(key.equals(populated), "ResourceKey matching ignores nonunique value difference");
        ResourceKey omitted = new ResourceKey("display", "VirtualMachine", "VMWARE");
        omitted.addIdentifier(new ResourceIdentifierConfig("aIdentity", "scope", true));
        check(key.equals(omitted), "matching ignores nonunique absence but representation differs");
        check(key.getIdentifiers().size() != omitted.getIdentifiers().size(), "never infer representation equivalence");
        System.out.println("Actual SDK identifier value/storage/order/matching checks: PASS");
    }

    private static void runtimeHasNoNodeLocalInputs() {
        java.nio.file.Path source = java.nio.file.Path.of("src/com/vcfcf/adapters/rdmtopologypoc");
        if (!java.nio.file.Files.isDirectory(source)) {
            source = java.nio.file.Path.of("content/sdk-adapters/rdm-topology-poc").resolve(source);
        }
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(source)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = java.nio.file.Files.readString(path);
                    for (String forbidden : List.of("java.io.", "java.nio.file.", "java.net.",
                            "System.getenv(", "System.getProperty(", "RdmTopologyFixture")) {
                        check(!content.contains(forbidden), "no runtime file/env/network/compiled fixture dependency");
                    }
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
            });
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }

    static ResourceConfig account(String json) {
        ResourceConfig config = new ResourceConfig(new ResourceKey("lab account", RdmTopologyPocAdapter.ADAPTER_KIND,
                RdmTopologyPocAdapter.ADAPTER_KIND));
        if (json != null) config.addResourceIdentifier(new ResourceIdentifierConfig("fixture_json", json, false));
        return config;
    }

    static RuntimeFixture current(RdmTopologyPocAdapter adapter) {
        try {
            Field field = VcfCfAdapter.class.getDeclaredField("config"); field.setAccessible(true);
            return (RuntimeFixture) field.get(adapter);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** Existing factory test idiom: SDK constructors require appliance log4j-core.
     * Bypass ONLY constructor/platform initialization; execute real onConfigure,
     * real SDK ResourceConfig and collector logic. This is not a runtime boot test. */
    static RdmTopologyPocAdapter offlineAdapter() {
        try {
            Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            RdmTopologyPocAdapter adapter = (RdmTopologyPocAdapter) unsafe.getClass()
                    .getMethod("allocateInstance", Class.class).invoke(unsafe, RdmTopologyPocAdapter.class);
            Field abort = VcfCfAdapter.class.getDeclaredField("abortRequested");
            abort.setAccessible(true); abort.set(adapter, new java.util.concurrent.atomic.AtomicBoolean());
            Field logger = com.integrien.alive.common.adapter3.AdapterBase.class.getDeclaredField("adapterLoggerFactory");
            logger.setAccessible(true);
            Class<?> factory = Class.forName("com.vmware.vrops.logging.AdapterLoggerFactory");
            Object noLogger = java.lang.reflect.Proxy.newProxyInstance(factory.getClassLoader(),
                    new Class<?>[] {factory}, (proxy, method, arguments) -> null);
            logger.set(adapter, noLogger);
            return adapter;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void rejects(Runnable operation) {
        try { operation.run(); throw new AssertionError("invalid fixture accepted"); }
        catch (IllegalArgumentException | IllegalStateException expected) {
            check(expected.getCause() == null, "no sensitive nested exception");
            check(!expected.getMessage().contains(VC) && !expected.getMessage().contains("vm-71"), "sanitized error");
        }
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
