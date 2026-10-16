package com.vcfcf.adapters.rdmtopologypoc.discovery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.vcfcf.adapters.rdmtopologypoc.discovery.SecureSoap.*;

/** Synthetic SOAP + loopback-only PKI tests, never customer inventory or live endpoints. */
public final class DiscoveryFoundationTest {
    private static int tests;
    private static final String NAA="60000000000000000000000000000001";
    private static void check(boolean b) { if(!b) throw new AssertionError("synthetic assertion failed"); }
    interface Checked {void run() throws Exception;}
    private static void test(Checked action) throws Exception {action.run();tests++;}
    private static void rejects(Checked action) throws Exception {try{action.run();throw new AssertionError("expected rejection");}catch(DiscoveryException ex){check(ex.getCause()==null&&ex.getSuppressed().length==0&&!ex.toString().contains("SENSITIVE"));}}
    private static byte[] xml(String op,String content) {return ("<s:Envelope xmlns:s=\""+SOAP+"\" xmlns=\""+VIM+"\" xmlns:xsi=\""+XSI+"\"><s:Body><"+op+"Response>"+content+"</"+op+"Response></s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8);}
    private static byte[] descriptor(int flags,String naa) {byte[] b=new byte[4+naa.length()/2];b[0]=1;b[1]=(byte)flags;b[3]=(byte)(naa.length()/2);for(int i=4;i<b.length;i++)b[i]=(byte)Integer.parseInt(naa.substring((i-4)*2,(i-4)*2+2),16);return b;}
    private static DiscoveryGraph.Lun qualify(String naa,int flags) throws Exception {return DiscoveryGraph.qualify("opaque","opaque","naa."+naa,List.of(new DiscoveryGraph.Descriptor("NAA",-1,descriptor(flags,naa))),Map.of("vendor","Synthetic"));}
    private static DiscoveryGraph.VmKey key(String vc,String moid) throws Exception {return new DiscoveryGraph.VmKey("VMWARE","VirtualMachine","duplicate name",List.of(new DiscoveryGraph.Identifier("VMEntityVCID",vc,true),new DiscoveryGraph.Identifier("VMEntityObjectID",moid,true),new DiscoveryGraph.Identifier("unused","",false)));}
    private static final class Fake implements VimDiscoveryClient.Transport {
        String mutation=""; boolean paginate,tokenLoop;List<String> calls=new ArrayList<>();int vmpages;Runnable hostCallback=()->{};
        @Override public VimDiscoveryClient.Reply exchange(String op,byte[] request,String cookie) throws DiscoveryException {
            calls.add(op);String text=new String(request,StandardCharsets.UTF_8);String content="";
            if(op.equals("RetrieveServiceContent"))content="<returnval><about><instanceUuid>11111111-1111-1111-1111-111111111111</instanceUuid><version>8.0</version></about><sessionManager type=\"SessionManager\">session</sessionManager><propertyCollector type=\"PropertyCollector\">collector</propertyCollector><rootFolder type=\"Folder\">root</rootFolder><viewManager type=\"ViewManager\">views</viewManager></returnval>";
            else if(op.equals("Login")){if(mutation.equals("auth"))return new VimDiscoveryClient.Reply(("<s:Envelope xmlns:s=\""+SOAP+"\"><s:Body><s:Fault><faultstring>SENSITIVE</faultstring></s:Fault></s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8),null);content="<returnval><key>ignored</key></returnval>";if(mutation.equals("malformed-login"))return new VimDiscoveryClient.Reply(xml("Wrong",""),"vmware_soap_session=synthetic");}
            else if(op.equals("CreateContainerView"))content="<returnval type=\"ContainerView\">view</returnval>";
            else if(op.equals("RetrievePropertiesEx")||op.equals("ContinueRetrievePropertiesEx")) {
                boolean host=text.contains("<type>HostSystem</type>"),fence=text.contains("<type>VirtualMachine</type>")&&!text.contains("config.hardware.device");
                if(host)hostCallback.run();
                if(op.equals("ContinueRetrievePropertiesEx")) {content="<returnval>"+(tokenLoop?"<token>next</token>":"")+"</returnval>";}
                else if(host)content=mutation.equals("host-absent")?"<returnval/>":mutation.equals("host-fault-token")?"<returnval><token>host-page</token>"+obj("HostSystem","host-a","<missingSet><path>config.storageDevice.scsiLun</path><fault>SENSITIVE</fault></missingSet>")+"</returnval>":"<returnval>"+obj("HostSystem","host-a",prop("config.storageDevice.scsiLun",lunArray()))+"</returnval>";
                else {String properties=prop("config.changeVersion",mutation.equals("motion")&&fence?"2":"1")+prop("runtime.host","<x type=\"HostSystem\"/>");properties=properties.replace("<val><x type=\"HostSystem\"/></val>","<val xsi:type=\"ManagedObjectReference\" type=\"HostSystem\">host-a</val>");
                    properties=prop("config.instanceUuid","aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")+properties;
                    if(!fence)properties+=prop("config.hardware.device",devices());
                    if(mutation.equals("missing"))properties=prop("config.instanceUuid","aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
                    if(mutation.equals("fault"))properties+="<missingSet><path>runtime.host</path><fault>SENSITIVE</fault></missingSet>";
                    if(mutation.equals("duplicate-property"))properties+=prop("config.changeVersion","1");
                    String object=obj("VirtualMachine","vm-1",properties);String second=mutation.equals("two-vm-one-malformed")?object.replace("vm-1","vm-2").replace("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb").replace("VirtualDisk","VirtualMysteryDisk"):"";
                    content=mutation.equals("invalid-vc-empty")?"<returnval/>":"<returnval>"+object+second+(mutation.equals("duplicate-object")?object:"")+(paginate&&!fence?"<token>next</token>":"")+"</returnval>";
                }
            }
            if(mutation.equals("cleanup")&&(op.equals("Logout")||op.equals("DestroyView")))throw new DiscoveryException(DiscoveryException.Code.CONNECTION);
            byte[] response=xml(op,content);
            if(mutation.equals("invalid-vc-empty")&&op.equals("RetrieveServiceContent")) response=new String(response,StandardCharsets.UTF_8).replace("11111111-1111-1111-1111-111111111111","invalid-vc").getBytes(StandardCharsets.UTF_8);
            if(mutation.equals("invalid-vm-empty")) response=new String(response,StandardCharsets.UTF_8).replace("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","invalid-vm").getBytes(StandardCharsets.UTF_8);
            if(mutation.equals("blank-version-empty")) response=new String(response,StandardCharsets.UTF_8).replace("<name>config.changeVersion</name><val>1</val>","<name>config.changeVersion</name><val></val>").getBytes(StandardCharsets.UTF_8);
            if(mutation.equals("wrong-namespace"))response=new String(response,StandardCharsets.UTF_8).replace("urn:vim25","urn:evil").getBytes(StandardCharsets.UTF_8);
            return new VimDiscoveryClient.Reply(response,op.equals("Login")?"vmware_soap_session=synthetic":null);
        }
        private String devices(){if(mutation.equals("invalid-vm-empty")||mutation.equals("blank-version-empty"))return "";String backing="<backing xsi:type=\"VirtualDiskRawDiskMappingVer1BackingInfo\"><lunUuid>opaque</lunUuid><deviceName>/synthetic</deviceName><fileName>synthetic.vmdk</fileName><compatibilityMode>physicalMode</compatibilityMode></backing>";return "<VirtualDevice xsi:type=\"VirtualDisk\"><key>2001</key>"+backing+"</VirtualDevice>";}
        private String lunArray(){String uuid=mutation.equals("join")?"other":"opaque",naa=mutation.equals("naa")?"naa.invalid":"naa."+NAA;StringBuilder data=new StringBuilder();for(byte b:descriptor(3,NAA))data.append("<data>").append(b).append("</data>");String item="<ScsiLun xsi:type=\"HostScsiDisk\"><uuid>"+uuid+"</uuid><canonicalName>"+naa+"</canonicalName><durableName><namespace>NAA</namespace><namespaceId>-1</namespaceId>"+data+"</durableName><vendor>Synthetic</vendor><capacity><block>100</block><blockSize>512</blockSize></capacity></ScsiLun>";return item+(mutation.equals("duplicate-lun")?item:"");}
        private String prop(String name,String value){String type=name.equals("config.hardware.device")?" xsi:type=\"ArrayOfVirtualDevice\"":name.equals("config.storageDevice.scsiLun")?" xsi:type=\"ArrayOfScsiLun\"":"";return "<propSet><name>"+name+"</name><val"+type+">"+value+"</val></propSet>";}
        private String obj(String kind,String id,String props){return "<objects><obj type=\""+kind+"\">"+id+"</obj>"+props+"</objects>";}
    }
    private static DiscoveryGraph run(Fake fake) throws Exception {return new VimDiscoveryClient(fake,new DiscoveryGraph.OfflineResolver(List.of(key("11111111-1111-1111-1111-111111111111","vm-1")),true)).discover("synthetic-user","SENSITIVE".toCharArray());}
    public static void main(String[] args) throws Exception {
        test(()->{DiscoveryGraph g=run(new Fake());check(g.complete&&g.attachments.size()==1&&g.luns.size()==1);check(g.attachments.get(0).mappingIdentity().equals(List.of("11111111-1111-1111-1111-111111111111","aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","2001")));check(g.luns.values().iterator().next().properties.get("capacity_bytes").equals("51200"));});
        test(()->{Fake f=new Fake();f.paginate=true;check(run(f).complete&&f.calls.contains("ContinueRetrievePropertiesEx")&&f.calls.contains("Logout")&&f.calls.contains("DestroyView"));});
        for(String m:List.of("auth","missing","fault","duplicate-property","duplicate-object","wrong-namespace"))test(()->{Fake f=new Fake();f.mutation=m;rejects(()->run(f));});
        test(()->{Fake f=new Fake();f.paginate=true;f.tokenLoop=true;rejects(()->run(f));check(f.calls.contains("CancelRetrievePropertiesEx"));});
        test(()->{Fake f=new Fake();f.paginate=true;f.mutation="fault";rejects(()->run(f));check(f.calls.contains("CancelRetrievePropertiesEx"));});
        test(()->{Fake f=new Fake();f.mutation="malformed-login";rejects(()->run(f));check(f.calls.contains("Logout"));});
        test(()->{Fake f=new Fake();java.util.concurrent.atomic.AtomicLong clock=new java.util.concurrent.atomic.AtomicLong();f.hostCallback=()->clock.set(121_000_000_000L);VimDiscoveryClient client=new VimDiscoveryClient(f,new DiscoveryGraph.OfflineResolver(List.of(key("11111111-1111-1111-1111-111111111111","vm-1")),true),clock::get);rejects(()->client.discover("synthetic-user","SENSITIVE".toCharArray()));check(f.calls.contains("DestroyView")&&f.calls.contains("Logout"));});
        test(()->{Fake f=new Fake();f.hostCallback=()->Thread.currentThread().interrupt();try {rejects(()->run(f));check(Thread.currentThread().isInterrupted()&&f.calls.contains("DestroyView")&&f.calls.contains("Logout"));}finally{Thread.interrupted();}});
        test(()->{Fake f=new Fake();f.mutation="host-absent";DiscoveryGraph g=run(f);check(!g.complete&&g.attachments.size()==1&&g.attachments.get(0).lun==null);});
        test(()->{Fake f=new Fake();f.mutation="host-fault-token";DiscoveryGraph g=run(f);check(!g.complete&&g.attachments.size()==1);int cancel=f.calls.indexOf("CancelRetrievePropertiesEx");check(cancel>=0&&f.calls.subList(cancel+1,f.calls.size()).contains("RetrievePropertiesEx"));});
        test(()->{Fake f=new Fake();f.mutation="two-vm-one-malformed";DiscoveryGraph graph=new VimDiscoveryClient(f,new DiscoveryGraph.OfflineResolver(List.of(key("11111111-1111-1111-1111-111111111111","vm-1"),key("11111111-1111-1111-1111-111111111111","vm-2")),true)).discover("synthetic-user","SENSITIVE".toCharArray());check(!graph.complete&&graph.attachments.size()==1&&graph.luns.size()==1);});
        test(()->{Fake f=new Fake();f.mutation="invalid-vc-empty";rejects(()->run(f));});
        for(String mutation:List.of("invalid-vm-empty","blank-version-empty"))test(()->{Fake f=new Fake();f.mutation=mutation;DiscoveryGraph g=run(f);check(!g.complete&&g.attachments.isEmpty()&&!g.diagnostics.isEmpty());});
        for(String m:List.of("join","naa","duplicate-lun"))test(()->{Fake f=new Fake();f.mutation=m;DiscoveryGraph g=run(f);check(!g.complete&&g.attachments.size()==1&&g.attachments.get(0).lun==null);});
        test(()->{Fake f=new Fake();f.mutation="motion";DiscoveryGraph g=run(f);check(!g.complete&&g.attachments.isEmpty()&&g.diagnostics.contains(DiscoveryException.Code.UNSTABLE));});
        test(()->{Fake f=new Fake();f.mutation="cleanup";check(!run(f).complete);});
        test(()->check(run(new Fake()).attachments.get(0).mappingIdentity().equals(run(new Fake()).attachments.get(0).mappingIdentity())));
        test(()->{DiscoveryGraph.OfflineResolver resolver=new DiscoveryGraph.OfflineResolver(List.of(key("11111111-1111-1111-1111-111111111111","vm-1"),key("22222222-2222-2222-2222-222222222222","vm-1")),true);check(resolver.resolve("22222222-2222-2222-2222-222222222222","vm-1").scoped("VMEntityVCID").equals("22222222-2222-2222-2222-222222222222"));check(resolver.resolve("11111111-1111-1111-1111-111111111111","vm-1").identifiers.get(2).value.isEmpty());});
        test(()->rejects(()->new DiscoveryGraph.OfflineResolver(List.of(key("11111111-1111-1111-1111-111111111111","vm-1"),key("11111111-1111-1111-1111-111111111111","vm-1")),true).resolve("11111111-1111-1111-1111-111111111111","vm-1")));
        test(()->rejects(()->new DiscoveryGraph.OfflineResolver(List.of(),false)));
        test(()->rejects(()->new DiscoveryGraph.OfflineResolver(List.of(key("11111111-1111-1111-1111-111111111111","vm-1")),true).resolve("99999999-9999-9999-9999-999999999999","vm-1")));
        test(()->rejects(()->new DiscoveryGraph.Identifier("unique","",true)));
        test(()->rejects(()->new DiscoveryGraph.Identifier("missing",null,false)));
        test(()->rejects(()->new DiscoveryGraph.VmKey("VMWARE","VirtualMachine","name",List.of(new DiscoveryGraph.Identifier("VMEntityVCID","11111111-1111-1111-1111-111111111111",false),new DiscoveryGraph.Identifier("VMEntityObjectID","vm-1",true)))));
        test(()->rejects(()->new DiscoveryGraph.VmKey("VMWARE","VirtualMachine","name",List.of(new DiscoveryGraph.Identifier("VMEntityVCID","11111111-1111-1111-1111-111111111111",true),new DiscoveryGraph.Identifier("vmentityvcid","11111111-1111-1111-1111-111111111111",true),new DiscoveryGraph.Identifier("VMEntityObjectID","vm-1",true)))));
        test(()->rejects(()->new DiscoveryGraph.VmKey("VMWARE","VirtualMachine","name",List.of(new DiscoveryGraph.Identifier("VMEntityObjectID","vm-1",true)))));
        test(()->{DiscoveryGraph.Lun a=qualify(NAA,3),b=qualify(NAA,3);check(a.token.equals(b.token));check(!a.token.equals(qualify("60000000000000000000000000000002",3).token));});
        for(int flag:new int[]{0x13,0x23,0x33,0x83,2})test(()->rejects(()->qualify(NAA,flag)));
        test(()->rejects(()->DiscoveryGraph.qualify("opaque","other","naa."+NAA,List.of(new DiscoveryGraph.Descriptor("NAA",0,descriptor(3,NAA))),Map.of())));
        test(()->rejects(()->DiscoveryGraph.qualify("opaque","opaque","naa."+NAA,List.of(new DiscoveryGraph.Descriptor("NAA",0,descriptor(3,NAA)),new DiscoveryGraph.Descriptor("NAA",0,descriptor(3,"60000000000000000000000000000002"))),Map.of())));
        test(()->check(DiscoveryGraph.qualify("opaque","opaque","naa."+NAA,List.of(new DiscoveryGraph.Descriptor("NAA",0,descriptor(3,NAA)),new DiscoveryGraph.Descriptor("NAA",0,descriptor(3,NAA))),Map.of()).token.endsWith(NAA)));
        test(()->rejects(()->response("<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///SENSITIVE'>]><x>&x;</x>".getBytes(StandardCharsets.UTF_8),"Login")));
        test(()->rejects(()->response(new byte[MAX_BYTES+1],"Login")));
        test(()->rejects(()->response(xml("Login","<returnval>"+"<a>".repeat(40)+"</a>".repeat(40)+"</returnval>"),"Login")));
        test(()->rejects(()->response(xml("Unexpected",""),"Login")));
        test(()->rejects(()->response(xml("Login","<evil xmlns=\"urn:evil\"/>"),"Login")));
        test(()->rejects(()->text(one(response(xml("Login","<returnval xsi:type=\"evil:string\" xmlns:evil=\"urn:evil\">SENSITIVE</returnval>"),"Login"),"returnval"))));
        test(()->{DiscoveryGraph.VmKey key=key("11111111-1111-1111-1111-111111111111","vm-1");DiscoveryGraph.Attachment a=new DiscoveryGraph.Attachment(key,"11111111-1111-1111-1111-111111111111","aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","2001","host-a",qualify(NAA,3),null),b=new DiscoveryGraph.Attachment(key,"11111111-1111-1111-1111-111111111111","aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","2002","host-b",qualify(NAA,3),null);DiscoveryGraph graph=new DiscoveryGraph(List.of(a,b),List.of());check(graph.luns.size()==1&&graph.attachments.size()==2);});
        test(()->{DiscoveryGraph.Attachment a=new DiscoveryGraph.Attachment(key("11111111-1111-1111-1111-111111111111","vm-1"),"11111111-1111-1111-1111-111111111111","aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","2001","host-a",qualify(NAA,3),null),b=new DiscoveryGraph.Attachment(key("11111111-1111-1111-1111-111111111111","vm-2"),"11111111-1111-1111-1111-111111111111","aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","2002","host-b",qualify(NAA,3),null);rejects(()->new DiscoveryGraph(List.of(a,b),List.of()));});
        test(()->rejects(()->DiscoveryGraph.qualify("opaque","opaque","naa."+NAA,List.of(new DiscoveryGraph.Descriptor("NAA",0,descriptor(3,NAA))),Map.of("capacity_bytes","9223372036854775808"))));
        test(()->{DiscoveryGraph.VmKey base=key("11111111-1111-1111-1111-111111111111","vm-1");List<DiscoveryGraph.Identifier> ids=new ArrayList<>(base.identifiers);ids.add(new DiscoveryGraph.Identifier("extra","different",true));DiscoveryGraph.VmKey different=new DiscoveryGraph.VmKey("VMWARE","VirtualMachine","same name",ids);DiscoveryGraph.Attachment a=new DiscoveryGraph.Attachment(base,"11111111-1111-1111-1111-111111111111","aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","2001","host-a",qualify(NAA,3),null),b=new DiscoveryGraph.Attachment(different,"11111111-1111-1111-1111-111111111111","aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","2002","host-a",qualify(NAA,3),null);rejects(()->new DiscoveryGraph(List.of(a,b),List.of()));});
        test(()->rejects(()->response(("<s:Envelope xmlns:s=\""+SOAP+"\"><s:Header/><s:Header/><s:Body/></s:Envelope>").getBytes(StandardCharsets.UTF_8),"Login")));
        if(args.length==2)test(()->tls(Path.of(args[0]),Path.of(args[1])));
        System.out.println("DiscoveryFoundationTest: PASS ("+tests+" deterministic cases; no inventory output)");
    }
    private static void tls(Path serverStore,Path caFile) throws Exception {
        char[] pass="synthetic-password".toCharArray();KeyStore store=KeyStore.getInstance("PKCS12");try(java.io.InputStream in=Files.newInputStream(serverStore)){store.load(in,pass);}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(store,pass);SSLContext serverContext=SSLContext.getInstance("TLS");serverContext.init(km.getKeyManagers(),null,null);
        X509Certificate ca;try(java.io.InputStream in=Files.newInputStream(caFile)){ca=(X509Certificate)java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in);}
        HttpsServer server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        server.createContext("/sdk",exchange->{try {String input=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);check(exchange.getRequestHeaders().getFirst("SOAPAction").equals("\"urn:vim25/6.5\""));byte[] body=xml("RetrieveServiceContent","");exchange.sendResponseHeaders(200,body.length);
            if(input.contains("slowSynthetic")) {for(int i=0;i<20;i++){exchange.getResponseBody().write(body[i]);exchange.getResponseBody().flush();try{Thread.sleep(100);}catch(InterruptedException ex){Thread.currentThread().interrupt();break;}}}
            else exchange.getResponseBody().write(body);
        }catch(java.io.IOException expectedClientDisconnect){/* No raw exception output. */}finally{exchange.close();}});server.start();
        try {int port=server.getAddress().getPort();byte[] req=request("RetrieveServiceContent","");SSLContext trust=HttpsSoapTransport.trust(List.of(ca));
            new HttpsSoapTransport(new URI("https://localhost:"+port+"/sdk"),trust,2000,2000).exchange("RetrieveServiceContent",req,null);
            rejects(()->new HttpsSoapTransport(new URI("https://127.0.0.1:"+port+"/sdk"),trust,2000,2000).exchange("RetrieveServiceContent",req,null));
            rejects(()->new HttpsSoapTransport(new URI("https://localhost:"+port+"/sdk"),SSLContext.getDefault(),2000,2000).exchange("RetrieveServiceContent",req,null));
            long start=System.nanoTime();rejects(()->new HttpsSoapTransport(new URI("https://localhost:"+port+"/sdk"),trust,2000,2000).exchange("RetrieveServiceContent",request("RetrieveServiceContent","<slowSynthetic/>"),null,200_000_000L));check(System.nanoTime()-start<1_500_000_000L);
            rejects(()->new HttpsSoapTransport(new URI("https://localhost:"+port+"/sdk"),trust,2000,2000).exchange("RetrieveServiceContent",req,null,1));
            try {Thread.currentThread().interrupt();rejects(()->new HttpsSoapTransport(new URI("https://localhost:"+port+"/sdk"),trust,2000,2000).exchange("RetrieveServiceContent",req,null));check(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
        } finally {server.stop(0);}
    }
}
