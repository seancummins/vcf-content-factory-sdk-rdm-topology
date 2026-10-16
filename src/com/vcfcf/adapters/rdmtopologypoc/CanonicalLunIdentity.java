package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.ResourceKey;
import com.integrien.alive.common.adapter3.config.ResourceIdentifierConfig;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.List;

/** Narrow, fixture-only qualification of complete LU-associated NAA5/6 descriptors. */
final class CanonicalLunIdentity {
    static final String KIND = "CanonicalLun";
    final String payload;
    final String scsiUuid;
    final Map<String, String> properties;

    private CanonicalLunIdentity(String payload, String uuid, Map<String, String> properties) {
        this.payload = payload;
        this.scsiUuid = uuid;
        this.properties = Map.copyOf(properties);
    }

    static CanonicalLunIdentity parse(Object input) {
        Map<String, Object> value = RuntimeFixture.object(input, "provenance", "backing_lun_uuid",
                "scsi_lun_uuid", "canonical_name", "storage_reference", "descriptors", "properties");
        RuntimeFixture.require("vsphere-soap-fixture".equals(value.get("provenance")));
        String uuid = RuntimeFixture.string(value.get("scsi_lun_uuid"));
        RuntimeFixture.require(uuid.equals(RuntimeFixture.string(value.get("backing_lun_uuid"))));
        String canonical = naa(value.get("canonical_name"), "naa.");
        RuntimeFixture.require(canonical.equals(naa(value.get("storage_reference"), "0x")));
        Object descriptors = value.get("descriptors");
        RuntimeFixture.require(descriptors instanceof List && !((List<?>) descriptors).isEmpty()
                && ((List<?>) descriptors).size() <= 16);
        Set<String> eligible = new LinkedHashSet<>();
        for (Object entry : (List<?>) descriptors) {
            Map<String, Object> descriptor = RuntimeFixture.object(entry, "namespace", "namespace_id", "data_hex");
            // Namespace ID is an opaque VMware byte, never the SCSI designator type.
            String namespaceId = RuntimeFixture.string(descriptor.get("namespace_id"));
            RuntimeFixture.require(namespaceId.matches("-?(0|[1-9][0-9]{0,2})"));
            int id = Integer.parseInt(namespaceId);
            RuntimeFixture.require(id >= -128 && id <= 255);
            String namespace = RuntimeFixture.string(descriptor.get("namespace"));
            RuntimeFixture.require(namespace.equalsIgnoreCase("naa"));
            String hex = RuntimeFixture.string(descriptor.get("data_hex"));
            RuntimeFixture.require(hex.matches("([0-9a-fA-F]{2}){4,259}"));
            byte[] raw = new byte[hex.length() / 2];
            for (int i = 0; i < raw.length; i++) raw[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            int a = raw[0] & 255, b = raw[1] & 255;
            RuntimeFixture.require(a == 1 && (b & 0xc0) == 0 && (b & 15) == 3
                    && raw[2] == 0 && raw.length == 4 + (raw[3] & 255));
            String candidate = naa("naa." + hex.substring(8), "naa.");
            int association = (b >> 4) & 3;
            RuntimeFixture.require(association != 3);
            if (association == 0) eligible.add(candidate);
        }
        RuntimeFixture.require(eligible.size() == 1 && eligible.contains(canonical));
        RuntimeFixture.require(value.get("properties") instanceof Map);
        Map<?, ?> descriptions = (Map<?, ?>) value.get("properties");
        Set<String> allowed = Set.of("vendor", "model", "revision", "serial_number", "lun_type", "device_type",
                "capacity_blocks", "capacity_block_size");
        RuntimeFixture.require(allowed.containsAll(descriptions.keySet()));
        Map<String, String> properties = new LinkedHashMap<>();
        for (Map.Entry<?, ?> item : descriptions.entrySet()) {
            String key = (String) item.getKey();
            String text = RuntimeFixture.string(item.getValue());
            if (!key.startsWith("capacity_")) properties.put("storage|" + key, text);
        }
        RuntimeFixture.require(descriptions.containsKey("capacity_blocks") == descriptions.containsKey("capacity_block_size"));
        if (descriptions.containsKey("capacity_blocks")) {
            long blocks = positive(descriptions.get("capacity_blocks"));
            long blockSize = positive(descriptions.get("capacity_block_size"));
            properties.put("storage|capacity_bytes", Long.toString(Math.multiplyExact(blocks, blockSize)));
        }
        properties.put("identity|format", "naa" + canonical.charAt(0));
        properties.put("identity|canonical_name", "naa." + canonical);
        properties.put("identity|provenance", "vsphere-soap-fixture");
        properties.put("identity|assessment", "fixture-qualified-single-authority-lab");
        return new CanonicalLunIdentity(canonical, uuid, properties);
    }

    private static long positive(Object value) {
        String text = RuntimeFixture.string(value);
        RuntimeFixture.require(text.matches("[1-9][0-9]{0,18}"));
        return Long.parseLong(text);
    }

    private static String naa(Object value, String prefix) {
        String text = RuntimeFixture.string(value);
        RuntimeFixture.require(text.regionMatches(true, 0, prefix, 0, prefix.length()));
        String payload = text.substring(prefix.length()).toLowerCase(Locale.ROOT);
        RuntimeFixture.require(payload.matches("5[0-9a-f]{15}|6[0-9a-f]{31}"));
        return payload;
    }

    ResourceKey toResourceKey(String adapterKind) {
        ResourceKey key = new ResourceKey("naa." + payload, KIND, adapterKind);
        key.addIdentifier(new ResourceIdentifierConfig("identity_token", "scsi-vpd83:naa:" + payload, true));
        return key;
    }
}
