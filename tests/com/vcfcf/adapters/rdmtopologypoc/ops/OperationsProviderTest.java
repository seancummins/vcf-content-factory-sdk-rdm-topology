package com.vcfcf.adapters.rdmtopologypoc.ops;

import com.integrien.alive.common.adapter3.config.*;
import com.vcfcf.adapters.rdmtopologypoc.discovery.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

/** Synthetic-only suite. No environment credential, VMware endpoint, platform decryption or real inventory. */
public final class OperationsProviderTest {
    static final String VC = "11111111-1111-1111-1111-111111111111", VC2 = "22222222-2222-2222-2222-222222222222";
    static final String ID = "33333333-3333-3333-3333-333333333333", ID2 = "44444444-4444-4444-4444-444444444444";
    static final String SECRET = "SYNTHETIC-ONLY-SECRET", TOKEN = "synthetic-token";
    static int checks;
    interface Action { void run() throws Exception; }
    static void check(boolean value) { if (!value) throw new AssertionError("synthetic assertion failed"); checks++; }
    static void fails(OpsException.Code code, Action action) throws Exception {
        try { action.run(); throw new AssertionError("expected sanitized category " + code); }
        catch (OpsException ex) { check(ex.code() == code); check(ex.getCause() == null && ex.getSuppressed().length == 0);
            check(!ex.toString().contains(SECRET) && !ex.toString().contains(VC) && !ex.toString().contains(TOKEN)); }
    }
    static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    static SuiteApiVmProvider.Reply reply(int status, String body) { return new SuiteApiVmProvider.Reply(status, bytes(body)); }
    static String identifier(String name, String value, String flag) {
        return "{\"identifierType\":{\"name\":\"" + name + "\",\"isPartOfUniqueness\":" + flag + "},\"value\":\"" + value + "\"}";
    }
    static String resource(String id, String vc, String moid) {
        return "{\"identifier\":\"" + id + "\",\"resourceKey\":{\"adapterKindKey\":\"VMWARE\",\"resourceKindKey\":\"VirtualMachine\",\"name\":\"duplicate synthetic name\",\"resourceIdentifiers\":["
                + identifier("VMEntityObjectID", moid, "true") + "," + identifier("VMEntityVCID", vc, "true") + ","
                + identifier("nonunique", "", "false") + "," + identifier("case-preserved", "MiXeD", "false") + "]}}";
    }
    static String page(String resources, int total, int number) {
        return "{\"pageInfo\":{\"page\":" + number + ",\"pageSize\":200,\"totalCount\":" + total + "},\"resourceList\":[" + resources + "]}";
    }
    static final class Fake implements SuiteApiVmProvider.Transport {
        List<SuiteApiVmProvider.Reply> replies = new ArrayList<>(); List<String> paths = new ArrayList<>();
        List<String> methods = new ArrayList<>(); List<byte[]> bodies = new ArrayList<>(); List<String> tokens = new ArrayList<>();
        int at; OpsException cleanup;
        Fake(String listing, String detail) { replies.add(reply(200, "{\"token\":\"" + TOKEN + "\",\"validity\":9999999999999}")); replies.add(reply(200, listing)); if (detail != null) replies.add(reply(200, detail)); }
        @Override public SuiteApiVmProvider.Reply exchange(String method, String path, byte[] body, String token, long remaining) throws OpsException {
            OpsHttpsTransport.validate(method, path, body, token); paths.add(path); methods.add(method); bodies.add(body.clone()); tokens.add(token);
            if (path.endsWith("/release")) { if (cleanup != null) throw cleanup; return reply(200, ""); }
            if (at >= replies.size()) throw new OpsException(OpsException.Code.INVALID_RESPONSE); return replies.get(at++);
        }
        SuiteApiVmProvider provider() { return new SuiteApiVmProvider(this, () -> new OperationsBinding.Credentials("synthetic-instance", SECRET.toCharArray())); }
    }
    static Fake normal() { String row = resource(ID, VC, "vm-71"); return new Fake(page(row, 1, 0), row); }
    static void providerTests() throws Exception {
        Fake fake = normal(); SuiteApiVmProvider provider = fake.provider();
        DiscoveryGraph.VmKey key = provider.resolve(VC, "vm-71");
        check(key.identifiers.size() == 4 && key.identifiers.get(2).value.equals("") && !key.identifiers.get(2).unique);
        check(key.identifiers.get(3).value.equals("MiXeD") && key.displayName.equals("duplicate synthetic name"));
        check(fake.paths.equals(List.of("/api/auth/token/acquire", "/api/resources?adapterKind=VMWARE&resourceKind=VirtualMachine&page=0&pageSize=200", "/api/resources/" + ID, "/api/auth/token/release")));
        check(!new String(fake.bodies.get(0), StandardCharsets.UTF_8).contains("authSource"));
        Map<String,Object> authSent=OpsJson.object(OpsJson.parse(fake.bodies.get(0)));
        check(authSent.size()==2&&authSent.get("username").equals("synthetic-instance")&&authSent.get("password").equals(SECRET));
        String escaped="synthetic\\\"\n\t\ud83d\ude00";
        try(OperationsBinding.Credentials c=new OperationsBinding.Credentials(escaped,escaped.toCharArray())) {
            Map<String,Object> parsed=OpsJson.object(OpsJson.parse(c.request()));check(parsed.size()==2&&parsed.get("username").equals(escaped)&&parsed.get("password").equals(escaped));
        }
        check(fake.bodies.get(3).length == 0 && fake.tokens.get(3).equals(TOKEN));
        Fake two = new Fake(page(resource(ID2, VC2, "vm-71") + "," + resource(ID, VC, "vm-71"), 2, 0), resource(ID, VC, "vm-71"));
        check(two.provider().resolve(VC, "vm-71").scoped("VMEntityVCID").equals(VC));
        String row = resource(ID, VC, "vm-71");
        for (int status : new int[] {401,403,302,429,500}) {
            Fake f = normal(); f.replies.set(0, reply(status, SECRET));
            fails(status == 401 ? OpsException.Code.AUTHENTICATION : status == 403 ? OpsException.Code.AUTHORIZATION : OpsException.Code.INVALID_RESPONSE, () -> f.provider().resolve(VC, "vm-71"));
            check(f.paths.size() == 1);
        }
        for (int status : new int[] {401,403,404,500}) {
            Fake f = normal(); f.replies.set(2, reply(status, SECRET));
            fails(status == 401 ? OpsException.Code.AUTHENTICATION : status == 403 ? OpsException.Code.AUTHORIZATION : status == 404 ? OpsException.Code.NOT_FOUND : OpsException.Code.INVALID_RESPONSE, () -> f.provider().resolve(VC, "vm-71"));
            check(f.paths.get(f.paths.size()-1).endsWith("/release"));
        }
        fails(OpsException.Code.NOT_FOUND, () -> new Fake(page("",0,0),null).provider().resolve(VC,"vm-71"));
        fails(OpsException.Code.AMBIGUOUS, () -> new Fake(page(row+","+row,2,0),null).provider().resolve(VC,"vm-71"));
        fails(OpsException.Code.AMBIGUOUS, () -> new Fake(page(row+","+resource(ID2,VC,"vm-71"),2,0),null).provider().resolve(VC,"vm-71"));
        fails(OpsException.Code.IDENTITY_MISMATCH, () -> new Fake(page(row,1,0),resource(ID,VC2,"vm-71")).provider().resolve(VC,"vm-71"));
        fails(OpsException.Code.IDENTITY_MISMATCH, () -> new Fake(page(row,1,0),row.replace("MiXeD","changed")).provider().resolve(VC,"vm-71"));
        fails(OpsException.Code.IDENTITY_MISMATCH, () -> new Fake(page(row,1,0),row.replace("duplicate synthetic name","new synthetic name")).provider().resolve(VC,"vm-71"));
        fails(OpsException.Code.IDENTITY_MISMATCH, () -> new Fake(page(row,1,0),row.replace(identifier("case-preserved","MiXeD","false"),identifier("case-preserved","MiXeD","true"))).provider().resolve(VC,"vm-71"));
        fails(OpsException.Code.IDENTITY_MISMATCH, () -> new Fake(page(row,1,0),row.replace(identifier("nonunique","","false")+","+identifier("case-preserved","MiXeD","false"),identifier("case-preserved","MiXeD","false")+","+identifier("nonunique","","false"))).provider().resolve(VC,"vm-71"));
        for (String broken : List.of(row.replace("\"isPartOfUniqueness\":true","\"isPartOfUniqueness\":false"),
                row.replace("\"isPartOfUniqueness\":true","\"isPartOfUniqueness\":\"true\""),
                row.replace("\"value\":\"\"","\"value\":null"), row.replace("\"value\":\"\"", "\"other\":\"\""),
                row.replace("VMEntityVCID", "unknown"), row.replace("nonunique", "VMEntityObjectID"), row.replace("VMWARE","OTHER")))
            fails(OpsException.Code.INVALID_RESOURCE_KEY, () -> new Fake(page(broken,1,0),null).provider().resolve(VC,"vm-71"));
        for (String broken : List.of(page(row,2,0),page(row,1,1),page(row,1,0).replace("200","201"),page(row,20001,0)))
            fails(OpsException.Code.INCOMPLETE, () -> new Fake(broken,null).provider().resolve(VC,"vm-71"));
        StringJoiner many = new StringJoiner(",");
        for (int i=1;i<=200;i++) many.add(resource(String.format("%08x-1111-1111-1111-111111111111",i),VC,"vm-"+i));
        Fake paged = new Fake(page(many.toString(),201,0),null); paged.replies.add(reply(200,page(resource(ID2,VC2,"vm-71"),201,1))); paged.replies.add(reply(200,resource("00000047-1111-1111-1111-111111111111",VC,"vm-71")));
        check(paged.provider().resolve(VC,"vm-71").scoped("VMEntityObjectID").equals("vm-71"));
        Fake incomplete = new Fake(page(many.toString(),201,0),null); incomplete.replies.add(reply(200,page("",201,1)));
        fails(OpsException.Code.INCOMPLETE, () -> incomplete.provider().resolve(VC,"vm-71"));
        Fake changed = new Fake(page(many.toString(),201,0),null); changed.replies.add(reply(200,page("",202,1)));
        fails(OpsException.Code.INCOMPLETE, () -> changed.provider().resolve(VC,"vm-71"));
        Fake repeated = new Fake(page(many.toString(),201,0),null); repeated.replies.add(reply(200,page(resource("00000047-1111-1111-1111-111111111111",VC,"vm-71"),201,1)));
        fails(OpsException.Code.AMBIGUOUS, () -> repeated.provider().resolve(VC,"vm-71"));
        StringJoiner bigFirst=new StringJoiner(","),bigSecond=new StringJoiner(",");Set<String> bigTargets=new LinkedHashSet<>();
        for(int i=1;i<=300;i++){String value=resource(String.format("%08x-2222-2222-2222-222222222222",i),VC,"vm-"+i).replace("MiXeD","a".repeat(8192));
            (i<=200?bigFirst:bigSecond).add(value);bigTargets.add("vm-"+i);}
        Fake selectedLimit=new Fake(page(bigFirst.toString(),300,0),null);selectedLimit.replies.add(reply(200,page(bigSecond.toString(),300,1)));
        fails(OpsException.Code.RESPONSE_LIMIT,()->selectedLimit.provider().capture(VC,bigTargets));
        AtomicInteger aggregatePage=new AtomicInteger();AtomicBoolean aggregateReleased=new AtomicBoolean();
        SuiteApiVmProvider aggregateLimit=new SuiteApiVmProvider((method,path,body,token,remaining)->{
            if(path.endsWith("/acquire"))return reply(200,"{\"token\":\""+TOKEN+"\",\"validity\":9999999999999}");
            if(path.endsWith("/release")){aggregateReleased.set(true);return reply(200,"");}
            int p=aggregatePage.getAndIncrement();StringJoiner values=new StringJoiner(",");
            for(int j=0;j<200;j++){int i=p*200+j+1;String r=resource(String.format("%08x-3333-3333-3333-333333333333",i),VC,"vm-"+i);
                values.add(r.substring(0,r.length()-1)+",\"padding\":\""+"a".repeat(16000)+"\"}");}
            return reply(200,page(values.toString(),2200,p));
        },()->new OperationsBinding.Credentials("synthetic",SECRET.toCharArray()));
        fails(OpsException.Code.RESPONSE_LIMIT,()->aggregateLimit.resolve(VC,"vm-71"));check(aggregateReleased.get());
        Fake cleanup = normal(); cleanup.cleanup = new OpsException(OpsException.Code.TLS); SuiteApiVmProvider cp = cleanup.provider();
        check(cp.resolve(VC,"vm-71") != null && cp.cleanupFailure().get() == OpsException.Code.TLS);
        Fake primary = new Fake(page("",0,0),null); primary.cleanup = new OpsException(OpsException.Code.TLS); SuiteApiVmProvider pp = primary.provider();
        fails(OpsException.Code.NOT_FOUND, () -> pp.resolve(VC,"vm-71")); check(pp.cleanupFailure().get() == OpsException.Code.TLS);
        Fake expired = normal(); expired.replies.set(0,reply(200,"{\"token\":\""+TOKEN+"\",\"validity\":1}"));
        fails(OpsException.Code.AUTHENTICATION, () -> expired.provider().resolve(VC,"vm-71")); check(expired.paths.size()==2);
        Fake badExpiry=normal(); badExpiry.replies.set(0,reply(200,"{\"token\":\""+TOKEN+"\",\"validity\":\"invalid\"}"));
        fails(OpsException.Code.INVALID_RESPONSE,()->badExpiry.provider().resolve(VC,"vm-71"));check(badExpiry.paths.size()==2);
        Fake badToken=normal();badToken.replies.set(0,reply(200,"{\"token\":\"bad\\r\\nheader\",\"validity\":9999999999999}"));
        fails(OpsException.Code.INVALID_RESPONSE,()->badToken.provider().resolve(VC,"vm-71"));check(badToken.paths.size()==1);
        Fake punctuated=normal();punctuated.replies.set(0,reply(200,"{\"token\":\"synthetic:opaque!value\",\"validity\":9999999999999}"));
        check(punctuated.provider().resolve(VC,"vm-71")!=null&&punctuated.tokens.get(3).equals("synthetic:opaque!value"));
        for(String token:List.of("a b","a\r\nb","a\tb","nonASCII\u00e9",""))check(!OpsHttpsTransport.validToken(token));
        char[] owned=SECRET.toCharArray();OperationsBinding.Credentials erased=new OperationsBinding.Credentials("synthetic",owned);erased.close();
        check(new String(owned).chars().allMatch(c->c==0));fails(OpsException.Code.CREDENTIAL_UNAVAILABLE,erased::request);
        AtomicLong clock = new AtomicLong(); Fake timed = normal(); SuiteApiVmProvider timedProvider = new SuiteApiVmProvider(timed,()->new OperationsBinding.Credentials("synthetic",SECRET.toCharArray()),clock::get,()->0);
        SuiteApiVmProvider.Snapshot snapshot = timedProvider.capture(VC,Set.of("vm-71"));
        fails(OpsException.Code.IDENTITY_MISMATCH,()->snapshot.resolve(VC,null));
        fails(OpsException.Code.IDENTITY_MISMATCH,()->snapshot.resolve(null,"vm-71"));
        fails(OpsException.Code.IDENTITY_MISMATCH,()->snapshot.resolve(VC,"71"));
        try { snapshot.resolver().resolve(VC,null);throw new AssertionError("expected sanitized graph failure"); }
        catch(DiscoveryException ex){check(ex.code()==DiscoveryException.Code.IDENTITY&&ex.getCause()==null);}
        check(snapshot.resolver().resolve(VC,"vm-71") != null); clock.set(120_000_000_000L);
        fails(OpsException.Code.TIMEOUT, () -> snapshot.resolve(VC,"vm-71"));
        fails(OpsException.Code.CREDENTIAL_UNAVAILABLE, () -> new SuiteApiVmProvider(normal(),()->null).resolve(VC,"vm-71"));
        fails(OpsException.Code.INVALID_RESPONSE, () -> new SuiteApiVmProvider((m,p,b,t,n)-> {throw new IllegalStateException(SECRET);},()->new OperationsBinding.Credentials("synthetic",SECRET.toCharArray())).resolve(VC,"vm-71"));
        fails(OpsException.Code.RUNTIME_UNQUALIFIED,OperationsBinding::production);
        fails(OpsException.Code.CONFIGURATION,()->provider.resolve("bare", "vm-71"));
        fails(OpsException.Code.IDENTITY_MISMATCH,()->provider.resolve(VC,"71"));
    }
    static void jsonTests() throws Exception {
        for (String invalid : List.of("{\"a\":null,\"a\":1}","{\"a\":true,}","[1,]","01","+1","NaN","true false","\"\\u+041\"","\"\\u-001\"","\"\\ud800\"","\"\\udc00\"", "\"\n\"", "1e999999999999"))
            fails(OpsException.Code.INVALID_RESPONSE,()->OpsJson.parse(bytes(invalid)));
        fails(OpsException.Code.INVALID_RESPONSE,()->OpsJson.parse(new byte[]{(byte)0xc0,(byte)0x80}));
        fails(OpsException.Code.RESPONSE_LIMIT,()->OpsJson.parse(bytes("[".repeat(26)+"0"+"]".repeat(26))));
        fails(OpsException.Code.RESPONSE_LIMIT,()->OpsJson.parse(bytes("\""+"a".repeat(16385)+"\"")));
        fails(OpsException.Code.RESPONSE_LIMIT,()->OpsJson.parse(new byte[OpsJson.MAX_BYTES+1]));
        fails(OpsException.Code.RESPONSE_LIMIT,()->OpsJson.parse(bytes("["+"0,".repeat(100001)+"0]")));
        check(OpsJson.string(OpsJson.parse(bytes("\"\\ud83d\\ude00\""))).equals("\ud83d\ude00"));
        fails(OpsException.Code.INVALID_RESPONSE,()->OpsJson.integer(OpsJson.parse(bytes("1.1"))));
        fails(OpsException.Code.INVALID_RESPONSE,()->OpsJson.integer(OpsJson.parse(bytes("9223372036854775808"))));
    }
    static void bindingTests(String caPath) throws Exception {
        AdapterConfig config = new AdapterConfig("vcfcf_rdm_topology_poc",1);
        config.setAdapterCredentials(new AdapterCredentialConfig("synthetic-instance","synthetic-encrypted-slot"));
        OperationsBinding binding = OperationsBinding.dormant(config,"https://ops.example.test/suite-api","jvm",null,null);
        try (OperationsBinding.Credentials creds = binding.credentials(c->"synthetic-instance",c->SECRET)) { check(creds.request().length>0); }
        fails(OpsException.Code.CREDENTIAL_UNAVAILABLE,binding::credentials); // actual accessor cannot decrypt without platform Crypt
        fails(OpsException.Code.CREDENTIAL_UNAVAILABLE,()->binding.credentials(c->"synthetic",c->{throw new NoClassDefFoundError(SECRET);}));
        fails(OpsException.Code.CREDENTIAL_UNAVAILABLE,()->binding.credentials(c->"synthetic",c->""));
        config.setAdapterCredentials(new AdapterCredentialConfig("replacement","synthetic"));
        fails(OpsException.Code.CREDENTIAL_UNAVAILABLE,()->binding.credentials(c->"synthetic",c->SECRET));
        for (String endpoint : List.of("http://ops.example.test/suite-api","https://localhost/suite-api","https://127.0.0.1/suite-api","https://ops/suite-api","https://user:secret@ops.example.test/suite-api","https://ops.example.test/suite-api?x=y","https://ops.example.test/suite-api/","https://ops.example.test:0/suite-api"))
            fails(OpsException.Code.CONFIGURATION,()->OperationsBinding.dormant(config,endpoint,"jvm",null,null));
        fails(OpsException.Code.CONFIGURATION,()->OperationsBinding.dormant(config,"https://ops.example.test/suite-api","insecure",null,null));
        fails(OpsException.Code.CONFIGURATION,()->OperationsBinding.dormant(config,"https://"+"a".repeat(64)+".test/suite-api","jvm",null,null));
        fails(OpsException.Code.CONFIGURATION,()->OperationsBinding.dormant(config,"https://ops.example.test/suite-api","enterprise_ca",null,List.of("opaque-id")));
        if (caPath != null) {
            CertificateConfig cert = new CertificateConfig(); cert.setCertificateString(Files.readString(Path.of(caPath)));
            check(OperationsBinding.dormant(config,"https://ops.example.test/suite-api","enterprise_ca",List.of(cert),null).dormantProvider()!=null);
            cert.setEnforceHostnameVertification(false);
            fails(OpsException.Code.CONFIGURATION,()->OperationsBinding.dormant(config,"https://ops.example.test/suite-api","enterprise_ca",List.of(cert),null));
        }
    }
    interface Handler { void handle(SSLSocket socket) throws Exception; }
    static final class Server implements AutoCloseable {
        final SSLServerSocket socket; final Thread thread; final AtomicReference<Throwable> failure = new AtomicReference<>();
        Server(SSLContext context, Handler handler) throws Exception {
            this(context,1,handler);
        }
        Server(SSLContext context,int connections, Handler handler) throws Exception {
            socket=(SSLServerSocket)context.getServerSocketFactory().createServerSocket(0,8,InetAddress.getLoopbackAddress());
            thread = new Thread(()-> { try {for(int i=0;i<connections;i++) try (SSLSocket client=(SSLSocket)socket.accept()) { handler.handle(client); }} catch(Exception ex) { if(!socket.isClosed()) failure.set(ex); } },"synthetic-ops-test-server");
            thread.setDaemon(true); thread.start();
        }
        @Override public void close() throws Exception { socket.close(); thread.join(2000); }
        OpsHttpsTransport transport(SSLContext context, String host) throws Exception { return new OpsHttpsTransport(OperationsBinding.endpoint("https://"+host+":"+socket.getLocalPort()+"/suite-api",true),context,1000,1000); }
    }
    static String readRequest(SSLSocket socket) throws Exception {
        return receive(socket).headers;
    }
    static final class Received { final String headers; final byte[] body;Received(String headers,byte[] body){this.headers=headers;this.body=body;} }
    static Received receive(SSLSocket socket) throws Exception {
        InputStream in=socket.getInputStream();ByteArrayOutputStream raw=new ByteArrayOutputStream();int state=0;
        while(state!=4){int n=in.read();if(n<0||raw.size()>65536)throw new AssertionError("synthetic request framing");raw.write(n);
            state=state==0&&n=='\r'?1:state==1&&n=='\n'?2:state==2&&n=='\r'?3:state==3&&n=='\n'?4:0;}
        String headers=raw.toString(StandardCharsets.US_ASCII).replace("\r\n","\n");int length=0;
        for(String line:headers.split("\n"))if(line.startsWith("Content-Length: "))length=Integer.parseInt(line.substring(16));
        if(length>32768)throw new AssertionError("synthetic request too large");byte[] body=in.readNBytes(length);
        if(body.length!=length)throw new AssertionError("synthetic incomplete request");return new Received(headers,body);
    }
    static String response(String fields,String body) { return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"+fields+"\r\n"+body; }
    static void tlsTests(String serverPath,String caPath) throws Exception {
        KeyStore store=KeyStore.getInstance("PKCS12"); try(InputStream in=Files.newInputStream(Path.of(serverPath))) {store.load(in,"synthetic-password".toCharArray());}
        KeyManagerFactory keys=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); keys.init(store,"synthetic-password".toCharArray()); SSLContext server=SSLContext.getInstance("TLS");server.init(keys.getKeyManagers(),null,null);
        CertificateConfig ca=new CertificateConfig();ca.setCertificateString(Files.readString(Path.of(caPath))); SSLContext client=OperationsBinding.trust("enterprise_ca",List.of(ca),null);
        AtomicInteger ambientCalls=new AtomicInteger(); java.net.Authenticator oldAuth=java.net.Authenticator.getDefault(); CookieHandler oldCookies=CookieHandler.getDefault();
        try {
            java.net.Authenticator.setDefault(new java.net.Authenticator(){@Override protected PasswordAuthentication getPasswordAuthentication(){ambientCalls.incrementAndGet();return new PasswordAuthentication("ambient",SECRET.toCharArray());}});
            CookieHandler.setDefault(new CookieHandler(){@Override public Map<String,List<String>> get(URI u,Map<String,List<String>> h){ambientCalls.incrementAndGet();return Map.of("Cookie",List.of(SECRET));}@Override public void put(URI u,Map<String,List<String>> h){ambientCalls.incrementAndGet();}});
            try(Server s=new Server(server,socket->{String req=readRequest(socket); if(req.contains("Cookie")||!req.startsWith("GET /suite-api/api/resources/")) throw new AssertionError("unexpected synthetic request"); socket.getOutputStream().write(bytes(response("Content-Length: 2\r\nSet-Cookie: ignored=synthetic\r\n","{}")));})) {
                check(s.transport(client,"localhost").exchange("GET","/api/resources/"+ID,new byte[0],TOKEN,2_000_000_000L).status==200);
                check(s.failure.get()==null);
            }
            try(Server s=new Server(server,socket->{readRequest(socket);socket.getOutputStream().write(bytes("HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=synthetic\r\nContent-Length: 0\r\n\r\n"));})) {check(s.transport(client,"localhost").exchange("GET","/api/resources/"+ID,new byte[0],TOKEN,2_000_000_000L).status==401);}
            check(ambientCalls.get()==0);
        } finally { java.net.Authenticator.setDefault(oldAuth);CookieHandler.setDefault(oldCookies); }
        try(Server s=new Server(server,socket->readRequest(socket))) {fails(OpsException.Code.TLS,()->s.transport(SSLContext.getDefault(),"localhost").exchange("GET","/api/resources/"+ID,new byte[0],TOKEN,2_000_000_000L));}
        try(Server s=new Server(server,socket->readRequest(socket))) {fails(OpsException.Code.TLS,()->s.transport(client,"127.0.0.1").exchange("GET","/api/resources/"+ID,new byte[0],TOKEN,2_000_000_000L));}
        // Malicious test-only ExtendedTrustManager intentionally omits endpoint checks. Manual SAN fence must still reject before HTTP.
        SSLContext permissive=SSLContext.getInstance("TLS");permissive.init(null,new TrustManager[]{new X509ExtendedTrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] c,String a){}public void checkServerTrusted(X509Certificate[] c,String a){}
            public void checkClientTrusted(X509Certificate[] c,String a,Socket s){}public void checkServerTrusted(X509Certificate[] c,String a,Socket s){}
            public void checkClientTrusted(X509Certificate[] c,String a,SSLEngine e){}public void checkServerTrusted(X509Certificate[] c,String a,SSLEngine e){}
        }},null);
        AtomicInteger preflightBytes=new AtomicInteger(-2);
        try(Server s=new Server(server,socket->{socket.startHandshake();preflightBytes.set(socket.getInputStream().read());})) {
            fails(OpsException.Code.TLS,()->s.transport(permissive,"127.0.0.1").exchange("POST","/api/auth/token/acquire",bytes("{\"password\":\""+SECRET+"\"}"),null,2_000_000_000L));
            s.thread.join(1000);check(preflightBytes.get()==-1&&s.failure.get()==null&&!s.thread.isAlive());
        }
        List<String> observed=Collections.synchronizedList(new ArrayList<>());
        try(Server s=new Server(server,4,socket->{
            Received received=receive(socket);String req=received.headers;String first=req.substring(0,req.indexOf('\n'));observed.add(first);
            boolean auth=first.startsWith("POST /suite-api/api/auth/token/acquire ");
            boolean release=first.startsWith("POST /suite-api/api/auth/token/release ");
            if(auth){Map<String,Object> actual=OpsJson.object(OpsJson.parse(received.body));if(actual.size()!=2||!"synthetic-instance".equals(actual.get("username"))||!SECRET.equals(actual.get("password")))throw new AssertionError("synthetic authentication payload incorrect");}
            if(!auth&&!req.contains("Authorization: OpsToken "+TOKEN))throw new AssertionError("missing synthetic token");
            if(release&&!req.contains("Content-Length: 0"))throw new AssertionError("release body not empty");
            String body=auth?"{\"token\":\""+TOKEN+"\",\"validity\":9999999999999}":release?"":first.contains("page=")?page(resource(ID,VC,"vm-71"),1,0):resource(ID,VC,"vm-71");
            socket.getOutputStream().write(bytes(response("Content-Length: "+bytes(body).length+"\r\n",body)));
        })) {
            AdapterConfig sdk=new AdapterConfig("vcfcf_rdm_topology_poc",1);sdk.setAdapterCredentials(new AdapterCredentialConfig("synthetic-instance","synthetic-slot"));
            OperationsBinding binding=OperationsBinding.dormant(sdk,"https://ops.example.test/suite-api","enterprise_ca",List.of(ca),null);
            SuiteApiVmProvider liveFake=new SuiteApiVmProvider(s.transport(client,"localhost"),()->binding.credentials(c->"synthetic-instance",c->SECRET));
            check(liveFake.resolve(VC,"vm-71").identifiers.get(2).value.equals(""));s.thread.join(1000);
            check(s.failure.get()==null&&observed.size()==4&&observed.get(0).startsWith("POST ")&&observed.get(3).startsWith("POST "));
        }
        for(String valid:List.of(response("Content-Length: 2\r\n","{}"),response("Transfer-Encoding: chunked\r\n","2\r\n{}\r\n0\r\n\r\n"),response("","{}"))) {
            try(Server s=new Server(server,socket->{readRequest(socket);socket.getOutputStream().write(bytes(valid));})) {check(Arrays.equals(s.transport(client,"localhost").exchange("GET","/api/resources/"+ID,new byte[0],TOKEN,2_000_000_000L).body,bytes("{}")));}
        }
        for(String invalid:List.of(response("Content-Length: 2\r\nContent-Length: 2\r\n","{}"),response("Content-Length: 2\r\nTransfer-Encoding: chunked\r\n","{}"),response("Transfer-Encoding: gzip\r\n","{}"),response("Transfer-Encoding: chunked\r\n","2;x=y\r\n{}\r\n0\r\n\r\n"),response("Transfer-Encoding: chunked\r\n","2\r\n{}\r\n0\r\nTrailer: x\r\n\r\n"),response("Content-Length: 3\r\n","{}"),response("Content-Length: -1\r\n","{}"),"HTTP/1.1 200 OK\r\n Folded: x\r\n\r\n{}",response("Transfer-Encoding: chunked\r\n","2\r\n{}xx0\r\n\r\n"))) {
            try(Server s=new Server(server,socket->{readRequest(socket);socket.getOutputStream().write(bytes(invalid));})) {fails(OpsException.Code.INVALID_RESPONSE,()->s.transport(client,"localhost").exchange("GET","/api/resources/"+ID,new byte[0],TOKEN,2_000_000_000L));}
        }
        try(Server s=new Server(server,socket->{readRequest(socket);socket.getOutputStream().write(bytes(response("Content-Length: 4194305\r\n","")));})) {fails(OpsException.Code.RESPONSE_LIMIT,()->s.transport(client,"localhost").exchange("GET","/api/resources/"+ID,new byte[0],TOKEN,2_000_000_000L));}
        try(Server s=new Server(server,socket->{readRequest(socket);socket.getOutputStream().write(bytes(response("","")));socket.getOutputStream().write(new byte[OpsJson.MAX_BYTES+1]);})) {fails(OpsException.Code.RESPONSE_LIMIT,()->s.transport(client,"localhost").exchange("GET","/api/resources/"+ID,new byte[0],TOKEN,2_000_000_000L));}
        try(Server s=new Server(server,socket->{readRequest(socket);socket.getOutputStream().write(bytes(response("Transfer-Encoding: chunked\r\n","400001\r\n")));})) {fails(OpsException.Code.RESPONSE_LIMIT,()->s.transport(client,"localhost").exchange("GET","/api/resources/"+ID,new byte[0],TOKEN,2_000_000_000L));}
        try(Server s=new Server(server,socket->{readRequest(socket);for(int i=0;i<50;i++){socket.getOutputStream().write('H');socket.getOutputStream().flush();Thread.sleep(25);}})) {long start=System.nanoTime();fails(OpsException.Code.TIMEOUT,()->s.transport(client,"localhost").exchange("GET","/api/resources/"+ID,new byte[0],TOKEN,150_000_000L));check(System.nanoTime()-start<1_000_000_000L);}
        try(Server s=new Server(server,socket->{readRequest(socket);socket.getOutputStream().write(bytes("HTTP/1.1 302 Found\r\nLocation: https://untrusted.example.test/\r\n\r\n"));})) {check(s.transport(client,"localhost").exchange("GET","/api/resources/"+ID,new byte[0],TOKEN,2_000_000_000L).status==302);}
    }
    public static void main(String[] args) throws Exception {
        jsonTests(); providerTests(); bindingTests(args.length>1?args[1]:null);
        if(args.length>1) tlsTests(args[0],args[1]);
        System.out.println("Operations provider: "+checks+" synthetic assertions passed; live runtime unqualified");
    }
}
