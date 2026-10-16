package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.*;
import com.integrien.alive.common.adapter3.config.*;
import com.integrien.alive.common.adapter3.describe.*;
import java.nio.file.*;
import java.util.*;

/** Genuine SDK schema-produced legacy carrier, separate from observed collector callback evidence. */
final class E493ColdStartTest {
    private static int checks;
    private static void check(boolean b){if(!b)throw new AssertionError("E493 cold assertion "+checks);checks++;}
    static void synchronizeWithDescriptor(AdapterConfig config,Path descriptor)throws Exception{
        AdapterDescribe describe;
        try(java.io.InputStream in=Files.newInputStream(descriptor)){describe=AdapterDescribe.make(in);}
        ResourceKindDescribe instance=describe.getResourceKinds().stream().filter(k->RdmTopologyDispatcher.KIND.equals(k.getKey())).findFirst().orElseThrow();
        ResourceKey template=new ResourceKey("synthetic-template",RdmTopologyDispatcher.KIND,RdmTopologyDispatcher.KIND);
        for(ResourceIdentifierDescribe id:instance.getResourceIdentifiers())template.addIdentifier(new ResourceIdentifierConfig(id.getKey(),"",id.getIdentType()));
        ResourceKindIdentifiers schema=new ResourceKindIdentifiers();schema.addResourceKindIdentifiers(template);
        if(!schema.verifyResourceIdentifiers(config.getAdapterInstResourceKey()))throw new AssertionError("SDK schema synchronization failed");
    }
    private static List<Object> snapshot(AdapterConfig c){
        List<Object> stamp=new ArrayList<>();ResourceConfig r=c.getAdapterInstResource();stamp.add(c);stamp.add(r);stamp.add(r.getResourceKey());stamp.add(r.getResourceCredential());stamp.add(r.getCredentialUuid());stamp.add(c.getAdapterCredentials());
        for(ResourceIdentifierConfig id:r.getResourceIdentifiers()){stamp.add(id);stamp.add(id.getKey());stamp.add(id.getValue());stamp.add(id.isPartOfUniqueness());}return stamp;
    }
    private static RdmTopologyDispatcher dispatcher(boolean collector)throws Exception{
        return collector?RdmTopologyDispatcher.class.getConstructor(String.class,Integer.class).newInstance("/tmp/rdm-cold-synthetic",Integer.valueOf(17)):new RdmTopologyDispatcher();
    }
    private static void empty(RdmTopologyDispatcher d){CollectResult r=d.collect();check(r.getResourceCollectResultMap().isEmpty());check(r.getDiscoveryResult()==null);check(r.getAdapterStatus().getResourceStatusMap().isEmpty());check(d.discover(null).isEmpty());}
    private static void failed(RdmTopologyDispatcher d,AdapterConfig c,String reason){List<Object> before=snapshot(c);AdapterStatus s=d.configure(c);check(s.getStatus()==AdapterStatus.Status.as_failed);check(s.getMessage().equals("RDM_DISPATCHER INVALID_CONFIGURATION reason="+reason));check(before.equals(snapshot(c)));empty(d);}
    private static void exercise(Path descriptor)throws Exception{
        for(boolean collector:List.of(false,true))for(boolean fromNull:List.of(false,true)){
            AdapterConfig c=DispatcherTest.fixture();
            if(fromNull){DispatcherTest.add(c,"execution_mode","fixture");DispatcherTest.set(c,"execution_mode",null);}
            String fixture=c.getAdapterInstResource().getResourceIdentifiers().stream().filter(i->"fixture_json".equals(i.getKey())).findFirst().orElseThrow().getValue();
            synchronizeWithDescriptor(c,descriptor);check(c.getAdapterInstResource().getResourceIdentifiers().size()==12);
            for(ResourceIdentifierConfig id:c.getAdapterInstResource().getResourceIdentifiers()){check(!id.isPartOfUniqueness());check("fixture_json".equals(id.getKey())?fixture.equals(id.getValue()):"".equals(id.getValue()));}
            List<Object> before=snapshot(c);RdmTopologyDispatcher d=dispatcher(collector);
            try{check(d.configure(c).getStatus()!=AdapterStatus.Status.as_failed);check(before.equals(snapshot(c)));
                for(int repeat=0;repeat<2;repeat++){CollectResult r=d.collect();check(r.getDiscoveryResult()!=null&&!r.getDiscoveryResult().isEmpty());check(before.equals(snapshot(c)));}
                check(d.configure(c).getStatus()!=AdapterStatus.Status.as_failed);check(before.equals(snapshot(c)));
            }finally{d.discard();}
        }
        for(boolean collector:List.of(false,true))for(String problem:List.of("null_mode","whitespace","case","unknown_mode","duplicate_mode","duplicate_fixture","unique_mode","unknown_field","duplicate_diagnostic","unique_diagnostic","diagnostic_false","diagnostic_populated","credential_empty","credential_populated","credential_reference","invalid_fixture")){
            AdapterConfig c=DispatcherTest.fixture();DispatcherTest.add(c,"execution_mode","");String reason="EXECUTION_MODE_UNKNOWN";
            if(problem.equals("null_mode")){DispatcherTest.set(c,"execution_mode",null);reason="EXECUTION_MODE_NULL";}
            if(problem.equals("whitespace"))DispatcherTest.set(c,"execution_mode"," ");
            if(problem.equals("case"))DispatcherTest.set(c,"execution_mode","FIXTURE");
            if(problem.equals("unknown_mode"))DispatcherTest.set(c,"execution_mode","SENSITIVE");
            if(problem.equals("duplicate_mode")){c.getAdapterInstResource().getResourceIdentifiers().add(new ResourceIdentifierConfig("execution_mode","",false));reason="IDENTIFIER_DUPLICATE";}
            if(problem.equals("duplicate_fixture")){c.getAdapterInstResource().getResourceIdentifiers().add(new ResourceIdentifierConfig("fixture_json",RuntimeFixtureTest.JSON,false));reason="IDENTIFIER_DUPLICATE";}
            if(problem.equals("unique_mode")){c.getAdapterInstResource().getResourceIdentifiers().removeIf(i->"execution_mode".equals(i.getKey()));c.getAdapterInstResource().addResourceIdentifier(new ResourceIdentifierConfig("execution_mode","",true));reason="IDENTIFIER_UNIQUE";}
            if(problem.equals("unknown_field")){DispatcherTest.add(c,"SENSITIVE-unknown","");reason="IDENTIFIER_UNKNOWN";}
            if(problem.equals("duplicate_diagnostic")){DispatcherTest.add(c,"authorize_once","");c.getAdapterInstResource().getResourceIdentifiers().add(new ResourceIdentifierConfig("authorize_once","",false));reason="IDENTIFIER_DUPLICATE";}
            if(problem.equals("unique_diagnostic")){c.getAdapterInstResource().addResourceIdentifier(new ResourceIdentifierConfig("authorize_once","",true));reason="IDENTIFIER_UNIQUE";}
            if(problem.equals("diagnostic_false")||problem.equals("diagnostic_populated")){DispatcherTest.add(c,"diagnostic_enabled",problem.equals("diagnostic_false")?"false":"SENSITIVE");reason="FIXTURE_DIAGNOSTIC_PRESENT";}
            if(problem.equals("credential_empty")){c.getAdapterInstResource().setResourceCredential(new CredentialConfig());reason="FIXTURE_CREDENTIAL_PRESENT";}
            if(problem.equals("credential_populated")){CredentialConfig credential=new CredentialConfig();credential.setCredentialKey("rdm_vcenter_readonly");credential.addCredentialField(new CredentialFieldConfig("username","SENSITIVE",false));c.getAdapterInstResource().setResourceCredential(credential);reason="FIXTURE_CREDENTIAL_PRESENT";}
            if(problem.equals("credential_reference")){c.getAdapterInstResource().setCredentialUuid(UUID.fromString("44444444-4444-4444-4444-444444444444"));reason="FIXTURE_CREDENTIAL_PRESENT";}
            if(problem.equals("invalid_fixture")){DispatcherTest.set(c,"fixture_json","SENSITIVE");reason="FIXTURE_INVALID";}
            RdmTopologyDispatcher d=dispatcher(collector);try{failed(d,c,reason);}finally{d.discard();}
        }
        for(boolean collector:List.of(false,true))for(String mode:Arrays.asList(null,"fixture","")){
            AdapterConfig c=DispatcherTest.fixture();if(mode!=null)DispatcherTest.add(c,"execution_mode",mode);
            c.getAdapterInstResource().setCredentialUuid(UUID.fromString("44444444-4444-4444-4444-444444444444"));
            RdmTopologyDispatcher d=dispatcher(collector);
            try{if("".equals(mode))failed(d,c,"FIXTURE_CREDENTIAL_PRESENT");else{
                List<Object> before=snapshot(c);check(d.configure(c).getStatus()!=AdapterStatus.Status.as_failed);check(before.equals(snapshot(c)));
                check(d.collect().getDiscoveryResult()!=null);
            }}finally{d.discard();}
        }
        for(boolean collector:List.of(false,true)){
            AdapterConfig c=DispatcherTest.fixture();DispatcherTest.add(c,"execution_mode","");RdmTopologyDispatcher d=dispatcher(collector);
            try{check(d.configure(c).getStatus()!=AdapterStatus.Status.as_failed);
                c.getAdapterInstResource().setCredentialUuid(UUID.fromString("44444444-4444-4444-4444-444444444444"));empty(d);
            }finally{d.discard();}
        }
    }
    static void run(Path descriptor)throws Exception{
        java.io.PrintStream out=System.out,err=System.err;
        try(java.io.PrintStream sink=new java.io.PrintStream(java.io.OutputStream.nullOutputStream())){
            System.setOut(sink);System.setErr(sink);exercise(descriptor);
        }finally{System.setOut(out);System.setErr(err);}
        out.println("E493ColdStartTest: PASS "+checks+" assertions (actual SDK schema expansion, strict empty-only fixture selection)");
    }
}
