package com.vcfcf.adapters.rdmtopologypoc.diagnostic;

import com.vcfcf.adapters.rdmtopologypoc.discovery.*;
import com.vcfcf.adapters.rdmtopologypoc.ops.*;
import com.vcfcf.adapters.rdmtopologypoc.runtime.RuntimeDiscoveryBinding;
import com.integrien.alive.common.adapter3.config.AdapterConfig;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.Authenticator;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

/** Actual native SOAP + Operations transports composed against ephemeral localhost TLS servers only. */
public final class DiagnosticTlsTest {
    static int count;static void check(boolean b){if(!b)throw new AssertionError("synthetic TLS diagnostic assertion");count++;}
    interface Action{void run()throws Exception;}
    static void rejects(DiscoveryException.Code expected,Action action)throws Exception{
        try{action.run();throw new AssertionError("expected sanitized rejection");}catch(DiscoveryException ex){check(ex.code()==expected&&ex.getCause()==null&&!ex.toString().contains("SENSITIVE"));}
    }
    static SSLContext permissive()throws Exception{
        X509ExtendedTrustManager accept=new X509ExtendedTrustManager(){
            public X509Certificate[]getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[]c,String a){}public void checkServerTrusted(X509Certificate[]c,String a){}
            public void checkClientTrusted(X509Certificate[]c,String a,Socket s){}public void checkServerTrusted(X509Certificate[]c,String a,Socket s){}
            public void checkClientTrusted(X509Certificate[]c,String a,SSLEngine s){}public void checkServerTrusted(X509Certificate[]c,String a,SSLEngine s){}
        };
        SSLContext context=SSLContext.getInstance("TLS");context.init(null,new TrustManager[]{accept},null);return context;
    }
    public static void main(String[]args)throws Exception{
        KeyStore store=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(Path.of(args[0]))){store.load(in,"synthetic-password".toCharArray());}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(store,"synthetic-password".toCharArray());
        SSLContext serverContext=SSLContext.getInstance("TLS");serverContext.init(km.getKeyManagers(),null,null);
        X509Certificate ca;try(InputStream in=Files.newInputStream(Path.of(args[1]))){ca=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);}
        SSLContext trusted=HttpsSoapTransport.trust(List.of(ca));
        HttpsServer server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        ExecutorService handlers=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"synthetic-diagnostic-server");t.setDaemon(true);return t;});server.setExecutor(handlers);
        DiagnosticSoapFixture soap=new DiagnosticSoapFixture();AtomicReference<Throwable> handlerFailure=new AtomicReference<>();AtomicInteger mode=new AtomicInteger(),httpRequests=new AtomicInteger();
        server.createContext("/sdk",exchange->{
            try{
                httpRequests.incrementAndGet();String input=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                String op=null;for(String candidate:List.of("RetrieveServiceContent","Login","Logout","RetrievePropertiesEx","ContinueRetrievePropertiesEx","CancelRetrievePropertiesEx"))if(input.contains("<"+candidate+">"))op=candidate;
                if(op==null)throw new AssertionError("Unexpected SOAP operation");
                if(!"\"urn:vim25/6.5\"".equals(exchange.getRequestHeaders().getFirst("SOAPAction")))throw new AssertionError("Wrong SOAPAction");
                if(op.equals("Login"))exchange.getResponseHeaders().add("Set-Cookie","vmware_soap_session=synthetic; Secure; HttpOnly");
                else if(!op.equals("RetrieveServiceContent")&&!"vmware_soap_session=synthetic".equals(exchange.getRequestHeaders().getFirst("Cookie")))throw new AssertionError("Missing managed session");
                byte[] reply=soap.response(op,input);
                exchange.getResponseHeaders().add("Content-Type","text/xml; charset=utf-8");
                if(mode.get()==1){exchange.sendResponseHeaders(200,reply.length);for(byte b:reply){exchange.getResponseBody().write(b);exchange.getResponseBody().flush();Thread.sleep(20);}}
                else if(mode.get()==2){exchange.sendResponseHeaders(500,0);exchange.getResponseBody().write("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault><faultstring>SENSITIVE</faultstring></s:Fault></s:Body></s:Envelope>".getBytes(StandardCharsets.UTF_8));}
                else{exchange.sendResponseHeaders(200,reply.length);exchange.getResponseBody().write(reply);}
            }catch(IOException ex){/* client timeout/rejection deliberately closes */}
            catch(Throwable ex){handlerFailure.compareAndSet(null,ex);}
            finally{exchange.close();}
        });
        server.createContext("/suite-api",exchange->{
            try{
                httpRequests.incrementAndGet();String path=exchange.getRequestURI().toString().substring("/suite-api".length());exchange.getRequestBody().readAllBytes();
                if(!path.endsWith("acquire")&&!"OpsToken synthetic-token".equals(exchange.getRequestHeaders().getFirst("Authorization")))throw new AssertionError("Missing Operations token");
                byte[] bytes=DiagnosticOpsFixture.response(path).getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length==0?-1:bytes.length);exchange.getResponseBody().write(bytes);
            }catch(Throwable ex){handlerFailure.compareAndSet(null,ex);}finally{exchange.close();}
        });
        CookieHandler cookies=CookieHandler.getDefault();ProxySelector proxy=ProxySelector.getDefault();Authenticator auth=Authenticator.getDefault();AtomicInteger globalCallbacks=new AtomicInteger();
        try{
            CookieHandler.setDefault(new CookieHandler(){public Map<String,List<String>>get(URI u,Map<String,List<String>>r){globalCallbacks.incrementAndGet();return Map.of();}public void put(URI u,Map<String,List<String>>r){globalCallbacks.incrementAndGet();}});
            ProxySelector.setDefault(new ProxySelector(){public List<Proxy>select(URI u){globalCallbacks.incrementAndGet();return List.of(Proxy.NO_PROXY);}public void connectFailed(URI u,SocketAddress a,IOException e){globalCallbacks.incrementAndGet();}});
            Authenticator.setDefault(new Authenticator(){protected PasswordAuthentication getPasswordAuthentication(){globalCallbacks.incrementAndGet();return new PasswordAuthentication("SENSITIVE","SENSITIVE".toCharArray());}});
            server.start();int port=server.getAddress().getPort();URI endpoint=URI.create("https://localhost:"+port+"/sdk");
            try(DiagnosticRuntime d=new DiagnosticRuntime(c->DiagnosticRuntime.compose(c,DiagnosticOpsFixture.tlsProvider(trusted,port),resolver->
                    new VimDiscoveryClient(new IsolatedSoapTransport(endpoint,trusted,1000,1000),resolver,c.vc,c.moid).discover("synthetic-user","SENSITIVE-SYNTHETIC".toCharArray())),10000)){
                d.configure(DiagnosticRuntimeTest.config());DiagnosticRuntime.Report r=d.executeOnce();check(r.state==DiagnosticRuntime.State.PASS&&r.mappingCount==1&&r.lunCount==1);check(d.executeOnce().mappingCount==1);
                check(soap.calls.equals(List.of("RetrieveServiceContent","Login","RetrievePropertiesEx","RetrievePropertiesEx","RetrievePropertiesEx","Logout")));
            }
            if(globalCallbacks.get()!=0)throw new AssertionError("Synthetic global handler callback count="+globalCallbacks.get());check(handlerFailure.get()==null);
            int before=httpRequests.get();
            rejects(DiscoveryException.Code.TLS,()->new IsolatedSoapTransport(endpoint,SSLContext.getDefault(),1000,1000).exchange("RetrieveServiceContent",SecureSoap.request("RetrieveServiceContent",""),null));
            rejects(DiscoveryException.Code.TLS,()->new IsolatedSoapTransport(URI.create("https://127.0.0.1:"+port+"/sdk"),trusted,1000,1000).exchange("RetrieveServiceContent",SecureSoap.request("RetrieveServiceContent",""),null));
            check(httpRequests.get()==before);
            // A permissive ExtendedTrustManager bypasses JDK endpoint checks; independent SAN must still reject before HTTP.
            rejects(DiscoveryException.Code.TLS,()->new IsolatedSoapTransport(URI.create("https://127.0.0.1:"+port+"/sdk"),permissive(),1000,1000).exchange("Login",SecureSoap.request("Login","<password>SENSITIVE</password>"),null));
            check(httpRequests.get()==before);
            SSLContext originalDefault=SSLContext.getDefault();
            try{
                SSLContext.setDefault(permissive());AdapterConfig sdk=DiagnosticRuntimeTest.config();DiagnosticRuntimeTest.set(sdk,"vcenter_endpoint",endpoint.toString());
                rejects(DiscoveryException.Code.TLS,()->RuntimeDiscoveryBinding.diagnostic(sdk.getAdapterInstResource()).diagnosticTransport().exchange("RetrieveServiceContent",SecureSoap.request("RetrieveServiceContent",""),null));
                try{DiagnosticOpsFixture.tlsProvider(DiagnosticOpsFixture.jvmContext(),port).capture(DiagnosticRuntimeTest.VC,Set.of("vm-71"));throw new AssertionError("JVM trust must not inherit mutable default context");}
                catch(OpsException ex){check(ex.code()==OpsException.Code.TLS);}
                check(httpRequests.get()==before);
            }finally{SSLContext.setDefault(originalDefault);}
            mode.set(1);IsolatedSoapTransport slow=new IsolatedSoapTransport(endpoint,trusted,1000,1000);
            long started=System.nanoTime();rejects(DiscoveryException.Code.LIMIT,()->slow.exchange("RetrieveServiceContent",SecureSoap.request("RetrieveServiceContent",""),null,120_000_000L));check(System.nanoTime()-started<2_000_000_000L);
            rejects(DiscoveryException.Code.CONNECTION,()->slow.exchange("RetrieveServiceContent",SecureSoap.request("RetrieveServiceContent",""),null));
            mode.set(2);VimDiscoveryClient.Reply fault=new IsolatedSoapTransport(endpoint,trusted,1000,1000).exchange("RetrieveServiceContent",SecureSoap.request("RetrieveServiceContent",""),null);
            check(fault!=null&&!fault.toString().contains("SENSITIVE"));
            mode.set(0);int unavailable;try(java.net.ServerSocket reserved=new java.net.ServerSocket(0,0,java.net.InetAddress.getLoopbackAddress())){unavailable=reserved.getLocalPort();}
            rejects(DiscoveryException.Code.CONNECTION,()->new IsolatedSoapTransport(URI.create("https://localhost:"+unavailable+"/sdk"),trusted,200,200).exchange("RetrieveServiceContent",SecureSoap.request("RetrieveServiceContent",""),null));
            Thread.currentThread().interrupt();try{rejects(DiscoveryException.Code.CANCELLED,()->new IsolatedSoapTransport(endpoint,trusted,1000,1000).exchange("Login",SecureSoap.request("Login",""),null));}finally{Thread.interrupted();}
            check(globalCallbacks.get()==0);
        }finally{
            CookieHandler.setDefault(cookies);ProxySelector.setDefault(proxy);Authenticator.setDefault(auth);server.stop(0);handlers.shutdownNow();
        }
        System.out.println("DiagnosticTlsTest: PASS ("+count+" localhost-only TLS/integration assertions)");
    }
}
