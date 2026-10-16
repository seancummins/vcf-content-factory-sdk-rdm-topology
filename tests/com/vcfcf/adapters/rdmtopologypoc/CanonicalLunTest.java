package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.MetricData;
import com.integrien.alive.common.adapter3.Relationships;
import com.integrien.alive.common.adapter3.ResourceKey;
import com.integrien.alive.common.adapter3.config.ResourceConfig;
import java.util.ArrayList;
import java.util.List;

/** Synthetic only. No lab values or appliance runtime assumptions. */
final class CanonicalLunTest {
    static final String NAA = "600000000000000000000000000000ab";
    static final String DESCRIPTOR = "01030010" + NAA;
    static final String LUN = "{\"provenance\":\"vsphere-soap-fixture\",\"backing_lun_uuid\":\"opaque-scsi-1\","
            + "\"scsi_lun_uuid\":\"opaque-scsi-1\",\"canonical_name\":\"naa." + NAA + "\","
            + "\"storage_reference\":\"0x" + NAA + "\",\"descriptors\":[{\"namespace\":\"naa\","
            + "\"namespace_id\":\"1\",\"data_hex\":\"" + DESCRIPTOR + "\"}],"
            + "\"properties\":{\"vendor\":\"synthetic vendor\",\"model\":\"synthetic model\","
            + "\"capacity_blocks\":\"1024\",\"capacity_block_size\":\"512\"}}";

    static String attachment(String v1, String lun) {
        return v1.substring(0, v1.length() - 1) + ",\"canonical_lun\":" + lun + "}";
    }
    static String v2(String... attachments) {
        return "{\"schema_version\":\"2\",\"attachments\":[" + String.join(",", attachments) + "]}";
    }

