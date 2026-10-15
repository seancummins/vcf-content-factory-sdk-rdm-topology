package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.ResourceKey;
import com.integrien.alive.common.adapter3.config.ResourceIdentifierConfig;

/** Stable identity for a pack-owned RDM mapping resource. */
public final class RdmMappingIdentity {

    public static final String RESOURCE_KIND = "RdmMapping";
    public static final String VCENTER_INSTANCE_UUID = "vcenter_instance_uuid";
    public static final String VM_INSTANCE_UUID = "vm_instance_uuid";
    public static final String VIRTUAL_DISK_KEY = "virtual_disk_key";

    private final String vcenterInstanceUuid;
    private final String vmInstanceUuid;
    private final String virtualDiskKey;

    public RdmMappingIdentity(String vcenterInstanceUuid, String vmInstanceUuid,
            String virtualDiskKey) {
        this.vcenterInstanceUuid = require("vcenterInstanceUuid", vcenterInstanceUuid);
        this.vmInstanceUuid = require("vmInstanceUuid", vmInstanceUuid);
        this.virtualDiskKey = require("virtualDiskKey", virtualDiskKey);
    }

    public ResourceKey toResourceKey(String adapterKind) {
        ResourceKey key = new ResourceKey("RDM mapping " + virtualDiskKey,
                RESOURCE_KIND, require("adapterKind", adapterKind));
        key.addIdentifier(new ResourceIdentifierConfig(VCENTER_INSTANCE_UUID,
                vcenterInstanceUuid, true));
        key.addIdentifier(new ResourceIdentifierConfig(VM_INSTANCE_UUID,
                vmInstanceUuid, true));
        key.addIdentifier(new ResourceIdentifierConfig(VIRTUAL_DISK_KEY,
                virtualDiskKey, true));
        return key;
    }

    private static String require(String field, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.trim();
    }
}
