package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.*;
import com.integrien.alive.common.adapter3.config.*;
import com.vcfcf.adapters.rdmtopologypoc.diagnostic.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real SDK carrier regressions; delegate-seam assertions supplement the actual fixture lifecycle. */
final class E493DispatcherTest {
    private static int checks;
    private static void check(boolean b){if(!b)throw new AssertionError("E493 assertion "+checks);checks++;}
    private static void empty(RdmTopologyDispatcher d){
        CollectResult r=d.collect();check(r.getResourceCollectResultMap().isEmpty());check(r.getDiscoveryResult()==null);
        check(r.getAdapterStatus().getResourceStatusMap().isEmpty());check(d.discover(null).isEmpty());
    }
    private static void failed(RdmTopologyDispatcher d,AdapterConfig c,String reason){
        AdapterStatus status=d.configure(c);check(status.getStatus()==AdapterStatus.Status.as_failed);
        check(status.getMessage().equals("RDM_DISPATCHER INVALID_CONFIGURATION reason="+reason));empty(d);
    }
    private static RdmTopologyDispatcher dispatcher(DispatcherTest.Delegate f){return new RdmTopologyDispatcher(()->f,DispatcherDiagnosticFixture.success(new AtomicInteger()));}
    private static AdapterConfig full(){AdapterConfig c=DispatcherTest.fixture();c.getAdapterInstResource().setResourceIdUuid(17,UUID.fromString("11111111-1111-1111-1111-111111111111"));return c;}
    private static AdapterConfig delta(AdapterConfig c,boolean nameOnly){
        AdapterConfig delta=new AdapterConfig(RdmTopologyDispatcher.KIND,17);
        if(nameOnly)delta.setAdapterInstResourceId(c.getAdapterInstResourceId(),c.getAdapterInstResourceName());
        else delta.setAdapterInstResourceId(c.getAdapterInstResourceId(),c.getAdapterInstResourceUuid(),c.getAdapterInstResourceKey());
        check(delta.getAdapterInstResource()==null);return delta;
    }
    static void run(){
        for(boolean nameOnly:List.of(false,true)){
            DispatcherTest.Delegate f=new DispatcherTest.Delegate();RdmTopologyDispatcher d=dispatcher(f);AdapterConfig c=full();
            check(d.configure(c).getStatus()!=AdapterStatus.Status.as_failed);AdapterConfig delta=delta(c,nameOnly);
            check(d.configure(delta).getStatus()!=AdapterStatus.Status.as_failed);check(f.received==delta&&f.configs==2);
            check(d.collect()==f.result);check(d.configure(delta).getStatus()!=AdapterStatus.Status.as_failed);check(d.collect()==f.result);
            DispatcherTest.set(c,"fixture_json","SENSITIVE-invalid");failed(d,delta,"DELTA_REQUIRES_ACTIVE_FIXTURE");check(f.configs==3&&f.discards==1);d.discard();
        }
        for(String problem:List.of("cold","id","uuid","key","kind","credentials","custom","extended","extended_only","stopped","retired","full_mutated")){
            DispatcherTest.Delegate f=new DispatcherTest.Delegate();RdmTopologyDispatcher d=dispatcher(f);AdapterConfig c=full();
            if(!problem.equals("cold"))check(d.configure(c).getStatus()!=AdapterStatus.Status.as_failed);
            AdapterConfig delta=delta(c,false);String reason="DELTA_REQUIRES_ACTIVE_FIXTURE";
            if(problem.equals("id")){delta.setAdapterInstResourceId(18,c.getAdapterInstResourceUuid(),c.getAdapterInstResourceKey());reason="INSTANCE_IDENTITY_CHANGED";}
            if(problem.equals("uuid")){delta.setAdapterInstResourceId(17,UUID.fromString("22222222-2222-2222-2222-222222222222"),c.getAdapterInstResourceKey());reason="INSTANCE_IDENTITY_CHANGED";}
            if(problem.equals("key")){ResourceKey key=new ResourceKey(RdmTopologyDispatcher.KIND,RdmTopologyDispatcher.KIND,"different-owner");delta.setAdapterInstResourceId(17,c.getAdapterInstResourceUuid(),key);reason="INSTANCE_IDENTITY_CHANGED";}
            if(problem.equals("kind")){delta.setAdapterKindKey("SENSITIVE-unknown");reason="ADAPTER_KIND_MISMATCH";}
            if(problem.equals("credentials")){delta.setAdapterCredentials(new AdapterCredentialConfig("SENSITIVE-user","SENSITIVE-password"));reason="DELTA_CREDENTIAL_UNSUPPORTED";}
            if(problem.equals("custom")){
                delta.setAdapterCustomConfig(new IAdapterCustomConfig(){public String put(String k,String v){throw new AssertionError();}public String get(String k){throw new AssertionError();}public String remove(String k){throw new AssertionError();}public void clear(){throw new AssertionError();}public int size(){throw new AssertionError();}});
                reason="DELTA_CUSTOM_CONFIGURATION_UNSUPPORTED";
            }
            if(problem.equals("extended")||problem.equals("extended_only")){AdapterConfig.AdapterExtendedConfig e=new AdapterConfig.AdapterExtendedConfig();e.applyOnlyExtendedConfig=problem.equals("extended_only");delta.setAdapterExtendedConfig(e);reason="DELTA_EXTENDED_CONFIGURATION_UNSUPPORTED";}
            if(problem.equals("stopped"))d.stopCollection();
            if(problem.equals("retired"))failed(d,null,"CONFIGURATION_MISSING");
            if(problem.equals("full_mutated"))DispatcherTest.set(c,"fixture_json","SENSITIVE-invalid");
            int configs=f.configs;failed(d,delta,reason);check(f.configs==configs);d.discard();
        }
        for(String failure:List.of("mutate","throw","failed_status")){
            AdapterConfig c=full();DispatcherTest.Delegate f=new DispatcherTest.Delegate(){
                @Override public AdapterStatus configure(AdapterConfig incoming){
                    if(incoming.getAdapterInstResource()==null){
                        if(failure.equals("mutate"))DispatcherTest.set(c,"fixture_json","SENSITIVE-invalid");
                        if(failure.equals("throw"))throw new IllegalStateException("SENSITIVE");
                        if(failure.equals("failed_status"))return new AdapterStatus(17,AdapterStatus.Status.as_failed,"SENSITIVE");
                    }
                    return super.configure(incoming);
                }
            };
            RdmTopologyDispatcher d=dispatcher(f);check(d.configure(c).getStatus()!=AdapterStatus.Status.as_failed);
            failed(d,delta(c,false),failure.equals("mutate")?"CONFIGURATION_RETIRED":"FIXTURE_CONFIGURATION_FAILED");check(f.discards==1);d.discard();
        }
        // Diagnostic instance-less updates cannot select/arm/reuse the publishing delegate.
        AtomicInteger runs=new AtomicInteger(),made=new AtomicInteger();
        RdmTopologyDispatcher diagnostic=new RdmTopologyDispatcher(()->{made.incrementAndGet();throw new AssertionError();},DispatcherDiagnosticFixture.success(runs));
        AdapterConfig config=DispatcherDiagnosticFixture.config();check(diagnostic.configure(config).getStatus()!=AdapterStatus.Status.as_failed);
        failed(diagnostic,delta(config,false),"DELTA_DIAGNOSTIC_UNSUPPORTED");check(made.get()==0&&runs.get()==0);diagnostic.discard();
        // Optional-mode policy is unchanged. Null is retained by the actual SDK updateValue API.
        for(String mode:new String[]{null,"SENSITIVE-unknown","fixture ","READ_ONLY_DIAGNOSTIC"}){
            AdapterConfig c=full();ResourceIdentifierConfig id=new ResourceIdentifierConfig("execution_mode","fixture",false);
            check(c.getAdapterInstResource().addResourceIdentifier(id));id.updateValue(mode);
            check(Objects.equals(mode,id.getValue()));
            DispatcherTest.Delegate f=new DispatcherTest.Delegate();RdmTopologyDispatcher d=dispatcher(f);
            String reason=mode==null?"EXECUTION_MODE_NULL":"EXECUTION_MODE_UNKNOWN";
            failed(d,c,reason);TestParam test=new TestParam(c);check(!d.test(test));check(test.getErrorMsg().equals("RDM_DISPATCHER INVALID_CONFIGURATION reason="+reason));check(f.configs==0);d.discard();
        }
        for(String problem:List.of("duplicate_mode","unknown","unique","null_identifier","null_key","fixture_invalid","mixed","credential")){
            AdapterConfig c=full();List<ResourceIdentifierConfig> ids=c.getAdapterInstResource().getResourceIdentifiers();String reason="IDENTIFIER_DUPLICATE";
            if(problem.equals("duplicate_mode")){DispatcherTest.add(c,"execution_mode","fixture");ids.add(new ResourceIdentifierConfig("execution_mode","",false));}
            if(problem.equals("unknown")){DispatcherTest.add(c,"SENSITIVE-unknown","");reason="IDENTIFIER_UNKNOWN";}
            if(problem.equals("unique")){ids.add(new ResourceIdentifierConfig("execution_mode","",true));reason="IDENTIFIER_UNIQUE";}
            if(problem.equals("null_identifier")){ids.add(null);reason="IDENTIFIER_NULL";}
            if(problem.equals("null_key")){ids.add(new ResourceIdentifierConfig(null,"SENSITIVE",false));reason="IDENTIFIER_KEY_NULL";}
            if(problem.equals("fixture_invalid")){DispatcherTest.set(c,"fixture_json","SENSITIVE-invalid");reason="FIXTURE_INVALID";}
            if(problem.equals("mixed")){DispatcherTest.add(c,"diagnostic_enabled","SENSITIVE");reason="FIXTURE_DIAGNOSTIC_PRESENT";}
            if(problem.equals("credential")){c.getAdapterInstResource().setResourceCredential(DispatcherDiagnosticFixture.config().getAdapterInstResource().getResourceCredential());reason="FIXTURE_CREDENTIAL_PRESENT";}
            DispatcherTest.Delegate f=new DispatcherTest.Delegate();RdmTopologyDispatcher d=dispatcher(f);failed(d,c,reason);check(f.configs==0);d.discard();
        }
        for(String value:new String[]{"",null}){
            DispatcherTest.Delegate f=new DispatcherTest.Delegate();RdmTopologyDispatcher d=dispatcher(f);AdapterConfig c=full();
            for(String key:List.of("vcenter_endpoint","trust_mode","discovery_scope","ops_endpoint","ops_trust_mode","target_vcenter_uuid","target_vm_moid","target_disk_key","diagnostic_enabled","authorize_once")){
                ResourceIdentifierConfig id=new ResourceIdentifierConfig(key,"",false);check(c.getAdapterInstResource().addResourceIdentifier(id));id.updateValue(value);
            }
            check(d.configure(c).getStatus()!=AdapterStatus.Status.as_failed);check(d.collect()==f.result);d.discard();
        }
        for(String failure:List.of("constructor","configure_throw","configure_status","linkage")){
            DispatcherTest.Delegate f=new DispatcherTest.Delegate(){
                @Override public AdapterStatus configure(AdapterConfig c){if(failure.equals("configure_throw"))throw new IllegalStateException("SENSITIVE");if(failure.equals("configure_status"))return new AdapterStatus(17,AdapterStatus.Status.as_failed,"SENSITIVE");return super.configure(c);}
            };
            RdmTopologyDispatcher d=new RdmTopologyDispatcher(()->{if(failure.equals("constructor"))throw new IllegalStateException("SENSITIVE");if(failure.equals("linkage"))throw new NoClassDefFoundError("SENSITIVE");return f;},DispatcherDiagnosticFixture.success(new AtomicInteger()));
            failed(d,full(),failure.equals("constructor")?"FIXTURE_CONSTRUCTION_FAILED":failure.equals("linkage")?"PLATFORM_LINKAGE_FAILURE":"FIXTURE_CONFIGURATION_FAILED");d.discard();
        }
        System.out.println("E493DispatcherTest: PASS "+checks+" assertions (actual SDK delta carriers and fixed rejection codes)");
    }
}
