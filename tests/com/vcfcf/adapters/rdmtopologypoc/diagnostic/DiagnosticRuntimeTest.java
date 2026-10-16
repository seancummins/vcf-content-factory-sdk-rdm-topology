package com.vcfcf.adapters.rdmtopologypoc.diagnostic;

import com.integrien.alive.common.adapter3.*;
import com.integrien.alive.common.adapter3.config.*;
import com.vcfcf.adapters.rdmtopologypoc.discovery.*;
import com.vcfcf.adapters.rdmtopologypoc.ops.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** SDK carriers and actual empty SDK payloads. Synthetic identities only, no platform decryption substitute. */
public final class DiagnosticRuntimeTest {
    static final String VC="11111111-1111-1111-1111-111111111111",VM="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    static int checks;static void check(boolean b){if(!b)throw new AssertionError("synthetic diagnostic assertion");checks++;}
    static AdapterConfig config(){
        AdapterConfig a=new AdapterConfig(DiagnosticRuntime.KIND,17);ResourceConfig r=new ResourceConfig(17);
        r.setAdapterKind(DiagnosticRuntime.KIND);r.setResourceKind(DiagnosticRuntime.KIND);r.setResourceName("synthetic-diagnostic");
        String[][] values={{"vcenter_endpoint","https://vc.synthetic.invalid/sdk"},{"trust_mode","jvm"},{"discovery_scope","single_vm_current_host"},
                {"ops_endpoint","https://ops.synthetic.invalid/suite-api"},{"ops_trust_mode","jvm"},{"target_vcenter_uuid",VC},
                {"target_vm_moid","vm-71"},{"target_disk_key","2001"},{"diagnostic_enabled","true"},{"authorize_once","true"}};
        for(String[] pair:values)r.addResourceIdentifier(new ResourceIdentifierConfig(pair[0],pair[1],false));
        CredentialConfig c=new CredentialConfig();c.setCredentialKey("rdm_vcenter_readonly");
        c.addCredentialField(new CredentialFieldConfig("username","SENSITIVE-USER",false));c.addCredentialField(new CredentialFieldConfig("password","SENSITIVE-CIPHERTEXT",true));
        r.setResourceCredential(c);a.setAdapterInstResource(r);return a;
    }
    static void set(AdapterConfig a,String key,String value){for(ResourceIdentifierConfig id:a.getAdapterInstResource().getResourceIdentifiers())if(id.getKey().equals(key))id.updateValue(value);}
    static DiscoveryGraph graph(String vc,String moid,String disk,boolean qualified,boolean partial)throws DiscoveryException{
        DiscoveryGraph.VmKey vm=new DiscoveryGraph.VmKey("VMWARE","VirtualMachine","SENSITIVE-NAME",List.of(
                new DiscoveryGraph.Identifier("VMEntityVCID",vc,true),new DiscoveryGraph.Identifier("VMEntityObjectID",moid,true),new DiscoveryGraph.Identifier("empty","",false)));
        byte[] d={1,3,0,8,0x50,0,0,0,0,0,0,1};
        DiscoveryGraph.Lun lun=qualified?DiscoveryGraph.qualify("opaque","opaque","naa.5000000000000001",List.of(new DiscoveryGraph.Descriptor("NAA",-1,d)),Map.of("vendor","Synthetic")):null;
        return new DiscoveryGraph(List.of(new DiscoveryGraph.Attachment(vm,vc,VM,disk,"SENSITIVE-HOST",lun,qualified?null:DiscoveryException.Code.LUN_UNRESOLVED)),partial?List.of(DiscoveryException.Code.INCOMPLETE):List.of());
    }
    static DiscoveryGraph good()throws DiscoveryException{return graph(VC,"vm-71","2001",true,false);}
    static void empty(CollectResult result){
        check(result.getResourceCollectResultMap().isEmpty());check(result.getDiscoveryResult()==null);
        check(result.getAdapterStatus().getResourceStatusMap().isEmpty());
        check(!result.getAdapterStatus().getMessage().contains("SENSITIVE")&&!result.getAdapterStatus().getMessage().contains(VC));
    }
    public static void main(String[]args)throws Exception{
        AtomicInteger calls=new AtomicInteger();DiagnosticRuntime.Work success=c->{calls.incrementAndGet();return good();};
        try(DiagnosticRuntime d=new DiagnosticRuntime(success,1000)){check(d.latest().state==DiagnosticRuntime.State.DISABLED);check(d.executeOnce().state==DiagnosticRuntime.State.DISABLED);check(calls.get()==0);}
        for(String flag:List.of("false","TRUE","SENSITIVE",""))try(DiagnosticRuntime d=new DiagnosticRuntime(success,1000)){
            AdapterConfig c=config();set(c,"diagnostic_enabled",flag);DiagnosticRuntime.Report r=d.configure(c);check(r.state!=(DiagnosticRuntime.State.READY));d.executeOnce();check(calls.get()==0);
        }
        try(DiagnosticRuntime d=new DiagnosticRuntime(success,1000)){AdapterConfig c=config();set(c,"authorize_once","false");check(d.configure(c).state==DiagnosticRuntime.State.NOT_AUTHORIZED);check(d.executeOnce().state==DiagnosticRuntime.State.NOT_AUTHORIZED);check(calls.get()==0);}
        for(String field:List.of("target_vcenter_uuid","target_vm_moid","target_disk_key","ops_endpoint","vcenter_endpoint","trust_mode","ops_trust_mode","discovery_scope","authorize_once")){
            try(DiagnosticRuntime d=new DiagnosticRuntime(success,1000)){AdapterConfig c=config();set(c,field,"SENSITIVE");check(d.configure(c).state==DiagnosticRuntime.State.FAILED);d.executeOnce();check(calls.get()==0);}
        }
        AtomicInteger secretReads=new AtomicInteger();AdapterConfig guarded=config();
        CredentialConfig guardedCredential=new CredentialConfig();guardedCredential.setCredentialKey("rdm_vcenter_readonly");
        guardedCredential.addCredentialField(new CredentialFieldConfig("username","SENSITIVE",false){@Override public String getValue(){secretReads.incrementAndGet();throw new IllegalStateException("SENSITIVE");}});
        guardedCredential.addCredentialField(new CredentialFieldConfig("password","SENSITIVE",true){@Override public String getValue(){secretReads.incrementAndGet();throw new IllegalStateException("SENSITIVE");}});
        guarded.getAdapterInstResource().setResourceCredential(guardedCredential);
        try(DiagnosticRuntime d=new DiagnosticRuntime(success,1000)){
            ReadOnlyDiagnosticAdapter adapter=new ReadOnlyDiagnosticAdapter(d);check(adapter.configure(guarded).getStatus()==AdapterStatus.Status.as_succeeded);
            int before=calls.get();TestParam unsaved=new TestParam(config());check(adapter.test(unsaved));check(secretReads.get()==0&&calls.get()==before);
            check(d.latest().state==DiagnosticRuntime.State.READY);check(d.executeOnce().state==DiagnosticRuntime.State.PASS);check(calls.get()==before+1);check(secretReads.get()==0);
        }
        for(String broken:List.of("missing-field","duplicate-field","unique-configuration","missing-credential","wrong-credential-kind","wrong-password-flag","wrong-adapter-kind"))try(DiagnosticRuntime d=new DiagnosticRuntime(success,1000)){
            AdapterConfig a=config();ResourceConfig r=a.getAdapterInstResource();
            if(broken.equals("missing-field"))r.getResourceIdentifiers().remove(0);
            if(broken.equals("duplicate-field"))r.getResourceIdentifiers().add(new ResourceIdentifierConfig("authorize_once","true",false));
            if(broken.equals("unique-configuration")){r.getResourceIdentifiers().remove(0);r.addResourceIdentifier(new ResourceIdentifierConfig("vcenter_endpoint","https://vc.synthetic.invalid/sdk",true));}
            if(broken.equals("missing-credential"))r.setResourceCredential(null);
            if(broken.equals("wrong-credential-kind"))r.getResourceCredential().setCredentialKey("wrong");
            if(broken.equals("wrong-password-flag")){r.getResourceCredential().getCredenialFields().clear();r.getResourceCredential().addCredentialField(new CredentialFieldConfig("username","synthetic",false));r.getResourceCredential().addCredentialField(new CredentialFieldConfig("password","synthetic",false));}
            if(broken.equals("wrong-adapter-kind"))a.setAdapterKindKey("VMWARE");
            int before=calls.get();check(d.configure(a).state==DiagnosticRuntime.State.FAILED);d.executeOnce();check(calls.get()==before);
        }
        try(DiagnosticRuntime d=new DiagnosticRuntime(success,1000)){
            int before=calls.get();
            check(d.configure(config()).state==DiagnosticRuntime.State.READY);DiagnosticRuntime.Report r=d.executeOnce();check(r.state==DiagnosticRuntime.State.PASS&&r.mappingCount==1&&r.vmCount==1&&r.lunCount==1);
            check(calls.get()==before+1);check(d.executeOnce().state==DiagnosticRuntime.State.CONSUMED);check(d.configure(config()).state==DiagnosticRuntime.State.CONSUMED);check(calls.get()==before+1);
        }
        for(OpsException.Code code:OpsException.Code.values())try(DiagnosticRuntime d=new DiagnosticRuntime(c->{throw new OpsException(code);},1000)){
            d.configure(config());DiagnosticRuntime.Report r=d.executeOnce();check(r.state==DiagnosticRuntime.State.FAILED&&r.stage==DiagnosticRuntime.Stage.OPERATIONS&&r.toString().contains(code.name()));check(!r.toString().contains("SENSITIVE"));
        }
        for(DiscoveryException.Code code:DiscoveryException.Code.values())try(DiagnosticRuntime d=new DiagnosticRuntime(c->{throw new DiscoveryException(code);},1000)){
            d.configure(config());DiagnosticRuntime.Report r=d.executeOnce();check(r.state==DiagnosticRuntime.State.FAILED&&r.toString().contains(code.name()));
        }
        List<DiagnosticRuntime.Work> invalid=List.of(c->null,c->new DiscoveryGraph(List.of(),List.of()),c->graph(VC,"vm-71","2002",true,false),
                c->graph(VC,"vm-72","2001",true,false),c->graph(VC,"vm-71","2001",false,false),c->graph(VC,"vm-71","2001",true,true),
                c->{throw new IllegalStateException("SENSITIVE-CREDENTIAL");},c->{throw new NoClassDefFoundError("SENSITIVE-CREDENTIAL");});
        for(DiagnosticRuntime.Work work:invalid)try(DiagnosticRuntime d=new DiagnosticRuntime(work,1000)){d.configure(config());DiagnosticRuntime.Report r=d.executeOnce();check(r.state==DiagnosticRuntime.State.FAILED);check(!r.toString().contains("SENSITIVE"));}
        for(String field:List.of("ops_endpoint","vcenter_endpoint","target_vm_moid","authorize_once"))try(DiagnosticRuntime d=new DiagnosticRuntime(success,1000)){
            AdapterConfig c=config();d.configure(c);set(c,field,"SENSITIVE");int before=calls.get();check(d.executeOnce().state==DiagnosticRuntime.State.FAILED);check(calls.get()==before);
        }
        try(DiagnosticRuntime d=new DiagnosticRuntime(c->{set(c.source,"target_disk_key","2002");return good();},1000)){
            d.configure(config());check(d.executeOnce().state==DiagnosticRuntime.State.FAILED);
        }
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger workers=new AtomicInteger();
        DiagnosticRuntime blocked=new DiagnosticRuntime(c->{workers.incrementAndGet();started.countDown();while(release.getCount()>0)try{release.await();}catch(InterruptedException ignored){}return good();},40);
        blocked.configure(config());DiagnosticRuntime.Report timeout=blocked.executeOnce();check(started.await(1,TimeUnit.SECONDS));check(timeout.toString().contains("TIMEOUT"));
        check(blocked.configure(config()).state==DiagnosticRuntime.State.BUSY);check(workers.get()==1);blocked.close();release.countDown();check(blocked.executeOnce().state==DiagnosticRuntime.State.DISCARDED);
        DiagnosticRuntime cancelled=new DiagnosticRuntime(c->{try{Thread.sleep(10000);}catch(InterruptedException ex){Thread.currentThread().interrupt();}return good();},2000);
        cancelled.configure(config());Thread t=new Thread(cancelled::executeOnce);t.start();Thread.sleep(30);cancelled.cancel();t.join(2000);check(!t.isAlive());check(cancelled.latest().state==DiagnosticRuntime.State.CANCELLED);cancelled.close();
        for(DiagnosticRuntime.Work work:List.<DiagnosticRuntime.Work>of(success,c->{throw new OpsException(OpsException.Code.AUTHORIZATION);})){
            ReadOnlyDiagnosticAdapter adapter=new ReadOnlyDiagnosticAdapter(new DiagnosticRuntime(work,1000));adapter.configure(config());empty(adapter.collect());empty(adapter.collect());
            check(adapter.discover(null).isEmpty());check(adapter.checkCertificate(null).getState()==CheckCertificateResult.CheckCertificateResultState.NOT_SUPPORTED);
            TestParam param=new TestParam(config());check(adapter.test(param)&&param.getErrorMsg().contains("NOT_RUNTIME_PROOF"));adapter.stopCollection();empty(adapter.collect());adapter.discard();empty(adapter.collect());
        }
        ReadOnlyDiagnosticAdapter bare=new ReadOnlyDiagnosticAdapter();empty(bare.collect());
        try{check(bare.describe()!=null&&bare.describe().getKey().equals(DiagnosticRuntime.KIND));}
        catch(IllegalStateException ex){check(ex.getMessage().equals("RDM_DIAGNOSTIC_DESCRIPTOR_RUNTIME_DEPENDENCY_UNAVAILABLE")&&ex.getCause()==null);}
        bare.discard();
        // Actual SDK password decryption is unavailable in this test JVM; no fallback must be invented.
        try(DiagnosticRuntime d=new DiagnosticRuntime()){d.configure(config());DiagnosticRuntime.Report r=d.executeOnce();check(r.state==DiagnosticRuntime.State.FAILED&&r.toString().contains("CREDENTIAL_UNAVAILABLE"));}
        for(String mutation:List.of("","no-rdm","two-rdm","motion","missing-property","wrong-join","naa-conflict","duplicate-lun","missing-lu-designator","cleanup","wrong-vcenter")){
            DiagnosticSoapFixture wire=new DiagnosticSoapFixture();wire.mutation=mutation;
            try(DiagnosticRuntime d=new DiagnosticRuntime(c->DiagnosticRuntime.compose(c,DiagnosticOpsFixture.provider(0,false),resolver->
                    new VimDiscoveryClient(wire,resolver,c.vc,c.moid).discover("synthetic-user","SENSITIVE-SYNTHETIC".toCharArray())),1000)){
                d.configure(config());DiagnosticRuntime.Report r=d.executeOnce();check(r.state==(mutation.isEmpty()?DiagnosticRuntime.State.PASS:DiagnosticRuntime.State.FAILED));
                check(!wire.calls.contains("CreateContainerView")&&!wire.calls.contains("DestroyView"));check(!r.toString().contains(VC)&&!r.toString().contains("SENSITIVE"));
            }
        }
        for(int status:new int[]{401,403,302,500})try(DiagnosticRuntime d=new DiagnosticRuntime(c->DiagnosticRuntime.compose(c,
                DiagnosticOpsFixture.provider(status,false),resolver->{throw new AssertionError("SOAP cannot run after failed Operations identity");}),1000)){
            d.configure(config());DiagnosticRuntime.Report r=d.executeOnce();check(r.state==DiagnosticRuntime.State.FAILED&&r.stage==DiagnosticRuntime.Stage.OPERATIONS);
            check(r.toString().contains(status==401?"AUTHENTICATION":status==403?"AUTHORIZATION":"INVALID_RESPONSE"));
        }
        try(DiagnosticRuntime d=new DiagnosticRuntime(c->DiagnosticRuntime.compose(c,DiagnosticOpsFixture.provider(0,true),resolver->{throw new AssertionError("cleanup failure must fail closed before SOAP");}),1000)){
            d.configure(config());DiagnosticRuntime.Report r=d.executeOnce();check(r.stage==DiagnosticRuntime.Stage.OPERATIONS_CLEANUP&&r.toString().contains("TLS"));
        }
        for(Class<?> production:List.of(DiagnosticRuntime.class,ReadOnlyDiagnosticAdapter.class)){
            String resource="/"+production.getName().replace('.','/')+".class";
            try(java.io.InputStream in=production.getResourceAsStream(resource)){
                String bytecode=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1);
                for(String forbidden:List.of("addNewResource","addRelationships","setRelationships","ResourceCollection","RelationshipBuilder","addEvent","addResourceCollectResult","addProperty","MetricData"))check(!bytecode.contains(forbidden));
            }
        }
        System.out.println("DiagnosticRuntimeTest: PASS ("+checks+" synthetic assertions; sanitized output only)");
    }
}