    static void run() {
        String first = attachment(RuntimeFixtureTest.JSON, LUN);
        String second = attachment(RuntimeFixtureTest.JSON.replace(RuntimeFixtureTest.VC,
                "33333333-3333-3333-3333-333333333333").replace(RuntimeFixtureTest.VM,
                "44444444-4444-4444-4444-444444444444"), LUN);
        check(RuntimeFixture.parse(RuntimeFixtureTest.JSON).lun == null, "v1 stays mapping-only");
        List<ResourceConfig> one = resources(v2(first));
        check(one.size() == 2, "one mapping and one LUN");
        List<ResourceConfig> shared = resources(v2(first, second));
        check(shared.size() == 3, "two scoped mappings converge on one emitted LUN");
        RuntimeFixture a = RuntimeFixture.parse(v2(first));
        RuntimeFixture b = RuntimeFixture.parse(v2(second));
        ResourceKey key = a.lun.toResourceKey(RdmTopologyPocAdapter.ADAPTER_KIND);
        check(key.equals(b.lun.toResourceKey(RdmTopologyPocAdapter.ADAPTER_KIND)), "global key excludes vCenter/VM");
        check(key.getIdentifiers().size() == 1 && key.getIdentifiers().get(0).isPartOfUniqueness()
                && key.getIdentifiers().get(0).getKey().equals("identity_token"), "sole global identifier");
        String different = LUN.replace(NAA, "60000000000000000000000000000002").replace("opaque-scsi-1", "opaque-scsi-2");
        check(resources(v2(first, attachment(RuntimeFixtureTest.JSON.replace("2000", "2001"), different))).size() == 4,
                "distinct NAA creates distinct LUN");
        check(!key.equals(RuntimeFixture.parse(v2(attachment(RuntimeFixtureTest.JSON, different)))
                .lun.toResourceKey(RdmTopologyPocAdapter.ADAPTER_KIND)), "distinct key");

        List<String> invalidLuns = new ArrayList<>();
        invalidLuns.add(LUN.replace("\"namespace\":\"naa\"", "\"namespace\":\"unknown\""));
        invalidLuns.add(LUN.replace("vsphere-soap-fixture", "qualified"));
        invalidLuns.add(LUN.replace("\"backing_lun_uuid\":\"opaque-scsi-1\"", "\"backing_lun_uuid\":\"different\""));
        invalidLuns.add(LUN.replace("\"namespace_id\":\"1\"", "\"namespace_id\":true"));
        for (String ns : List.of("-129", "256", "", "x", "1.0"))
            invalidLuns.add(LUN.replace("\"namespace_id\":\"1\"", "\"namespace_id\":\"" + ns + "\""));
        for (String header : List.of("02030010", "01020010", "01130010", "01230010", "01330010",
                "01830010", "01430010", "01030110", "11030010", "0103000f"))
            invalidLuns.add(LUN.replace(DESCRIPTOR, header + NAA));
        invalidLuns.add(LUN.replace("\"data_hex\":\"" + DESCRIPTOR + "\"", "\"data_hex\":\"" + NAA + "\""));
        invalidLuns.add(LUN.replace("\"descriptors\":[", "\"descriptors\":[],\"unexpected\":["));
        invalidLuns.add(LUN.replace("\"canonical_name\":\"naa." + NAA, "\"canonical_name\":\"naa.60000000000000000000000000000002"));
        invalidLuns.add(LUN.replace("\"storage_reference\":\"0x" + NAA, "\"storage_reference\":\"0x60000000000000000000000000000002"));
        for (String dimension : List.of("0", "-1", "1.5", "01", "9223372036854775808"))
            invalidLuns.add(LUN.replace("\"1024\"", "\"" + dimension + "\""));
        invalidLuns.add(LUN.replace("\"1024\"", "\"9223372036854775807\""));
        invalidLuns.add(LUN.replace(",\"capacity_block_size\":\"512\"", ""));
        invalidLuns.add(LUN.replace("\"vendor\":", "\"array_endpoint\":"));
        invalidLuns.add(LUN.replace(NAA, "300000000000000000000000000000ab"));
        invalidLuns.add(LUN.replace(NAA, NAA + "00"));
        invalidLuns.add(LUN.replace("\"properties\":{", "\"host_moid\":\"synthetic-host\",\"properties\":{"));
        for (String bad : invalidLuns) rejects(v2(attachment(RuntimeFixtureTest.JSON, bad)));
        rejects(v2(first, first));
        rejects(v2(first, attachment(RuntimeFixtureTest.JSON.replace("2000", "2001"), different.replace("opaque-scsi-2", "opaque-scsi-1"))));
        rejects(v2(first, attachment(RuntimeFixtureTest.JSON.replace("2000", "2001"), LUN.replace("synthetic model", "conflicting model"))));
        rejects(v2(first, attachment(RuntimeFixtureTest.JSON.replace(RuntimeFixtureTest.VM,
                "44444444-4444-4444-4444-444444444444").replace("2000", "2001"), LUN)));
        rejects(v2(first, attachment(RuntimeFixtureTest.JSON.replace("vm-71", "vm-72").replace("2000", "2001"), LUN)));
        rejects(v2());
        rejects(v2(first).replace("\"2\"", "\"3\""));
        rejects(v2(first).replace("\"canonical_lun\":", "\"unqualified_lun\":"));

        // Equivalent complete byte representations and hex case, never arbitrary truncation.
        String upper = LUN.replace(NAA, NAA.toUpperCase(java.util.Locale.ROOT));
        check(RuntimeFixture.parse(v2(attachment(RuntimeFixtureTest.JSON, upper))).lun.toResourceKey("adapter").equals(
                a.lun.toResourceKey("adapter")), "approved hexadecimal-case equivalence");
        check(RuntimeFixture.parse(v2(attachment(RuntimeFixtureTest.JSON,
                upper.replace("naa.", "NAA.").replace("0x", "0X").replace("\"naa\"", "\"NAA\""))))
                .lun.toResourceKey("adapter").equals(a.lun.toResourceKey("adapter")), "documented prefix/namespace case equivalence");
        String naa5 = LUN.replace(DESCRIPTOR, "01030008" + "5000000000000001")
                .replace("naa." + NAA, "naa.5000000000000001").replace("0x" + NAA, "0x5000000000000001");
        check(RuntimeFixture.parse(v2(attachment(RuntimeFixtureTest.JSON, naa5))).lun.payload.length() == 16, "NAA5 supported");
        String duplicate = LUN.replace("}],\"properties\"", "},{\"namespace\":\"naa\",\"namespace_id\":\"-128\",\"data_hex\":\"" + DESCRIPTOR + "\"}],\"properties\"");
        check(RuntimeFixture.parse(v2(attachment(RuntimeFixtureTest.JSON, duplicate))).lun != null, "equivalent descriptors deduplicated");
        String portAlongside = duplicate.replace("\"namespace_id\":\"-128\",\"data_hex\":\"" + DESCRIPTOR,
                "\"namespace_id\":\"-128\",\"data_hex\":\"01130010" + NAA);
        check(RuntimeFixture.parse(v2(attachment(RuntimeFixtureTest.JSON, portAlongside))).lun != null,
                "valid port descriptor never substitutes for LU identity");
        rejects(v2(attachment(RuntimeFixtureTest.JSON, duplicate.replace("\"namespace_id\":\"-128\",\"data_hex\":\"" + DESCRIPTOR,
                "\"namespace_id\":\"-128\",\"data_hex\":\"0103001060000000000000000000000000000002"))));

        RdmTopologyPocAdapter adapter = RuntimeFixtureTest.offlineAdapter();
        adapter.onConfigure(null, RuntimeFixtureTest.account(v2(first, second)));
        RuntimeFixture current = RuntimeFixtureTest.current(adapter);
        List<ResourceConfig> targets = new ArrayList<>(); adapter.enumerateResources(targets::add);
        int edges = 0, propertySets = 0;
        for (ResourceConfig target : targets) {
            Relationships relationships = adapter.getCollector().collectRelationships(current, target);
            if (relationships != null) {
                Relationships repeated = adapter.getCollector().collectRelationships(current, target);
                check(repeated.getRelationshipItems().size() == relationships.getRelationshipItems().size(), "stable edge count");
                for (Relationships.RelationshipItem item : relationships.getRelationshipItems()) {
                    check(item.isAdd() && !item.isClearFirst(), "all edges additive, no removals/fullset");
                    check(item.getParent().getResourceKind().equals("VirtualMachine")
                            ? item.getParent().getAdapterKind().equals("VMWARE")
                                && item.getChildren().stream().allMatch(k -> k.getResourceKind().equals("RdmMapping"))
                            : item.getParent().getResourceKind().equals("RdmMapping")
                                && item.getChildren().stream().allMatch(k -> k.getResourceKind().equals("CanonicalLun")),
                            "exact foreign/local relationship directions");
                    edges += item.getChildren().size();
                    check(item.getChildren().stream().allMatch(k -> k.getAdapterKind().equals(RdmTopologyPocAdapter.ADAPTER_KIND)),
                            "never emits VMware-owned children");
                }
            }
            List<MetricData> output = new ArrayList<>();
            try { adapter.getCollector().collect(current, null, target, output, adapter); }
            catch (Exception error) { throw new AssertionError(error); }
            if (!output.isEmpty()) {
                propertySets++;
                check(output.stream().allMatch(p -> p.getMetricKey().isProperty()), "all descriptive samples are properties");
                check(output.stream().anyMatch(p -> "524288".equals(p.getStringValue())), "checked capacity product");
            }
        }
        check(edges == 4 && propertySets == 1, "two foreign edges, two local edges, one property emission");
        adapter.onConfigure(null, RuntimeFixtureTest.account(v2(first, second)));
        List<ResourceConfig> again = new ArrayList<>(); adapter.enumerateResources(again::add);
        check(targets.stream().map(ResourceConfig::getResourceKey).toList().equals(again.stream().map(ResourceConfig::getResourceKey).toList()),
                "repeated collection resources stable");
        System.out.println("CanonicalLunTest: PASS (" + invalidLuns.size() + " invalid-LUN cases + topology/convergence/conflict/idempotence matrix)");
    }

    private static List<ResourceConfig> resources(String fixture) {
        RdmTopologyPocAdapter adapter = RuntimeFixtureTest.offlineAdapter();
        adapter.onConfigure(null, RuntimeFixtureTest.account(fixture));
        List<ResourceConfig> result = new ArrayList<>(); adapter.enumerateResources(result::add); return result;
    }
    private static void rejects(String fixture) {
        try { resources(fixture); throw new AssertionError("invalid canonical fixture accepted"); }
        catch (IllegalArgumentException expected) {
            check(expected.getCause() == null && expected.getMessage().equals("Invalid runtime fixture configuration"), "sanitized rejection");
        }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
