package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.*;
import com.integrien.alive.common.adapter3.config.*;
import com.integrien.alive.common.adapter3.describe.AdapterDescribe;
import com.vcfcf.adapters.rdmtopologypoc.diagnostic.DiagnosticRuntime;
import java.util.*;
import java.util.function.Supplier;

/** Same-kind facade. Diagnostic dispatch never constructs AdapterBase or invokes its publication lifecycle. */
public final class RdmTopologyDispatcher implements AdapterInterface3 {
    public static final String KIND="vcfcf_rdm_topology_poc";
    private enum Mode { FIXTURE, DIAGNOSTIC }
    /** Only these fixed codes cross the SDK status boundary; never expose input or exception text. */
    private enum Reason {
        CONFIGURATION_MISSING, COLLECTOR_ID_MISMATCH, ADAPTER_KIND_MISMATCH, INSTANCE_RESOURCE_MISSING,
        INSTANCE_KIND_MISMATCH, IDENTIFIERS_MISSING, IDENTIFIER_NULL, IDENTIFIER_KEY_NULL,
        IDENTIFIER_UNIQUE, IDENTIFIER_DUPLICATE, IDENTIFIER_UNKNOWN, EXECUTION_MODE_NULL,
        EXECUTION_MODE_UNKNOWN, DIAGNOSTIC_FIXTURE_PRESENT,
        DIAGNOSTIC_CONFIGURATION_INVALID, DIAGNOSTIC_CONFIGURATION_FAILED, FIXTURE_DIAGNOSTIC_PRESENT, FIXTURE_CREDENTIAL_PRESENT,
        FIXTURE_INVALID, MODE_CHANGED, INSTANCE_IDENTITY_CHANGED, DELTA_REQUIRES_ACTIVE_FIXTURE,
        DELTA_DIAGNOSTIC_UNSUPPORTED, DELTA_CREDENTIAL_UNSUPPORTED, DELTA_CUSTOM_CONFIGURATION_UNSUPPORTED,
        DELTA_EXTENDED_CONFIGURATION_UNSUPPORTED, FIXTURE_CONSTRUCTION_FAILED, FIXTURE_CONFIGURATION_FAILED,
        CONFIGURATION_RETIRED, INTERNAL_CONFIGURATION_FAILURE, PLATFORM_LINKAGE_FAILURE
    }
    private static final class Rejected extends IllegalArgumentException {
        final Reason reason;
        Rejected(Reason reason){this.reason=reason;}
    }
    private static Rejected rejected(Reason reason){return new Rejected(reason);}
    private static String rejection(Reason reason){return "INVALID_CONFIGURATION reason="+reason.name();}
    private static final Set<String> DIAGNOSTIC_FIELDS=Set.of("vcenter_endpoint","trust_mode","discovery_scope","ops_endpoint",
            "ops_trust_mode","target_vcenter_uuid","target_vm_moid","target_disk_key","diagnostic_enabled","authorize_once");
    private final Object lock=new Object();
    private final Supplier<AdapterInterface3> fixtureFactory;
    private final DiagnosticRuntime diagnostic;
    private final boolean collectorBound;
    private final Integer collectorInstanceId;
    private AdapterInterface3 fixture;
    private AdapterConfig source;
    private List<Object> stamp;
    private Mode latched;
    private Integer instanceId;
    private java.util.UUID instanceUuid;
    private boolean valid,closed,stopped;
    public RdmTopologyDispatcher(){this(RdmTopologyPocAdapter::new,DiagnosticRuntime.sameKind());}
    /** Collector reflection contract; retain its exact SDK initialization arguments lazily. */
    public RdmTopologyDispatcher(String adapterDir,Integer adapterInstanceId){
        this(()->new RdmTopologyPocAdapter(adapterDir,adapterInstanceId),DiagnosticRuntime.sameKind(),true,adapterInstanceId);
    }
    RdmTopologyDispatcher(Supplier<AdapterInterface3> factory,DiagnosticRuntime diagnostic){this(factory,diagnostic,false,null);}
    private RdmTopologyDispatcher(Supplier<AdapterInterface3> factory,DiagnosticRuntime diagnostic,boolean bound,Integer id){
        this.fixtureFactory=factory;this.diagnostic=diagnostic;this.collectorBound=bound;this.collectorInstanceId=id;this.instanceId=id;
    }
    private void requireCollectorIdentity(AdapterConfig config){
        if(config==null)throw rejected(Reason.CONFIGURATION_MISSING);
        if(collectorBound&&!Objects.equals(collectorInstanceId,config.getAdapterInstResourceId()))throw rejected(Reason.COLLECTOR_ID_MISMATCH);
    }
    private static Mode selection(AdapterConfig config){
        if(config==null)throw rejected(Reason.CONFIGURATION_MISSING);
        if(!KIND.equals(config.getAdapterKindKey()))throw rejected(Reason.ADAPTER_KIND_MISMATCH);
        ResourceConfig r=config.getAdapterInstResource();
        if(r==null)throw rejected(Reason.INSTANCE_RESOURCE_MISSING);
        if(!KIND.equals(r.getAdapterKind())||!KIND.equals(r.getResourceKind()))throw rejected(Reason.INSTANCE_KIND_MISMATCH);
        if(r.getResourceIdentifiers()==null)throw rejected(Reason.IDENTIFIERS_MISSING);
        Map<String,String> values=new LinkedHashMap<>();
        for(ResourceIdentifierConfig id:r.getResourceIdentifiers()){
            if(id==null)throw rejected(Reason.IDENTIFIER_NULL);
            if(id.getKey()==null)throw rejected(Reason.IDENTIFIER_KEY_NULL);
            if(id.isPartOfUniqueness())throw rejected(Reason.IDENTIFIER_UNIQUE);
            if(values.containsKey(id.getKey()))throw rejected(Reason.IDENTIFIER_DUPLICATE);
            if(!(DIAGNOSTIC_FIELDS.contains(id.getKey())||Set.of("execution_mode","fixture_json").contains(id.getKey())))throw rejected(Reason.IDENTIFIER_UNKNOWN);
            values.put(id.getKey(),id.getValue());
        }
        String mode=values.get("execution_mode");
        if(values.containsKey("execution_mode")){
            if(mode==null)throw rejected(Reason.EXECUTION_MODE_NULL);
            // SDK schema synchronization fills absent optional strings with exact empty values.
            // Empty selects only the fully validated fixture path below; never diagnostic fallback.
            if(!mode.isEmpty()&&!Set.of("fixture","read_only_diagnostic").contains(mode))throw rejected(Reason.EXECUTION_MODE_UNKNOWN);
        }
        if("read_only_diagnostic".equals(mode)){
            if(values.get("fixture_json")!=null&&!values.get("fixture_json").isEmpty())throw rejected(Reason.DIAGNOSTIC_FIXTURE_PRESENT);
            if(DiagnosticRuntime.validateSameKindConfiguration(config).state==DiagnosticRuntime.State.FAILED)throw rejected(Reason.DIAGNOSTIC_CONFIGURATION_INVALID);
            return Mode.DIAGNOSTIC;
        }
        for(String key:DIAGNOSTIC_FIELDS)if(values.get(key)!=null&&!values.get(key).isEmpty())throw rejected(Reason.FIXTURE_DIAGNOSTIC_PRESENT);
        if(r.getResourceCredential()!=null||"".equals(mode)&&r.getCredentialUuid()!=null)throw rejected(Reason.FIXTURE_CREDENTIAL_PRESENT);
        try{RuntimeFixture.parse(values.get("fixture_json"));}catch(RuntimeException ex){throw rejected(Reason.FIXTURE_INVALID);}
        return Mode.FIXTURE;
    }
    private static List<Object> stamp(AdapterConfig a){
        List<Object>s=new ArrayList<>();ResourceConfig r=a.getAdapterInstResource();
        s.add(a);s.add(a.getAdapterKindKey());s.add(a.getAdapterInstResourceId());s.add(a.getAdapterInstResourceUuid());s.add(r);
        s.add(r.getAdapterKind());s.add(r.getResourceKind());s.add(r.getResourceCredential());s.add(a.getAdapterCredentials());
        if(r.getResourceIdentifiers().stream().anyMatch(id->"execution_mode".equals(id.getKey())&&"".equals(id.getValue())))s.add(r.getCredentialUuid());
        for(ResourceIdentifierConfig id:r.getResourceIdentifiers()){s.add(id.getKey());s.add(id.getValue());s.add(id.isPartOfUniqueness());}
        return Collections.unmodifiableList(s);
    }
    private AdapterStatus status(boolean ok,String code){return new AdapterStatus(instanceId,ok?AdapterStatus.Status.as_succeeded:AdapterStatus.Status.as_failed,"RDM_DISPATCHER "+code);}
    private void retire(){valid=false;source=null;stamp=null;diagnostic.cancel();if(fixture!=null){try{fixture.stopCollection();}catch(RuntimeException|LinkageError ignored){}try{fixture.discard();}catch(RuntimeException|LinkageError ignored){}fixture=null;}}
    private boolean current(){
        if(!valid||closed||stopped)return false;
        try{if(!stamp.equals(stamp(source))||selection(source)!=latched){retire();return false;}return true;}
        catch(RuntimeException|LinkageError ex){retire();return false;}
    }
    /** SDK deltas omit the instance resource; preserve its validated snapshot and forward the delta unchanged. */
    private AdapterStatus configureFixtureDelta(AdapterConfig config){
        if(!KIND.equals(config.getAdapterKindKey()))throw rejected(Reason.ADAPTER_KIND_MISMATCH);
        if(latched==Mode.DIAGNOSTIC)throw rejected(Reason.DELTA_DIAGNOSTIC_UNSUPPORTED);
        if(latched!=Mode.FIXTURE||!current()||fixture==null)throw rejected(Reason.DELTA_REQUIRES_ACTIVE_FIXTURE);
        if(!Objects.equals(instanceId,config.getAdapterInstResourceId())
                ||config.getAdapterInstResourceUuid()!=null&&!Objects.equals(instanceUuid,config.getAdapterInstResourceUuid())
                ||config.getAdapterInstResourceKey()!=null&&!source.getAdapterInstResourceKey().equals(config.getAdapterInstResourceKey()))
            throw rejected(Reason.INSTANCE_IDENTITY_CHANGED);
        // AdapterCache does not apply these fields on ordinary instance-less updates.
        // Reject rather than imply a credential, custom-configuration or trust update succeeded.
        if(config.getAdapterCredentials()!=null)throw rejected(Reason.DELTA_CREDENTIAL_UNSUPPORTED);
        if(config.getAdapterCustomConfig()!=null)throw rejected(Reason.DELTA_CUSTOM_CONFIGURATION_UNSUPPORTED);
        if(config.getAdapterExtendedConfig()!=null)throw rejected(Reason.DELTA_EXTENDED_CONFIGURATION_UNSUPPORTED);
        AdapterStatus result;
        try{result=fixture.configure(config);}catch(RuntimeException ex){throw rejected(Reason.FIXTURE_CONFIGURATION_FAILED);}
        if(result==null||result.getStatus()==AdapterStatus.Status.as_failed)throw rejected(Reason.FIXTURE_CONFIGURATION_FAILED);
        if(!current())throw rejected(Reason.CONFIGURATION_RETIRED);
        return result;
    }
    @Override public AdapterStatus configure(AdapterConfig config){synchronized(lock){
        if(closed)return status(false,"DISCARDED");
        try{
            requireCollectorIdentity(config);
            if(config.getAdapterInstResource()==null)return configureFixtureDelta(config);
            valid=false;source=null;stamp=null;
            Mode next=selection(config);
            if(latched!=null&&next!=latched)throw rejected(Reason.MODE_CHANGED);
            if(latched!=null&&(!Objects.equals(instanceId,config.getAdapterInstResourceId())
                    ||!Objects.equals(instanceUuid,config.getAdapterInstResourceUuid())))throw rejected(Reason.INSTANCE_IDENTITY_CHANGED);
            instanceId=config.getAdapterInstResourceId();instanceUuid=config.getAdapterInstResourceUuid();latched=next;stopped=false;
            AdapterStatus result;
            if(next==Mode.DIAGNOSTIC){DiagnosticRuntime.Report r=diagnostic.configure(config);result=status(r.state!=DiagnosticRuntime.State.FAILED&&r.state!=DiagnosticRuntime.State.BUSY,r.toString());}
            else{
                if(fixture==null)try{fixture=fixtureFactory.get();}catch(RuntimeException ex){throw rejected(Reason.FIXTURE_CONSTRUCTION_FAILED);}
                if(fixture==null)throw rejected(Reason.FIXTURE_CONSTRUCTION_FAILED);
                try{result=fixture.configure(config);}catch(RuntimeException ex){throw rejected(Reason.FIXTURE_CONFIGURATION_FAILED);}
            }
            if(result==null||result.getStatus()==AdapterStatus.Status.as_failed){retire();return status(false,rejection(next==Mode.DIAGNOSTIC?Reason.DIAGNOSTIC_CONFIGURATION_FAILED:Reason.FIXTURE_CONFIGURATION_FAILED));}
            source=config;stamp=stamp(config);valid=true;return result;
        }catch(Rejected ex){retire();return status(false,rejection(ex.reason));}
        catch(LinkageError ex){retire();return status(false,rejection(Reason.PLATFORM_LINKAGE_FAILURE));}
        catch(RuntimeException ex){retire();return status(false,rejection(Reason.INTERNAL_CONFIGURATION_FAILURE));}
    }}
    @Override public AdapterDescribe describe(){
        try{
            // Actual SDK path contract: ADAPTER_HOME/<kind>/conf/describe.xml, never fixture or secret input.
            AdapterDescribe d=AdapterDescribe.make(AdapterBase.getAdapterDescribeFile(KIND,"describe.xml").toString());if(d==null)throw new IllegalStateException();return d;
        }catch(Exception|LinkageError ex){throw new IllegalStateException("RDM_DISPATCHER_DESCRIPTOR_UNAVAILABLE");}
    }
    private CollectResult empty(String code){CollectResult r=new CollectResult(KIND,instanceId);r.getAdapterStatus().setStatus(AdapterStatus.Status.as_failed,"RDM_DISPATCHER "+code);return r;}
    @Override public CollectResult collect(){
        synchronized(lock){if(!current())return empty("NOT_CONFIGURED");if(latched==Mode.FIXTURE){try{CollectResult result=fixture.collect();return current()?result:empty("CONFIGURATION_RETIRED");}catch(RuntimeException|LinkageError ex){retire();return empty("FIXTURE_FAILED");}}}
        DiagnosticRuntime.Report report=diagnostic.executeOnce();CollectResult r=new CollectResult(KIND,instanceId);
        r.getAdapterStatus().setStatus(report.previousRunPassed()||report.state==DiagnosticRuntime.State.DISABLED||report.state==DiagnosticRuntime.State.NOT_AUTHORIZED?AdapterStatus.Status.as_succeeded:AdapterStatus.Status.as_failed,report.toString());return r;
    }
    @Override public DiscoveryResult discover(DiscoveryParam param){synchronized(lock){
        if(!current()||latched==Mode.DIAGNOSTIC)return new DiscoveryResult(KIND,instanceId);
        try{DiscoveryResult result=fixture.discover(param);return current()?result:new DiscoveryResult(KIND,instanceId);}catch(RuntimeException|LinkageError ex){retire();return new DiscoveryResult(KIND,instanceId);}
    }}
    /** Unsaved test settings never configure, arm or change the installed route. */
    @Override public boolean test(TestParam param){
        if(param==null)return false;try{requireCollectorIdentity(param.getAdapterConfig());selection(param.getAdapterConfig());param.setErrorMsg("RDM_DISPATCHER CONFIGURATION_ONLY_NOT_RUNTIME_PROOF");return true;}
        catch(Rejected ex){param.setErrorMsg("RDM_DISPATCHER "+rejection(ex.reason));return false;}
        catch(LinkageError ex){param.setErrorMsg("RDM_DISPATCHER "+rejection(Reason.PLATFORM_LINKAGE_FAILURE));return false;}
        catch(RuntimeException ex){param.setErrorMsg("RDM_DISPATCHER "+rejection(Reason.INTERNAL_CONFIGURATION_FAILURE));return false;}
    }
    @Override public CheckCertificateResult checkCertificate(CheckCertificateParam ignored){CheckCertificateResult r=new CheckCertificateResult();r.setState(CheckCertificateResult.CheckCertificateResultState.NOT_SUPPORTED);r.setMessage("RDM_DISPATCHER MANAGED_CA_DELIVERY_REQUIRED");return r;}
    @Override public boolean changePassword(PasswordParam ignored){return false;}
    @Override public void stopCollection(){synchronized(lock){stopped=true;diagnostic.cancel();if(fixture!=null)try{fixture.stopCollection();}catch(RuntimeException|LinkageError ignored){}}}
    @Override public AdapterStatus discard(){synchronized(lock){retire();closed=true;return status(true,"DISCARDED");}}
}
