package com.vcfcf.adapters.rdmtopologypoc.discovery;

import java.io.*;
import java.net.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.*;

/** Diagnostic SOAP transport: direct TLS, no URLConnection/global proxy/authenticator/cookie handlers. */
public final class IsolatedSoapTransport implements VimDiscoveryClient.Transport {
    private final URI endpoint;private final SSLContext tls;private final int connectMillis,readMillis;
    private volatile boolean retired;
    public IsolatedSoapTransport(URI endpoint,SSLContext tls,int connectMillis,int readMillis) throws DiscoveryException {
        if(endpoint==null||tls==null||!"https".equals(endpoint.getScheme())||endpoint.getHost()==null
                ||endpoint.getRawUserInfo()!=null||endpoint.getRawQuery()!=null||endpoint.getRawFragment()!=null
                ||!"/sdk".equals(endpoint.getRawPath())||endpoint.getPort()==0||endpoint.getPort()>65535
                ||connectMillis<1||connectMillis>60000||readMillis<1||readMillis>120000) fail(DiscoveryException.Code.CONFIGURATION);
        this.endpoint=endpoint;this.tls=tls;this.connectMillis=connectMillis;this.readMillis=readMillis;
    }
    @Override public VimDiscoveryClient.Reply exchange(String op,byte[] request,String cookie) throws DiscoveryException {
        return exchange(op,request,cookie,(connectMillis+(long)readMillis)*1_000_000L);
    }
    @Override public synchronized VimDiscoveryClient.Reply exchange(String op,byte[] request,String cookie,long remaining) throws DiscoveryException {
        if(retired)fail(DiscoveryException.Code.CONNECTION);
        if(Thread.currentThread().isInterrupted())fail(DiscoveryException.Code.CANCELLED);
        if(remaining<=0)fail(DiscoveryException.Code.LIMIT);
        if(!Set.of("RetrieveServiceContent","Login","Logout","RetrievePropertiesEx","ContinueRetrievePropertiesEx","CancelRetrievePropertiesEx").contains(op)
                ||request==null||request.length==0||request.length>1048576)fail(DiscoveryException.Code.CONFIGURATION);
        if(cookie!=null&&!session(cookie))fail(DiscoveryException.Code.SESSION);
        ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"rdm-isolated-soap");t.setDaemon(true);return t;});
        AtomicReference<Socket> active=new AtomicReference<>();
        Future<VimDiscoveryClient.Reply> result=worker.submit(()->perform(request,cookie,active));
        try{return result.get(Math.min(remaining,(connectMillis+(long)readMillis)*1_000_000L),TimeUnit.NANOSECONDS);}
        catch(TimeoutException ex){retired=true;throw new DiscoveryException(DiscoveryException.Code.LIMIT);}
        catch(InterruptedException ex){retired=true;Thread.currentThread().interrupt();throw new DiscoveryException(DiscoveryException.Code.CANCELLED);}
        catch(ExecutionException ex){if(ex.getCause() instanceof DiscoveryException)throw (DiscoveryException)ex.getCause();throw new DiscoveryException(DiscoveryException.Code.CONNECTION);}
        finally{result.cancel(true);worker.shutdownNow();close(active.get());}
    }
    private VimDiscoveryClient.Reply perform(byte[] request,String cookie,AtomicReference<Socket> active) throws DiscoveryException {
        SSLSocket socket=null;Socket direct=null;
        try{
            direct=new Socket(Proxy.NO_PROXY);active.set(direct);
            if(Thread.currentThread().isInterrupted())fail(DiscoveryException.Code.CANCELLED);
            int port=endpoint.getPort()==-1?443:endpoint.getPort();direct.connect(new InetSocketAddress(endpoint.getHost(),port),connectMillis);
            if(Thread.currentThread().isInterrupted())fail(DiscoveryException.Code.CANCELLED);
            socket=(SSLSocket)tls.getSocketFactory().createSocket(direct,endpoint.getHost(),port,true);active.set(socket);socket.setSoTimeout(readMillis);
            SSLParameters parameters=socket.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");
            if(endpoint.getHost().indexOf(':')<0&&!endpoint.getHost().matches("[0-9.]+"))parameters.setServerNames(List.of(new SNIHostName(endpoint.getHost())));
            socket.setSSLParameters(parameters);socket.startHandshake();
            HttpsSoapTransport.verifyName(endpoint.getHost(),(X509Certificate)socket.getSession().getPeerCertificates()[0]);
            if(Thread.currentThread().isInterrupted())fail(DiscoveryException.Code.CANCELLED);
            String headers="POST /sdk HTTP/1.1\r\nHost: "+endpoint.getRawAuthority()+"\r\nContent-Type: text/xml; charset=utf-8\r\nSOAPAction: \"urn:vim25/6.5\"\r\nConnection: close\r\nContent-Length: "+request.length+"\r\n"
                    +(cookie==null?"":"Cookie: "+cookie+"\r\n")+"\r\n";
            OutputStream out=socket.getOutputStream();out.write(headers.getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.write(request);out.flush();
            return decode(new BufferedInputStream(socket.getInputStream()));
        }catch(DiscoveryException ex){throw ex;}
        catch(SSLException ex){throw new DiscoveryException(DiscoveryException.Code.TLS);}
        catch(SocketTimeoutException ex){throw new DiscoveryException(DiscoveryException.Code.LIMIT);}
        catch(Exception|LinkageError ex){throw new DiscoveryException(DiscoveryException.Code.CONNECTION);}
        finally{close(socket);close(direct);}
    }
    /** Package-private deterministic wire parser; error responses retain bounded SOAP fault bytes only. */
    static VimDiscoveryClient.Reply decode(InputStream in) throws IOException,DiscoveryException {
        Budget budget=new Budget();String status=line(in,budget);
        if(!status.matches("HTTP/1\\.[01] [1-5][0-9]{2}(?: [\\x20-\\x7e]*)?"))fail(DiscoveryException.Code.HTTP);
        int code=Integer.parseInt(status.substring(9,12));Map<String,String> fields=new HashMap<>();String header,session=null;
        while(!(header=line(in,budget)).isEmpty()){
            int colon=header.indexOf(':');if(colon<1||!header.substring(0,colon).matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+"))fail(DiscoveryException.Code.HTTP);
            String name=header.substring(0,colon).toLowerCase(Locale.ROOT),value=header.substring(colon+1).trim();
            if(name.equals("set-cookie")){
                if(value.startsWith("vmware_soap_session=")){
                    if(session!=null)fail(DiscoveryException.Code.SESSION);session=value.split(";",2)[0];
                    if(!session(session))fail(DiscoveryException.Code.SESSION);
                }
            }else if(fields.putIfAbsent(name,value)!=null)fail(DiscoveryException.Code.HTTP);
        }
        if(code==401||code==403)fail(DiscoveryException.Code.AUTHENTICATION);
        if(code!=200&&code!=500)fail(DiscoveryException.Code.HTTP);
        if(fields.containsKey("content-encoding")&&!fields.get("content-encoding").equalsIgnoreCase("identity"))fail(DiscoveryException.Code.HTTP);
        String type=fields.get("content-type");
        if(type==null||!type.toLowerCase(Locale.ROOT).matches("(?:text/xml|application/soap\\+xml)(?:\\s*;\\s*charset=(?:utf-8|\"utf-8\"))?"))fail(DiscoveryException.Code.HTTP);
        String transfer=fields.get("transfer-encoding"),length=fields.get("content-length");
        if(transfer!=null&&length!=null)fail(DiscoveryException.Code.HTTP);
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        if(transfer!=null){
            if(!transfer.equalsIgnoreCase("chunked"))fail(DiscoveryException.Code.HTTP);
            while(true){String chunk=line(in,budget);if(!chunk.matches("[0-9a-fA-F]{1,8}"))fail(DiscoveryException.Code.HTTP);long n=Long.parseLong(chunk,16);
                if(n==0){if(!line(in,budget).isEmpty())fail(DiscoveryException.Code.HTTP);break;}
                body(in,body,n);if(in.read()!='\r'||in.read()!='\n')fail(DiscoveryException.Code.HTTP);
            }
        }else if(length!=null){if(!length.matches("0|[1-9][0-9]{0,9}"))fail(DiscoveryException.Code.HTTP);body(in,body,Long.parseLong(length));}
        else{byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(body.size()+n>SecureSoap.MAX_BYTES)fail(DiscoveryException.Code.LIMIT);body.write(bytes,0,n);}}
        return new VimDiscoveryClient.Reply(body.toByteArray(),session);
    }
    private static void body(InputStream in,ByteArrayOutputStream out,long n)throws IOException,DiscoveryException{
        if(n>SecureSoap.MAX_BYTES-out.size())fail(DiscoveryException.Code.LIMIT);byte[] bytes=new byte[8192];
        while(n>0){int count=in.read(bytes,0,(int)Math.min(n,bytes.length));if(count<0)fail(DiscoveryException.Code.HTTP);out.write(bytes,0,count);n-=count;}
    }
    private static final class Budget{int bytes,lines;}
    private static String line(InputStream in,Budget b)throws IOException,DiscoveryException{
        if(++b.lines>512)fail(DiscoveryException.Code.LIMIT);StringBuilder value=new StringBuilder();
        while(true){int c=in.read();if(++b.bytes>65536||value.length()>8192)fail(DiscoveryException.Code.LIMIT);
            if(c=='\r'){if(in.read()!='\n')fail(DiscoveryException.Code.HTTP);if(++b.bytes>65536)fail(DiscoveryException.Code.LIMIT);return value.toString();}
            if(c<32||c>126)fail(DiscoveryException.Code.HTTP);value.append((char)c);}
    }
    private static void close(Socket socket){if(socket!=null)try{socket.close();}catch(IOException ignored){}}
    private static boolean session(String value){return value.matches("vmware_soap_session=(?:[A-Za-z0-9._-]{1,512}|\"[A-Za-z0-9._-]{1,512}\")");}
    private static void fail(DiscoveryException.Code code)throws DiscoveryException{throw new DiscoveryException(code);}
    @Override public String toString(){return "IsolatedSoapTransport[diagnostic,redacted]";}
}
