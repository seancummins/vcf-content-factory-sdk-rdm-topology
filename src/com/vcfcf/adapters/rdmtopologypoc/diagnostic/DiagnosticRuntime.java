package com.vcfcf.adapters.rdmtopologypoc.diagnostic;

import com.integrien.alive.common.adapter3.config.*;
import com.vcfcf.adapters.rdmtopologypoc.discovery.*;
import com.vcfcf.adapters.rdmtopologypoc.runtime.*;
import com.vcfcf.adapters.rdmtopologypoc.ops.*;
import java.util.*;
import java.util.concurrent.*;

/** One shot, target-scoped, publication-free diagnostic. No platform collection builder is reachable here. */
public final class DiagnosticRuntime implements AutoCloseable {
    public static final String KIND = "vcfcf_rdm_discovery_diagnostic";
    public enum State { DISABLED, READY, NOT_AUTHORIZED, RUNNING, PASS, FAILED, CONSUMED, CANCELLED, DISCARDED, BUSY }
    public enum Stage { CONFIGURATION, OPERATIONS, OPERATIONS_CLEANUP, VCENTER, GRAPH, EXECUTION }
    public static final class Report {
        public final State state; public final Stage stage;
        private final String code;
        public final int vmCount, mappingCount, lunCount;
        private Report(State state, Stage stage, String code, int vmCount, int mappingCount, int lunCount) {
            this.state=state;this.stage=stage;this.code=code;
            this.vmCount=vmCount;this.mappingCount=mappingCount;this.lunCount=lunCount;
        }
        public boolean previousRunPassed(){return state==State.PASS||state==State.CONSUMED&&code.startsWith("NO_NEW_EXECUTION_LAST_PASS_");}
        @Override public String toString() { return "RDM_DIAGNOSTIC state="+state+" stage="+stage+" code="+code
                +" vms="+vmCount+" mappings="+mappingCount+" luns="+lunCount; }
    }
    static Report report(State state, Stage stage, String staticCode) { return new Report(state,stage,staticCode,0,0,0); }
    static final class Config {
        final AdapterConfig source; final ResourceConfig resource;
        final RuntimeDiscoveryBinding vcenter; final OperationsBinding operations;
        final String vc,moid,disk; final boolean authorized;
        final List<Object> stamp;
        Config(AdapterConfig source) throws DiscoveryException,OpsException { this(source,KIND); }
        Config(AdapterConfig source,String owner) throws DiscoveryException,OpsException {
            this.source=source;
            if(source==null||!owner.equals(source.getAdapterKindKey())) invalid();
            resource=source.getAdapterInstResource();
            if(resource==null||!owner.equals(resource.getAdapterKind())||!owner.equals(resource.getResourceKind())) invalid();
            Map<String,String> fields=new LinkedHashMap<>();
            Set<String> expected=Set.of("vcenter_endpoint","trust_mode","discovery_scope","ops_endpoint","ops_trust_mode",
                    "target_vcenter_uuid","target_vm_moid","target_disk_key","diagnostic_enabled","authorize_once");
            if(resource.getResourceIdentifiers()==null) invalid();
            Set<String> seen=new HashSet<>();
            for(ResourceIdentifierConfig id:resource.getResourceIdentifiers()) {
                if(id==null||!seen.add(id.getKey()))invalid();
                if(!KIND.equals(owner)&&id!=null&&("execution_mode".equals(id.getKey())||"fixture_json".equals(id.getKey()))) {
                    if(id.isPartOfUniqueness()||("execution_mode".equals(id.getKey())?!"read_only_diagnostic".equals(id.getValue()):id.getValue()!=null&&!id.getValue().isEmpty()))invalid();
                    continue;
                }
                if(id==null||id.isPartOfUniqueness()||!expected.contains(id.getKey())||id.getValue()==null
                        ||fields.putIfAbsent(id.getKey(),id.getValue())!=null) invalid();
            }
            if(!KIND.equals(owner)&&!seen.contains("execution_mode"))invalid();
            if(!fields.keySet().equals(expected)||!"single_vm_current_host".equals(fields.get("discovery_scope"))) invalid();
            for(String flag:List.of("diagnostic_enabled","authorize_once")) if(!Set.of("true","false").contains(fields.get(flag))) invalid();
            authorized="true".equals(fields.get("authorize_once"));
            vc=fields.get("target_vcenter_uuid");moid=fields.get("target_vm_moid");disk=fields.get("target_disk_key");
            if(!vc.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                    ||vc.equals("00000000-0000-0000-0000-000000000000")||!moid.matches("vm-[1-9][0-9]{0,18}")
                    ||!disk.matches("[1-9][0-9]{0,9}")||Long.parseLong(disk)>Integer.MAX_VALUE) invalid();
            vcenter=KIND.equals(owner)?RuntimeDiscoveryBinding.diagnostic(resource):RuntimeDiscoveryBinding.sameKindDiagnostic(resource);
            operations=KIND.equals(owner)?OperationsBinding.diagnostic(source,fields.get("ops_endpoint"),fields.get("ops_trust_mode"),
                    resource.getTrustedCertificates(),resource.getTrustedCertificateIds()):OperationsBinding.dormant(source,fields.get("ops_endpoint"),fields.get("ops_trust_mode"),
                    resource.getTrustedCertificates(),resource.getTrustedCertificateIds());
            stamp=stamp(source);
            if(!"true".equals(fields.get("diagnostic_enabled"))) throw new Disabled();
        }
        void check() throws DiscoveryException { if(source.getAdapterInstResource()!=resource||!stamp.equals(stamp(source))) invalid(); }
        private static List<Object> stamp(AdapterConfig source) {
            ResourceConfig r=source.getAdapterInstResource();List<Object> s=new ArrayList<>();
            s.add(source.getAdapterKindKey());s.add(source.getAdapterInstResourceId());s.add(source.getAdapterInstResourceUuid());
            s.add(r);s.add(source.getAdapterCredentials());s.add(r.getAdapterKind());s.add(r.getResourceKind());s.add(r.getResourceCredential());
            for(ResourceIdentifierConfig id:r.getResourceIdentifiers()) { s.add(id.getKey());s.add(id.getValue());s.add(id.isPartOfUniqueness()); }
            CredentialConfig c=r.getResourceCredential();
            if(c!=null) {s.add(c.getCredentialKey());if(c.getCredenialFields()!=null)for(CredentialFieldConfig f:c.getCredenialFields()) {s.add(f);s.add(f.getKey());s.add(f.isPassword());}}
            s.add(r.getTrustedCertificateIds()==null?null:new ArrayList<>(r.getTrustedCertificateIds()));
            if(r.getTrustedCertificates()!=null)for(CertificateConfig cert:r.getTrustedCertificates()) {
                s.add(cert.getCertificateString());s.add(cert.getThumbprint());s.add(cert.getEnforceHostnameVerification());s.add(cert.getEnforceDateValidityChecking());
            }
            return Collections.unmodifiableList(s);
        }
        private static void invalid() throws DiscoveryException { throw new DiscoveryException(DiscoveryException.Code.CONFIGURATION); }
    }
    private static final class Disabled extends RuntimeException { }
    static final class CleanupFailure extends Exception {
        final OpsException.Code code;CleanupFailure(OpsException.Code code){this.code=code;}
    }
    interface Work { DiscoveryGraph run(Config config) throws DiscoveryException,OpsException,CleanupFailure; }
    private final Object lock=new Object(); private final Work work; private final long timeoutMillis;
    private final String owner;
    private Config config; private boolean consumed,closed; private long generation;
    private ExecutorService worker; private Future<Report> pending;
    private volatile Report latest=report(State.DISABLED,Stage.CONFIGURATION,"DISABLED");
    public DiagnosticRuntime() { this(DiagnosticRuntime::nativeDiscovery,150000); }
    DiagnosticRuntime(Work work,long timeoutMillis) { this(work,timeoutMillis,KIND); }
    DiagnosticRuntime(Work work,long timeoutMillis,String owner) { this.work=work;this.timeoutMillis=timeoutMillis;this.owner=owner; }
    public static DiagnosticRuntime sameKind(){return new DiagnosticRuntime(DiagnosticRuntime::nativeDiscovery,150000,"vcfcf_rdm_topology_poc");}
    public Report configure(AdapterConfig source) {
        synchronized(lock) {
            if(closed)return latest=report(State.DISCARDED,Stage.CONFIGURATION,"DISCARDED");
            generation++;
            if(pending!=null&&!pending.isDone())pending.cancel(true);
            if(worker!=null)worker.shutdownNow();
            config=null;
            if(worker!=null&&!worker.isTerminated())return latest=report(State.BUSY,Stage.CONFIGURATION,"WORKER_RETIRING");
            if(consumed)return latest=report(State.CONSUMED,Stage.CONFIGURATION,"ONE_SHOT_CONSUMED");
            try { config=new Config(source,owner);return latest=report(config.authorized?State.READY:State.NOT_AUTHORIZED,Stage.CONFIGURATION,"CONFIGURATION_ONLY"); }
            catch(Disabled ex){return latest=report(State.DISABLED,Stage.CONFIGURATION,"DISABLED");}
            catch(Exception|LinkageError ex){return latest=report(State.FAILED,Stage.CONFIGURATION,"INVALID_CONFIGURATION");}
        }
    }
    public Report latest() { return latest; }
    /** Validation-only SDK callback: never changes an installed generation or consumes authorization. */
    public static Report validateConfiguration(AdapterConfig source) {
        return validateConfiguration(source,KIND);
    }
    public static Report validateSameKindConfiguration(AdapterConfig source){return validateConfiguration(source,"vcfcf_rdm_topology_poc");}
    private static Report validateConfiguration(AdapterConfig source,String owner) {
        try{Config c=new Config(source,owner);return report(c.authorized?State.READY:State.NOT_AUTHORIZED,Stage.CONFIGURATION,"CONFIGURATION_ONLY_NOT_RUNTIME_PROOF");}
        catch(Disabled ex){return report(State.DISABLED,Stage.CONFIGURATION,"CONFIGURATION_ONLY_NOT_RUNTIME_PROOF");}
        catch(Exception|LinkageError ex){return report(State.FAILED,Stage.CONFIGURATION,"INVALID_CONFIGURATION");}
    }
    /** Blocks for at most 150 seconds; cancellation prevents a subsequent run on this object. */
    public Report executeOnce() {
        final Future<Report> task;final long epoch;
        synchronized(lock) {
            if(closed)return latest=report(State.DISCARDED,Stage.EXECUTION,"DISCARDED");
            if(consumed)return new Report(pending!=null&&!pending.isDone()?State.RUNNING:State.CONSUMED,latest.stage,
                    "NO_NEW_EXECUTION_LAST_"+latest.state+"_"+latest.code,latest.vmCount,latest.mappingCount,latest.lunCount);
            if(config==null)return latest;
            if(!config.authorized)return latest=report(State.NOT_AUTHORIZED,Stage.CONFIGURATION,"AUTHORIZATION_REQUIRED");
            consumed=true;epoch=generation;final Config bound=config;
            latest=report(State.RUNNING,Stage.EXECUTION,"RUNNING");
            worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"rdm-readonly-diagnostic");t.setDaemon(true);return t;});
            task=worker.submit(()->run(bound));pending=task;worker.shutdown();
        }
        Report result;
        try {result=task.get(timeoutMillis,TimeUnit.MILLISECONDS);}
        catch(TimeoutException ex){task.cancel(true);result=report(State.FAILED,Stage.EXECUTION,"TIMEOUT");}
        catch(InterruptedException ex){task.cancel(true);Thread.currentThread().interrupt();result=report(State.CANCELLED,Stage.EXECUTION,"CANCELLED");}
        catch(CancellationException ex){result=report(State.CANCELLED,Stage.EXECUTION,"CANCELLED");}
        catch(ExecutionException|RuntimeException ex){result=report(State.FAILED,Stage.EXECUTION,"UNEXPECTED_FAILURE");}
        synchronized(lock) {
            if(closed||epoch!=generation)return report(State.CANCELLED,Stage.EXECUTION,"GENERATION_RETIRED");
            return latest=result;
        }
    }
    private Report run(Config c) {
        try {
            c.check();DiscoveryGraph graph=work.run(c);c.check();
            if(Thread.currentThread().isInterrupted())return report(State.CANCELLED,Stage.EXECUTION,"CANCELLED");
            if(graph==null||!graph.complete||!graph.diagnostics.isEmpty()||graph.attachments.size()!=1||graph.luns.size()!=1)
                return report(State.FAILED,Stage.GRAPH,"INCOMPLETE_OR_AMBIGUOUS_GRAPH");
            DiscoveryGraph.Attachment a=graph.attachments.get(0);
            if(a.lun==null||a.unresolved!=null||!c.vc.equals(a.vcenterInstanceUuid)||!c.vc.equals(a.vm.scoped("VMEntityVCID"))
                    ||!c.moid.equals(a.vm.scoped("VMEntityObjectID"))||!c.disk.equals(a.diskKey))
                return report(State.FAILED,Stage.GRAPH,"IDENTITY_MISMATCH");
            return new Report(State.PASS,Stage.GRAPH,"READ_ONLY_GRAPH_QUALIFIED",1,1,1);
        }catch(CleanupFailure ex){return report(State.FAILED,Stage.OPERATIONS_CLEANUP,ex.code.name());}
        catch(OpsException ex){return report(State.FAILED,Stage.OPERATIONS,ex.code().name());}
        catch(DiscoveryException ex){return report(State.FAILED,ex.code()==DiscoveryException.Code.CONFIGURATION?Stage.CONFIGURATION:Stage.VCENTER,ex.code().name());}
        catch(RuntimeException|LinkageError ex){return report(State.FAILED,Stage.EXECUTION,"UNEXPECTED_FAILURE");}
    }
    private static DiscoveryGraph nativeDiscovery(Config c) throws OpsException,DiscoveryException,CleanupFailure {
        c.check();SuiteApiVmProvider provider=c.operations.dormantProvider();
        return compose(c,provider,resolver->{
            VimDiscoveryClient client=new VimDiscoveryClient(c.vcenter.diagnosticTransport(),resolver,c.vc,c.moid);
            try(RuntimeDiscoveryBinding.Credentials credential=c.vcenter.credentials()) {return credential.discover(client);}
        });
    }
    interface VcenterRead { DiscoveryGraph discover(DiscoveryGraph.VmResolver resolver) throws DiscoveryException; }
    /** Shared composition, injectable only by same-package deterministic tests; production uses SDK leases above. */
    static DiscoveryGraph compose(Config c,SuiteApiVmProvider provider,VcenterRead read) throws OpsException,DiscoveryException,CleanupFailure {
        c.check();
        SuiteApiVmProvider.Snapshot keys=provider.capture(c.vc,Set.of(c.moid));
        if(provider.cleanupFailure().isPresent())throw new CleanupFailure(provider.cleanupFailure().get());
        c.check();keys.resolve(c.vc,c.moid);
        DiscoveryGraph graph=read.discover(keys.resolver());keys.resolve(c.vc,c.moid);return graph;
    }
    public void cancel() {
        synchronized(lock) {consumed=true;generation++;config=null;if(pending!=null)pending.cancel(true);if(worker!=null)worker.shutdownNow();latest=report(State.CANCELLED,Stage.EXECUTION,"CANCELLED");}
    }
    @Override public void close() { synchronized(lock){cancel();closed=true;latest=report(State.DISCARDED,Stage.EXECUTION,"DISCARDED");} }
    @Override public String toString(){return latest.toString();}
}
