package com.vcfcf.adapters.rdmtopologypoc.runtime;

import com.integrien.alive.common.adapter3.config.*;
import com.vcfcf.adapters.rdmtopologypoc.discovery.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.file.*;

/** Actual SDK carriers; no platform decryption claim, no VMware endpoint. */
public final class RuntimeBindingTest {
    private static int cases;
    private static final String VC = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER = "22222222-2222-2222-2222-222222222222";
    interface Checked { void run() throws Exception; }
    static void pass(boolean condition) { if (!condition) throw new AssertionError("Synthetic assertion failed"); cases++; }
    static void reject(DiscoveryException.Code code, Checked test) throws Exception {
        try { test.run(); throw new AssertionError("Expected rejection"); }
        catch (DiscoveryException ex) { pass(ex.code() == code && ex.getCause() == null && !ex.toString().contains("SENSITIVE")); }
    }
    static ResourceConfig config() {
        ResourceConfig c = new ResourceConfig(); c.setAdapterKind("vcfcf_rdm_topology_poc"); c.setResourceKind("vcfcf_rdm_topology_poc");
        c.addResourceIdentifier(new ResourceIdentifierConfig("vcenter_endpoint", "https://synthetic.invalid/sdk", false));
        c.addResourceIdentifier(new ResourceIdentifierConfig("trust_mode", "jvm", false));
        c.addResourceIdentifier(new ResourceIdentifierConfig("discovery_scope", "caller_visible_inventory", false));
        CredentialConfig credential = new CredentialConfig(); credential.setCredentialKey(RuntimeDiscoveryBinding.CREDENTIAL_KIND);
        credential.addCredentialField(new CredentialFieldConfig("username", "SENSITIVE-USER", false));
        credential.addCredentialField(new CredentialFieldConfig("password", "SENSITIVE-ENCRYPTED", true));
        c.setResourceCredential(credential); return c;
    }
    static void set(ResourceConfig c,String name,String value) {
        for(ResourceIdentifierConfig id:c.getResourceIdentifiers()) if(name.equals(id.getKey())) id.updateValue(value);
    }
    static DiscoveryGraph.VmKey vm(String vc) throws Exception {
        return new DiscoveryGraph.VmKey("VMWARE","VirtualMachine","SENSITIVE-VM",List.of(
            new DiscoveryGraph.Identifier("VMEntityVCID",vc,true),
            new DiscoveryGraph.Identifier("VMEntityObjectID","vm-71",true),
            new DiscoveryGraph.Identifier("empty","",false),
            new DiscoveryGraph.Identifier("extra","SENSITIVE-EXTRA",true)));
    }
    static ScopedOperationsResolver.Snapshot snap(List<DiscoveryGraph.VmKey> keys,boolean complete,boolean auth,boolean route,long time,String vc,long gen) {
        return new ScopedOperationsResolver.Snapshot(keys,complete,auth,route,time,vc,gen);
    }
    public static void main(String[] args) throws Exception {
        ResourceConfig c=config(); RuntimeDiscoveryBinding binding=RuntimeDiscoveryBinding.from(c);
        pass(binding.transport()!=null && !binding.toString().contains("synthetic"));
        try(RuntimeDiscoveryBinding.Credentials credentials=binding.credentials(f->f.isPassword()?"SENSITIVE-SECRET":"SENSITIVE-USER")) {
            java.lang.reflect.Field secretField=credentials.getClass().getDeclaredField("password");secretField.setAccessible(true);
            char[] leased=(char[])secretField.get(credentials);
            pass(!credentials.toString().contains("SENSITIVE")); credentials.close();
            boolean wiped=true;for(char b:leased)wiped&=b==0;pass(wiped && secretField.get(credentials)==null);
            reject(DiscoveryException.Code.AUTHENTICATION,()->credentials.discover(null));
        }
        try(RuntimeDiscoveryBinding.Credentials credentials=binding.credentials(f->"synthetic")) {
            VimDiscoveryClient client=new VimDiscoveryClient((operation,request,cookie)->{
                DiscoveryException unsafe=new DiscoveryException(DiscoveryException.Code.CONNECTION);
                unsafe.addSuppressed(new IllegalStateException("SENSITIVE"));throw unsafe;
            },(vc,moid)->null);
            try { credentials.discover(client);throw new AssertionError(); }
            catch(DiscoveryException ex) {pass(ex.code()==DiscoveryException.Code.CONNECTION && ex.getCause()==null && ex.getSuppressed().length==0);}
            reject(DiscoveryException.Code.AUTHENTICATION,()->credentials.discover(client));
        }
        reject(DiscoveryException.Code.AUTHENTICATION,()->binding.credentials(f->{throw new NoClassDefFoundError("SENSITIVE");}));
        reject(DiscoveryException.Code.AUTHENTICATION,()->binding.credentials(f->{throw new IllegalStateException("SENSITIVE");}));
        reject(DiscoveryException.Code.AUTHENTICATION,()->binding.credentials(f->""));
        for (String invalid : List.of("bad\u0001", "bad\ud800", "bad\udfff"))
            reject(DiscoveryException.Code.AUTHENTICATION,()->binding.credentials(f->f.isPassword()?invalid:"synthetic-user"));
        reject(DiscoveryException.Code.AUTHENTICATION,()->binding.credentials(f->f.isPassword()?"synthetic-secret":"bad\ud800"));
        try (RuntimeDiscoveryBinding.Credentials whitespace = binding.credentials(f->f.isPassword()?"  valid secret  ":"synthetic-user")) { pass(whitespace != null); }
        ResourceConfig moved=config();RuntimeDiscoveryBinding old=RuntimeDiscoveryBinding.from(moved);set(moved,"vcenter_endpoint","https://other.invalid/sdk");
        reject(DiscoveryException.Code.AUTHENTICATION,()->old.credentials(f->"SENSITIVE"));
        reject(DiscoveryException.Code.CONFIGURATION,old::transport);
        ResourceConfig rescope=config();RuntimeDiscoveryBinding oldScope=RuntimeDiscoveryBinding.from(rescope);set(rescope,"discovery_scope","different");
        reject(DiscoveryException.Code.AUTHENTICATION,()->oldScope.credentials(f->"SENSITIVE"));
        ResourceConfig retrust=config();RuntimeDiscoveryBinding oldTrust=RuntimeDiscoveryBinding.from(retrust);set(retrust,"trust_mode","enterprise_ca");
        reject(DiscoveryException.Code.CONFIGURATION,oldTrust::transport);
        ResourceConfig replaced=config();RuntimeDiscoveryBinding oldCredential=RuntimeDiscoveryBinding.from(replaced);replaced.setResourceCredential(config().getResourceCredential());
        reject(DiscoveryException.Code.AUTHENTICATION,()->oldCredential.credentials(f->"SENSITIVE"));
        c.getResourceCredential().getCredenialFields().get(1).setPassword(false);
        reject(DiscoveryException.Code.AUTHENTICATION,()->binding.credentials(f->"SENSITIVE"));
        reject(DiscoveryException.Code.CONFIGURATION,()->RuntimeDiscoveryBinding.from(null));
        for(String endpoint:List.of("http://synthetic.invalid/sdk","https://user:secret@synthetic.invalid/sdk","https://synthetic.invalid/sdk?secret=SENSITIVE","https://synthetic.invalid/other","https://synthetic.invalid:0/sdk")) {
            ResourceConfig bad=config();set(bad,"vcenter_endpoint",endpoint);
            reject(DiscoveryException.Code.CONFIGURATION,()->RuntimeDiscoveryBinding.from(bad));
        }
        for(String key:List.of("vcenter_endpoint","trust_mode","discovery_scope")) {
            ResourceConfig bad=config();set(bad,key,""); reject(DiscoveryException.Code.CONFIGURATION,()->RuntimeDiscoveryBinding.from(bad));
        }
        ResourceConfig foreign=config();foreign.setAdapterKind("VMWARE");
        reject(DiscoveryException.Code.CONFIGURATION,()->RuntimeDiscoveryBinding.from(foreign));
        ResourceConfig noCred=config();noCred.setResourceCredential(null);
        reject(DiscoveryException.Code.CONFIGURATION,()->RuntimeDiscoveryBinding.from(noCred));
        ResourceConfig wrongKind=config();wrongKind.getResourceCredential().setCredentialKey("VMWARE");
        reject(DiscoveryException.Code.CONFIGURATION,()->RuntimeDiscoveryBinding.from(wrongKind));
        ResourceConfig unresolved=config();unresolved.setTrustedCertificateIds(List.of("SENSITIVE-ID"));
        reject(DiscoveryException.Code.CONFIGURATION,()->RuntimeDiscoveryBinding.from(unresolved));
        ResourceConfig malformed=config();set(malformed,"trust_mode","enterprise_ca");CertificateConfig invalid=new CertificateConfig();invalid.setCertificateString("SENSITIVE-NOT-PEM");malformed.setTrustedCertificates(List.of(invalid));
        reject(DiscoveryException.Code.CONFIGURATION,()->RuntimeDiscoveryBinding.from(malformed));
        if(args.length==1) {
            String pem=Files.readString(Path.of(args[0]));ResourceConfig trusted=config();set(trusted,"trust_mode","enterprise_ca");
            CertificateConfig ca=new CertificateConfig();ca.setCertificateString(pem);trusted.setTrustedCertificates(List.of(ca));
            RuntimeDiscoveryBinding trustedBinding=RuntimeDiscoveryBinding.from(trusted);
            pass(trustedBinding.transport()!=null);
            ca.setEnforceHostnameVertification(false);reject(DiscoveryException.Code.CONFIGURATION,()->RuntimeDiscoveryBinding.from(trusted));
            reject(DiscoveryException.Code.CONFIGURATION,trustedBinding::transport);
            ca.setEnforceHostnameVertification(true);ca.setEnforceDateValidityChecking(false);reject(DiscoveryException.Code.CONFIGURATION,()->RuntimeDiscoveryBinding.from(trusted));
            ca.setEnforceDateValidityChecking(true);trusted.setTrustedCertificates(List.of(ca,ca));reject(DiscoveryException.Code.CONFIGURATION,()->RuntimeDiscoveryBinding.from(trusted));
        }
        AtomicLong clock=new AtomicLong(100);DiscoveryGraph.VmKey key=vm(VC);
        ScopedOperationsResolver resolver=ScopedOperationsResolver.offline(()->snap(List.of(key),true,true,true,100,VC,1),clock::get,1000,VC,1);
        DiscoveryGraph.VmKey otherKey=vm(OTHER);
        ScopedOperationsResolver otherResolver=ScopedOperationsResolver.offline(()->snap(List.of(otherKey),true,true,true,100,OTHER,1),clock::get,1000,OTHER,1);
        pass(otherResolver.resolve(OTHER,"vm-71")==otherKey && resolver.resolve(VC,"vm-71")==key);
        reject(DiscoveryException.Code.CONFIGURATION,()->ScopedOperationsResolver.offline(()->null,clock::get,1000,"00000000-0000-0000-0000-000000000000",1));
        pass(resolver.resolve(VC,"vm-71")==key && resolver.resolve(VC,"vm-71").identifiers.get(2).value.isEmpty()
            && !resolver.resolve(VC,"vm-71").identifiers.get(2).unique && resolver.resolve(VC,"vm-71").identifiers.get(3).unique);
        reject(DiscoveryException.Code.IDENTITY,()->resolver.resolve(OTHER,"vm-71"));
        reject(DiscoveryException.Code.IDENTITY,()->resolver.resolve(VC,"vm-72"));
        reject(DiscoveryException.Code.CONFIGURATION,ScopedOperationsResolver::production);
        for(int i=0;i<3;i++) {final int flag=i;
            reject(DiscoveryException.Code.INCOMPLETE,()->ScopedOperationsResolver.offline(()->snap(List.of(key),flag!=0,flag!=1,flag!=2,100,VC,1),clock::get,1000,VC,1));
        }
        reject(DiscoveryException.Code.INCOMPLETE,()->ScopedOperationsResolver.offline(()->snap(List.of(key),true,true,true,101,VC,1),clock::get,1000,VC,1));
        reject(DiscoveryException.Code.INCOMPLETE,()->ScopedOperationsResolver.offline(()->snap(List.of(key),true,true,true,100,VC,2),clock::get,1000,VC,1));
        reject(DiscoveryException.Code.INCOMPLETE,()->ScopedOperationsResolver.offline(()->snap(List.of(key),true,true,true,100,OTHER,1),clock::get,1000,VC,1));
        reject(DiscoveryException.Code.IDENTITY,()->ScopedOperationsResolver.offline(()->snap(List.of(vmUnchecked(OTHER)),true,true,true,100,VC,1),clock::get,1000,VC,1));
        ScopedOperationsResolver duplicate=ScopedOperationsResolver.offline(()->snap(List.of(key,key),true,true,true,100,VC,1),clock::get,1000,VC,1);
        reject(DiscoveryException.Code.AMBIGUOUS,()->duplicate.resolve(VC,"vm-71"));
        for(ScopedOperationsResolver.Failure failure:ScopedOperationsResolver.Failure.values()) {
            reject(failure==ScopedOperationsResolver.Failure.AUTHENTICATION?DiscoveryException.Code.AUTHENTICATION:failure==ScopedOperationsResolver.Failure.TRANSPORT?DiscoveryException.Code.CONNECTION:DiscoveryException.Code.INCOMPLETE,
                ()->ScopedOperationsResolver.offline(()->{throw new ScopedOperationsResolver.ProviderException(failure);},clock::get,1000,VC,1));
        }
        clock.set(1101);reject(DiscoveryException.Code.INCOMPLETE,()->resolver.resolve(VC,"vm-71"));
        reject(DiscoveryException.Code.INCOMPLETE,()->ScopedOperationsResolver.offline(()->snap(List.of(key),true,true,true,Long.MIN_VALUE,VC,1),()->Long.MAX_VALUE,1000,VC,1));
        reject(DiscoveryException.Code.INCOMPLETE,()->ScopedOperationsResolver.offline(()->snap(List.of(key),true,true,true,100,VC,1),()->{throw new IllegalStateException("SENSITIVE");},1000,VC,1));
        reject(DiscoveryException.Code.INCOMPLETE,()->ScopedOperationsResolver.offline(()->{throw new IllegalStateException("SENSITIVE");},clock::get,1000,VC,1));
        System.out.println("Runtime binding: "+cases+" deterministic cases passed; no production API or decryption exercised.");
    }
    private static DiscoveryGraph.VmKey vmUnchecked(String vc) { try{return vm(vc);}catch(Exception ex){throw new AssertionError();} }
}
