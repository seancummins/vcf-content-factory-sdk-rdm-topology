package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.AdapterBase;
import com.integrien.alive.common.adapter3.MetricData;
import com.integrien.alive.common.adapter3.Relationships;
import com.integrien.alive.common.adapter3.ResourceStatus;
import com.integrien.alive.common.adapter3.config.ResourceConfig;
import com.integrien.alive.common.util.CommonConstants.ResourceStatusEnum;
import com.vcfcf.adapter.VcfCfAdapter;
import com.vcfcf.adapter.http.ManagedHttpClient;
import com.vcfcf.adapter.spi.ResourceSink;
import com.vcfcf.adapter.spi.VcfCfCollector;
import com.vcfcf.adapter.spi.VcfCfTester;

import java.util.List;

/**
 * Fixture-only proof of ResourceKey and additive foreign-parent relationship
 * mechanics. No code in this adapter opens a socket or queries vCenter/Operations.
 */
public final class RdmTopologyPocAdapter extends VcfCfAdapter<Void> {

    public static final String ADAPTER_KIND = "vcfcf_rdm_topology_poc";

    public RdmTopologyPocAdapter() {
        super(ADAPTER_KIND);
    }

    public RdmTopologyPocAdapter(String adapterDir, Integer adapterInstanceId) {
        super(ADAPTER_KIND, adapterDir, adapterInstanceId);
    }

    @Override
    protected void configureAdapter(ResourceStatus status, ResourceConfig resourceConfig) {
        // This POC intentionally has neither endpoint configuration nor credentials.
        this.config = null;
    }

    @Override
    protected void enumerateResources(ResourceSink sink) {
        sink.accept(new ResourceConfig(RdmTopologyFixture.mapping().toResourceKey(ADAPTER_KIND)));
    }

    @Override
    protected boolean discoverOnCollect() {
        return true;
    }

    @Override
    protected VcfCfTester<Void> getTester() {
        return null;
    }

    @Override
    protected VcfCfCollector<Void> getCollector() {
        return new VcfCfCollector<Void>() {
            @Override
            public void collect(Void ignored, ManagedHttpClient http, ResourceConfig resource,
                    List<MetricData> output, AdapterBase adapter) {
                // No metrics or properties: this POC proves topology mechanics only.
            }

            @Override
            public Relationships collectRelationships(Void ignored, ResourceConfig resource) {
                if (!RdmMappingIdentity.RESOURCE_KIND.equals(resource.getResourceKind())) {
                    return null;
                }
                // VcfCfAdapter attaches this to the current CollectResult with
                // addRelationshipsToCurrentCycle(). The parentForeign builder path
                // invokes Relationships.addRelationships, never setRelationships.
                return RdmTopologyRelationships.build(ADAPTER_KIND,
                        RdmTopologyFixture.foreignVm(), "fixture-rdm-vm",
                        RdmTopologyFixture.mapping());
            }

            @Override
            public ResourceStatusEnum mapCollectException(Exception error) {
                return ResourceStatusEnum.RESOURCE_STATUS_ERROR;
            }
        };
    }
}
