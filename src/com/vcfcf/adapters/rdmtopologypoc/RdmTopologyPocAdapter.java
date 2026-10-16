package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.AdapterBase;
import com.integrien.alive.common.adapter3.MetricData;
import com.integrien.alive.common.adapter3.MetricKey;
import com.integrien.alive.common.adapter3.ResourceKey;
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
        java.util.Set<ResourceKey> emitted = new java.util.LinkedHashSet<>();
        for (RuntimeFixture attachment : fixture.attachments) {
            ResourceKey mapping = attachment.mapping.toResourceKey(ADAPTER_KIND);
            if (emitted.add(mapping)) sink.accept(new ResourceConfig(mapping));
            if (attachment.lun != null) {
                ResourceKey lun = attachment.lun.toResourceKey(ADAPTER_KIND);
                if (emitted.add(lun)) sink.accept(new ResourceConfig(lun));
            }
        }
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
                RuntimeFixture attachment = requireCurrent(configured, resource);
                if (attachment.lun != null && resource.getResourceKey().getResourceKind().equals(CanonicalLunIdentity.KIND)) {
                    long now = System.currentTimeMillis();
                    attachment.lun.properties.forEach((key, value) ->
                            output.add(new MetricData(new MetricKey(true, key), now, value)));
                }
            }

            @Override
            public Relationships collectRelationships(RuntimeFixture configured, ResourceConfig resource) {
                RuntimeFixture fixture = requireCurrent(configured, resource);
                if (resource.getResourceKey().getResourceKind().equals(CanonicalLunIdentity.KIND)) return null;
                // VcfCfAdapter attaches this to the current CollectResult with
                // addRelationshipsToCurrentCycle(). The parentForeign builder path
                // invokes Relationships.addRelationships, never setRelationships.
                Relationships relationships = RdmTopologyRelationships.build(ADAPTER_KIND,
                        fixture.vm, fixture.vmName, fixture.mapping);
                if (fixture.lun != null) relationships.addRelationships(
                        new com.vcfcf.adapter.stitch.RelationshipBuilder(ADAPTER_KIND)
                                .parent(fixture.mapping.toResourceKey(ADAPTER_KIND), fixture.lun.toResourceKey(ADAPTER_KIND))
                                .buildDelta(false));
                return relationships;
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
        if (configured != current || resource == null) {
            throw new IllegalStateException("Collection target does not match the active fixture");
        }
        for (RuntimeFixture attachment : current.attachments) {
            if (attachment.mapping.toResourceKey(ADAPTER_KIND).equals(resource.getResourceKey())
                    || attachment.lun != null && attachment.lun.toResourceKey(ADAPTER_KIND).equals(resource.getResourceKey())) {
                return attachment;
            }
        }
        throw new IllegalStateException("Collection target does not match the active fixture");
    }
}
