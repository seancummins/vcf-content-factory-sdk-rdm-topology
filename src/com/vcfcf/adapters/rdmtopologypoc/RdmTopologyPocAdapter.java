package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.AdapterBase;
import com.integrien.alive.common.adapter3.MetricData;
import com.integrien.alive.common.adapter3.Relationships;
import com.integrien.alive.common.adapter3.ResourceStatus;
import com.integrien.alive.common.adapter3.config.ResourceConfig;
import com.integrien.alive.common.adapter3.config.ResourceIdentifierConfig;
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
public final class RdmTopologyPocAdapter extends VcfCfAdapter<RuntimeFixture> {

    public static final String ADAPTER_KIND = "vcfcf_rdm_topology_poc";
    public static final String FIXTURE_JSON = "fixture_json";

    public RdmTopologyPocAdapter() {
        super(ADAPTER_KIND);
    }

    public RdmTopologyPocAdapter(String adapterDir, Integer adapterInstanceId) {
        super(ADAPTER_KIND, adapterDir, adapterInstanceId);
    }

    @Override
    protected void configureAdapter(ResourceStatus status, ResourceConfig resourceConfig) {
        // Clear first: a failed reconfiguration must never retain an old target.
        this.config = null;
        String input = null;
        int count = 0;
        if (resourceConfig != null && resourceConfig.getResourceIdentifiers() != null) {
            for (ResourceIdentifierConfig identifier : resourceConfig.getResourceIdentifiers()) {
                if (identifier != null && FIXTURE_JSON.equals(identifier.getKey())) {
                    input = identifier.getValue();
                    count++;
                    if (identifier.isPartOfUniqueness()) {
                        throw new IllegalArgumentException("Invalid runtime fixture setting identity");
                    }
                }
            }
        }
        if (count != 1) throw new IllegalArgumentException("Exactly one runtime fixture setting is required");
        this.config = RuntimeFixture.parse(input);
    }

    @Override
    protected void enumerateResources(ResourceSink sink) {
        RuntimeFixture fixture = requireFixture();
        sink.accept(new ResourceConfig(fixture.mapping.toResourceKey(ADAPTER_KIND)));
    }

    @Override
    protected boolean discoverOnCollect() {
        return true;
    }

    @Override
    protected VcfCfTester<RuntimeFixture> getTester() {
        return null;
    }

    @Override
    protected VcfCfCollector<RuntimeFixture> getCollector() {
        return new VcfCfCollector<RuntimeFixture>() {
            @Override
            public void collect(RuntimeFixture configured, ManagedHttpClient http, ResourceConfig resource,
                    List<MetricData> output, AdapterBase adapter) {
                requireCurrent(configured, resource);
                // No metrics or properties: this POC proves topology mechanics only.
            }

            @Override
            public Relationships collectRelationships(RuntimeFixture configured, ResourceConfig resource) {
                RuntimeFixture fixture = requireCurrent(configured, resource);
                // VcfCfAdapter attaches this to the current CollectResult with
                // addRelationshipsToCurrentCycle(). The parentForeign builder path
                // invokes Relationships.addRelationships, never setRelationships.
                return RdmTopologyRelationships.build(ADAPTER_KIND,
                        fixture.vm, fixture.vmName, fixture.mapping);
            }

            @Override
            public ResourceStatusEnum mapCollectException(Exception error) {
                return ResourceStatusEnum.RESOURCE_STATUS_ERROR;
            }
        };
    }

    private RuntimeFixture requireFixture() {
        RuntimeFixture fixture = this.config;
        if (fixture == null) throw new IllegalStateException("Runtime fixture is not configured");
        return fixture;
    }

    private RuntimeFixture requireCurrent(RuntimeFixture configured, ResourceConfig resource) {
        RuntimeFixture current = requireFixture();
        if (configured != current || resource == null
                || !current.mapping.toResourceKey(ADAPTER_KIND).equals(resource.getResourceKey())) {
            throw new IllegalStateException("Collection target does not match the active fixture");
        }
        return current;
    }
}
