package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.*;
import com.integrien.alive.common.adapter3.config.*;
import com.integrien.alive.common.adapter3.describe.AdapterDescribe;
import com.vcfcf.adapters.rdmtopologypoc.diagnostic.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real SDK carriers/publication payloads, synthetic delegate seam explicitly distinguished from SDK fixture lifecycle. */
public final class DispatcherTest {
    static int assertions;static void check(boolean b){if(!b)throw new AssertionError("synthetic dispatcher assertion "+assertions);assertions++;}
    static AdapterConfig fixture(){AdapterConfig a=new AdapterConfig(RdmTopologyDispatcher.KIND,17);ResourceConfig r=new ResourceConfig(17);r.setAdapterKind(RdmTopologyDispatcher.KIND);r.setResourceKind(RdmTopologyDispatcher.KIND);r.setResourceName("synthetic");r.addResourceIdentifier(new ResourceIdentifierConfig("fixture_json",RuntimeFixtureTest.JSON,false));a.setAdapterInstResource(r);return a;}
    static void add(AdapterConfig c,String k,String v){c.getAdapterInstResource().addResourceIdentifier(new ResourceIdentifierConfig(k,v,false));}
    static void set(AdapterConfig c,String k,String v){for(ResourceIdentifierConfig id:c.getAdapterInstResource().getResourceIdentifiers())if(id.getKey().equals(k))id.updateValue(v);}
    static void empty(CollectResult r){check(r!=null&&r.getResourceCollectResultMap().isEmpty());check(r.getDiscoveryResult()==null);check(r.getAdapterStatus().getResourceStatusMap().isEmpty());check(!r.getAdapterStatus().getMessage().contains("SENSITIVE"));}
    static class Delegate implements AdapterInterface3 {
        int configs,collects,discovers,stops,discards;AdapterConfig received;
        final CollectResult result=new CollectResult(RdmTopologyDispatcher.KIND,17);
        public AdapterStatus configure(AdapterConfig c){configs++;received=c;return new AdapterStatus(17,AdapterStatus.Status.as_succeeded,"synthetic");}
        public CollectResult collect(){collects++;return result;}
        public DiscoveryResult discover(DiscoveryParam p){discovers++;return new DiscoveryResult(RdmTopologyDispatcher.KIND,17);}
        public AdapterDescribe describe(){throw new AssertionError();}public boolean test(TestParam p){throw new AssertionError();}
        public boolean changePassword(PasswordParam p){throw new AssertionError();}public CheckCertificateResult checkCertificate(CheckCertificateParam p){throw new AssertionError();}
        public void stopCollection(){stops++;}public AdapterStatus discard(){discards++;return new AdapterStatus(17);}
    }
    public static void main(String[]args)throws Exception{
        // The real collector uses this exact boxed-Integer public signature.
        java.lang.reflect.Constructor<RdmTopologyDispatcher> collector=RdmTopologyDispatcher.class.getConstructor(String.class,Integer.class);
        check(java.lang.reflect.Modifier.isPublic(collector.getModifiers()));
        java.lang.reflect.Field delegate=RdmTopologyDispatcher.class.getDeclaredField("fixture");delegate.setAccessible(true);
        RdmTopologyDispatcher reflected=collector.newInstance("/synthetic/offline-adapter",Integer.valueOf(17));
        check(delegate.get(reflected)==null);empty(reflected.collect());check(reflected.discover(null).isEmpty());
        AdapterConfig wrong=fixture();wrong.setAdapterInstResourceId(18,UUID.randomUUID(),wrong.getAdapterInstResource().getResourceKey());
        check(reflected.configure(wrong).getStatus()==AdapterStatus.Status.as_failed);check(!reflected.test(new TestParam(wrong)));
        check(delegate.get(reflected)==null);empty(reflected.collect());
        AdapterConfig readonly=DispatcherDiagnosticFixture.config();set(readonly,"diagnostic_enabled","false");
        check(reflected.configure(readonly).getStatus()==AdapterStatus.Status.as_succeeded);empty(reflected.collect());empty(reflected.collect());
        check(delegate.get(reflected)==null);check(reflected.discover(null).isEmpty());
        check(reflected.configure(fixture()).getStatus()==AdapterStatus.Status.as_failed);empty(reflected.collect());check(delegate.get(reflected)==null);reflected.discard();
        AtomicInteger work=new AtomicInteger(),constructed=new AtomicInteger();Delegate fixture=new Delegate();
        RdmTopologyDispatcher d=new RdmTopologyDispatcher(()->{constructed.incrementAndGet();return fixture;},DispatcherDiagnosticFixture.success(work));
        empty(d.collect());check(d.discover(null).isEmpty());check(constructed.get()==0&&work.get()==0);
        AdapterConfig c=DispatcherDiagnosticFixture.config();check(d.configure(c).getStatus()==AdapterStatus.Status.as_succeeded);check(constructed.get()==0);
        TestParam unsaved=new TestParam(fixture());check(d.test(unsaved));check(work.get()==0&&constructed.get()==0);check(unsaved.getErrorMsg().contains("CONFIGURATION_ONLY"));
        empty(d.collect());check(work.get()==1);empty(d.collect());check(work.get()==1);check(d.discover(null).isEmpty());
        check(d.configure(fixture()).getStatus()==AdapterStatus.Status.as_failed);empty(d.collect());check(constructed.get()==0&&work.get()==1);
        check(d.configure(c).getStatus()==AdapterStatus.Status.as_succeeded);empty(d.collect());check(work.get()==1);
        d.stopCollection();empty(d.collect());d.discard();check(d.configure(c).getStatus()==AdapterStatus.Status.as_failed);empty(d.collect());
        for(String mode:List.of("unknown","fixture ","READ_ONLY_DIAGNOSTIC")){
            RdmTopologyDispatcher x=new RdmTopologyDispatcher(()->{throw new AssertionError();},DispatcherDiagnosticFixture.success(work));AdapterConfig a=fixture();add(a,"execution_mode",mode);
            check(x.configure(a).getStatus()==AdapterStatus.Status.as_failed);empty(x.collect());x.discard();
        }
        for(String problem:List.of("missing_mode","mixed","duplicate_blank","unique_blank","unknown","missing_credential","wrong_owner","missing_target","credential_flag","bad_trust")){
            AdapterConfig a=DispatcherDiagnosticFixture.config();ResourceConfig r=a.getAdapterInstResource();
            if(problem.equals("missing_mode"))r.getResourceIdentifiers().removeIf(i->"execution_mode".equals(i.getKey()));
            if(problem.equals("mixed"))add(a,"fixture_json",RuntimeFixtureTest.JSON);
            if(problem.equals("duplicate_blank")){add(a,"fixture_json","");r.getResourceIdentifiers().add(new ResourceIdentifierConfig("fixture_json","",false));}
            if(problem.equals("unique_blank"))r.addResourceIdentifier(new ResourceIdentifierConfig("fixture_json","",true));
            if(problem.equals("unknown"))add(a,"unknown","");
            if(problem.equals("missing_credential"))r.setResourceCredential(null);
            if(problem.equals("wrong_owner"))a.setAdapterKindKey("VMWARE");
            if(problem.equals("missing_target"))r.getResourceIdentifiers().removeIf(i->"target_vm_moid".equals(i.getKey()));
            if(problem.equals("credential_flag")){r.getResourceCredential().getCredenialFields().clear();}
            if(problem.equals("bad_trust"))set(a,"trust_mode","insecure");
            RdmTopologyDispatcher x=new RdmTopologyDispatcher(()->{throw new AssertionError();},DispatcherDiagnosticFixture.success(work));
            check(x.configure(a).getStatus()==AdapterStatus.Status.as_failed);empty(x.collect());check(!x.test(new TestParam(a)));x.discard();
        }
        for(boolean explicit:List.of(false,true)){
            Delegate f=new Delegate();AtomicInteger made=new AtomicInteger();RdmTopologyDispatcher x=new RdmTopologyDispatcher(()->{made.incrementAndGet();return f;},DispatcherDiagnosticFixture.success(work));
            AdapterConfig a=fixture();if(explicit)add(a,"execution_mode","fixture");for(String key:List.of("vcenter_endpoint","diagnostic_enabled","authorize_once"))add(a,key,"");
            check(x.configure(a).getStatus()==AdapterStatus.Status.as_succeeded);check(made.get()==1&&f.received==a);check(x.collect()==f.result);check(x.collect()==f.result);check(f.collects==2);
            x.discover(null);check(f.discovers==1);check(x.test(new TestParam(DispatcherDiagnosticFixture.config())));check(f.configs==1);
            set(a,"fixture_json","SENSITIVE-invalid");empty(x.collect());check(f.collects==2&&f.discards==1);
            check(x.configure(DispatcherDiagnosticFixture.config()).getStatus()==AdapterStatus.Status.as_failed);empty(x.collect());x.discard();
        }
        for(String change:List.of("id","uuid","in_place","invalid_reconfigure","stop","disabled")){
            AtomicInteger runs=new AtomicInteger();RdmTopologyDispatcher x=new RdmTopologyDispatcher(()->{throw new AssertionError();},DispatcherDiagnosticFixture.success(runs));
            AdapterConfig a=DispatcherDiagnosticFixture.config();if(change.equals("disabled"))set(a,"diagnostic_enabled","false");
            check(x.configure(a).getStatus()==AdapterStatus.Status.as_succeeded);
            if(change.equals("id")||change.equals("uuid")){
                AdapterConfig other=DispatcherDiagnosticFixture.config();
                other.setAdapterInstResourceId(change.equals("id")?18:17,UUID.randomUUID(),other.getAdapterInstResource().getResourceKey());
                check(x.configure(other).getStatus()==AdapterStatus.Status.as_failed);
            }
            if(change.equals("in_place"))set(a,"target_vm_moid","vm-72");
            if(change.equals("invalid_reconfigure")){AdapterConfig bad=DispatcherDiagnosticFixture.config();set(bad,"execution_mode","SENSITIVE");check(x.configure(bad).getStatus()==AdapterStatus.Status.as_failed);}
            if(change.equals("stop"))x.stopCollection();
            empty(x.collect());check(runs.get()==0);x.discard();
        }
        for(boolean discovery:List.of(false,true)){
            AdapterConfig a=fixture();Delegate mutating=new Delegate(){
                @Override public CollectResult collect(){
                    CollectResult r=new CollectResult(RdmTopologyDispatcher.KIND,17);
                    r.addNewResource(new ResourceKey("synthetic-returned-resource","RdmMapping",RdmTopologyDispatcher.KIND));
                    check(r.getDiscoveryResult()!=null&&!r.getDiscoveryResult().isEmpty());set(a,"fixture_json","SENSITIVE");return r;
                }
                @Override public DiscoveryResult discover(DiscoveryParam ignored){
                    DiscoveryResult r=new DiscoveryResult(RdmTopologyDispatcher.KIND,17);
                    r.addResource(new ResourceConfig(new ResourceKey("synthetic-returned-resource","RdmMapping",RdmTopologyDispatcher.KIND)));
                    check(!r.isEmpty());set(a,"fixture_json","SENSITIVE");return r;
                }
            };
            RdmTopologyDispatcher x=new RdmTopologyDispatcher(()->mutating,DispatcherDiagnosticFixture.success(work));check(x.configure(a).getStatus()==AdapterStatus.Status.as_succeeded);
            if(discovery)check(x.discover(null).isEmpty());else empty(x.collect());
            check(mutating.discards==1);empty(x.collect());x.discard();
        }
        AdapterConfig target=DispatcherDiagnosticFixture.config();
        check(com.vcfcf.adapters.rdmtopologypoc.runtime.RuntimeDiscoveryBinding.sameKindDiagnostic(target.getAdapterInstResource()).diagnosticTransport()!=null);
        for(String problem:List.of("missing_mode","duplicate_mode","duplicate_fixture")){
            AdapterConfig a=DispatcherDiagnosticFixture.config();ResourceConfig r=a.getAdapterInstResource();
            if(problem.equals("missing_mode"))r.getResourceIdentifiers().removeIf(i->"execution_mode".equals(i.getKey()));
            if(problem.equals("duplicate_mode"))r.getResourceIdentifiers().add(new ResourceIdentifierConfig("execution_mode","read_only_diagnostic",false));
            if(problem.equals("duplicate_fixture")){add(a,"fixture_json","");r.getResourceIdentifiers().add(new ResourceIdentifierConfig("fixture_json","",false));}
            boolean rejected=false;try{com.vcfcf.adapters.rdmtopologypoc.runtime.RuntimeDiscoveryBinding.sameKindDiagnostic(r);}catch(com.vcfcf.adapters.rdmtopologypoc.discovery.DiscoveryException ex){rejected=true;}check(rejected);
        }
        // Actual SDK descriptor load, not XML-only or test logging linkage.
        try(java.io.InputStream in=java.nio.file.Files.newInputStream(java.nio.file.Path.of(args[0]))){
            AdapterDescribe loaded=AdapterDescribe.make(in);check(loaded!=null);
            com.integrien.alive.common.adapter3.describe.ResourceKindDescribe instance=loaded.getResourceKinds().stream().filter(k->RdmTopologyDispatcher.KIND.equals(k.getKey())).findFirst().orElseThrow();
            check(!instance.isCredentialRequired());check(instance.getResourceIdentifiers().size()==12);
            for(com.integrien.alive.common.adapter3.describe.ResourceIdentifierDescribe setting:instance.getResourceIdentifiers())check(Boolean.FALSE.equals(setting.isRequired())&&Integer.valueOf(2).equals(setting.getIdentType())&&setting.getDefaultValue()==null);
            check(instance.getCredentialKinds().equals(List.of("rdm_vcenter_readonly")));
            check(loaded.getCredentialKinds().size()==1);
            com.integrien.alive.common.adapter3.describe.CredentialKindDescribe credential=loaded.getCredentialKinds().get(0);
            check("rdm_vcenter_readonly".equals(credential.getKey())&&credential.getCredentialFields().size()==2);
            for(com.integrien.alive.common.adapter3.describe.CredentialFieldDescribe field:credential.getCredentialFields())
                check(Boolean.TRUE.equals(field.isRequired())&&("password".equals(field.getKey())?Boolean.TRUE.equals(field.isPassworded()):"username".equals(field.getKey())&&!Boolean.TRUE.equals(field.isPassworded())));
        }
        for(String interleave:List.of("invalid","mode","owner","stop","discard")){
            AtomicInteger runs=new AtomicInteger();java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
            RdmTopologyDispatcher x=new RdmTopologyDispatcher(()->{throw new AssertionError();},DispatcherDiagnosticFixture.blocked(runs,started,release));
            check(x.configure(DispatcherDiagnosticFixture.config()).getStatus()==AdapterStatus.Status.as_succeeded);
            check(x.configure(DispatcherDiagnosticFixture.config()).getStatus()==AdapterStatus.Status.as_succeeded);
            java.util.concurrent.atomic.AtomicReference<CollectResult> result=new java.util.concurrent.atomic.AtomicReference<>();Thread t=new Thread(()->result.set(x.collect()));t.start();
            check(started.await(1,java.util.concurrent.TimeUnit.SECONDS));
            if(interleave.equals("invalid"))x.configure(null);
            if(interleave.equals("mode"))x.configure(fixture());
            if(interleave.equals("owner")){AdapterConfig other=DispatcherDiagnosticFixture.config();other.setAdapterInstResourceId(18,UUID.randomUUID(),other.getAdapterInstResource().getResourceKey());x.configure(other);}
            if(interleave.equals("stop"))x.stopCollection();if(interleave.equals("discard"))x.discard();
            release.countDown();t.join(2500);check(!t.isAlive());empty(result.get());check(!result.get().getAdapterStatus().getMessage().contains("READ_ONLY_GRAPH_QUALIFIED"));
            empty(x.collect());check(runs.get()==1);x.discard();
        }
        // Default constructor must remain bare: no Base constructor, SDK configuration or networking before route selection.
        RdmTopologyDispatcher bare=new RdmTopologyDispatcher();empty(bare.collect());check(bare.discover(null).isEmpty());
        java.nio.file.Path home=java.nio.file.Files.createTempDirectory("rdm-bare-describe-");String previous=System.getProperty("ADAPTER_HOME");
        java.nio.file.Path conf=home.resolve(RdmTopologyDispatcher.KIND).resolve("conf");
        try{java.nio.file.Files.createDirectories(conf.resolve("resources"));java.nio.file.Files.copy(java.nio.file.Path.of(args[0]),conf.resolve("describe.xml"));
            java.nio.file.Files.copy(java.nio.file.Path.of(args[0]).getParent().resolve("resources/resources.properties"),conf.resolve("resources/resources.properties"));
            System.setProperty("ADAPTER_HOME",home.toString());check(bare.describe()!=null);}
        finally{if(previous==null)System.clearProperty("ADAPTER_HOME");else System.setProperty("ADAPTER_HOME",previous);java.nio.file.Files.deleteIfExists(conf.resolve("resources/resources.properties"));java.nio.file.Files.deleteIfExists(conf.resolve("resources"));java.nio.file.Files.deleteIfExists(conf.resolve("describe.xml"));java.nio.file.Files.deleteIfExists(conf);java.nio.file.Files.deleteIfExists(conf.getParent());java.nio.file.Files.deleteIfExists(home);}
        bare.discard();
        E493DispatcherTest.run();
        E493ColdStartTest.run(java.nio.file.Path.of(args[0]));
        System.out.println("DispatcherTest: PASS "+assertions+" assertions (delegate seam, actual SDK carriers/payloads)");
    }
}
