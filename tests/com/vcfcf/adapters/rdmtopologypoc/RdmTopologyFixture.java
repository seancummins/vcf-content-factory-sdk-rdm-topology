package com.vcfcf.adapters.rdmtopologypoc;

import java.util.List;

/** Synthetic test data only; never compiled into the PAK. */
final class RdmTopologyFixture {
    static RdmMappingIdentity mapping() {
        return new RdmMappingIdentity("11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222", "2000");
    }

    static ForeignVmIdentity foreignVm() {
        return new ForeignVmIdentity(List.of(
                new ForeignVmIdentity.Identifier("VMEntityObjectID", "vm-71", true),
                new ForeignVmIdentity.Identifier("VMEntityVCID", "11111111-1111-1111-1111-111111111111", true),
                new ForeignVmIdentity.Identifier("VMEntityName", "fixture-rdm-vm", false)));
    }
}
