package com.vcfcf.adapters.rdmtopologypoc.diagnostic;

import java.util.concurrent.atomic.AtomicInteger;
/** Deterministic graph boundary for dispatcher tests; no fake SDK or secret-decryption classes. */
public final class DispatcherDiagnosticFixture {
    public static DiagnosticRuntime success(AtomicInteger calls){return new DiagnosticRuntime(c->{calls.incrementAndGet();return DiagnosticRuntimeTest.good();},1000,"vcfcf_rdm_topology_poc");}
    public static DiagnosticRuntime blocked(AtomicInteger calls,java.util.concurrent.CountDownLatch started,java.util.concurrent.CountDownLatch release){
        return new DiagnosticRuntime(c->{calls.incrementAndGet();started.countDown();while(release.getCount()>0)try{release.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();return DiagnosticRuntimeTest.good();}return DiagnosticRuntimeTest.good();},2000,"vcfcf_rdm_topology_poc");
    }
    public static com.integrien.alive.common.adapter3.config.AdapterConfig config(){
        com.integrien.alive.common.adapter3.config.AdapterConfig c=DiagnosticRuntimeTest.config();
        c.setAdapterKindKey("vcfcf_rdm_topology_poc");c.getAdapterInstResource().setAdapterKind("vcfcf_rdm_topology_poc");c.getAdapterInstResource().setResourceKind("vcfcf_rdm_topology_poc");
        c.getAdapterInstResource().addResourceIdentifier(new com.integrien.alive.common.adapter3.config.ResourceIdentifierConfig("execution_mode","read_only_diagnostic",false));return c;
    }
}
