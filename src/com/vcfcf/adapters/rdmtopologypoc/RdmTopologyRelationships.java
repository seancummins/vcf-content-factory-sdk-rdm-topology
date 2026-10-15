package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.Relationships;
import com.integrien.alive.common.adapter3.ResourceKey;
import com.vcfcf.adapter.stitch.RelationshipBuilder;

/** Builds the sole additive foreign-parent edge emitted by the POC. */
public final class RdmTopologyRelationships {

    private RdmTopologyRelationships() { }

    public static Relationships build(String adapterKind, ForeignVmIdentity vm,
            String vmDisplayName, RdmMappingIdentity mapping) {
        if (vm == null || mapping == null) {
            throw new IllegalArgumentException("Resolved foreign VM and RDM mapping are required");
        }
        ResourceKey foreignVm = vm.toResourceKey(vmDisplayName);
        ResourceKey localMapping = mapping.toResourceKey(adapterKind);
        return new RelationshipBuilder(adapterKind)
                .parentForeign(foreignVm, localMapping)
                .build();
    }
}
