package com.vcfcf.adapters.rdmtopologypoc.discovery;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;

/** Fixed endpoint HTTPS only. No global TLS changes or collector-local files. */
public final class HttpsSoapTransport implements VimDiscoveryClient.Transport {
    private final URI endpoint; private final SSLContext context; private final int connectMillis, readMillis;
    public HttpsSoapTransport(URI endpoint, SSLContext context, int connectMillis, int readMillis) throws DiscoveryException {
        if(endpoint==null || context==null || !"https".equals(endpoint.getScheme()) || endpoint.getHost()==null
                || endpoint.getRawUserInfo()!=null || endpoint.getRawQuery()!=null || endpoint.getRawFragment()!=null
                || !"/sdk".equals(endpoint.getRawPath()) || connectMillis<1 || connectMillis>60000 || readMillis<1 || readMillis>120000)
            throw new DiscoveryException(DiscoveryException.Code.CONFIGURATION);
        this.endpoint=endpoint; this.context=context; this.connectMillis=connectMillis; this.readMillis=readMillis;
    }
    /** Trust material supplied in memory by an explicitly approved configuration boundary. */
    public static SSLContext trust(Collection<X509Certificate> certificates) throws DiscoveryException {
        if(certificates==null || certificates.isEmpty() || certificates.size()>64) throw new DiscoveryException(DiscoveryException.Code.CONFIGURATION);
        try {
            KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType()); store.load(null,null); int i=0;
            for(X509Certificate cert:certificates) { if(cert==null) throw new IllegalArgumentException(); store.setCertificateEntry("ca"+(i++),cert); }
            TrustManagerFactory managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); managers.init(store);
            SSLContext ssl=SSLContext.getInstance("TLS"); ssl.init(null,managers.getTrustManagers(),null); return ssl;
        } catch(Exception ex) { throw new DiscoveryException(DiscoveryException.Code.CONFIGURATION); }
    }
    @Override public VimDiscoveryClient.Reply exchange(String operation,byte[] request,String cookie) throws DiscoveryException {
        return exchange(operation,request,cookie,(connectMillis+(long)readMillis)*1_000_000L);
    }
    @Override public VimDiscoveryClient.Reply exchange(String operation,byte[] request,String cookie,long remainingNanos) throws DiscoveryException {
        if(!java.util.Set.of("RetrieveServiceContent","Login","Logout","CreateContainerView","DestroyView","RetrievePropertiesEx","ContinueRetrievePropertiesEx","CancelRetrievePropertiesEx").contains(operation)
                ||request==null||request.length==0||request.length>1048576) throw new DiscoveryException(DiscoveryException.Code.CONFIGURATION);
        if(Thread.currentThread().isInterrupted()) throw new DiscoveryException(DiscoveryException.Code.CANCELLED);
        if(remainingNanos<=0) throw new DiscoveryException(DiscoveryException.Code.LIMIT);
        HttpsURLConnection c=null;java.util.concurrent.ScheduledExecutorService watchdog=null;
        long absolute=System.nanoTime()+Math.min(remainingNanos,(connectMillis+(long)readMillis)*1_000_000L);
        try {
            c=(HttpsURLConnection)endpoint.toURL().openConnection(); c.setSSLSocketFactory(context.getSocketFactory());
            final HttpsURLConnection active=c;final Thread owner=Thread.currentThread();
            watchdog=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"rdm-soap-deadline");t.setDaemon(true);return t;});
            watchdog.scheduleAtFixedRate(()->{if(System.nanoTime()>absolute||owner.isInterrupted())active.disconnect();},1,10,java.util.concurrent.TimeUnit.MILLISECONDS);
            // Leave the JDK's standard hostname verification enabled AND independently check SANs after handshake.
            c.setHostnameVerifier((host,session)->false); // Any provider fallback mismatch must reject, never waive validation.
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(connectMillis); c.setReadTimeout(readMillis);
            c.setRequestMethod("POST"); c.setDoOutput(true); c.setUseCaches(false);
            c.setRequestProperty("Content-Type","text/xml; charset=utf-8"); c.setRequestProperty("SOAPAction","\"urn:vim25/6.5\"");
            if(cookie!=null) { if(!cookie.matches("vmware_soap_session=\"?[A-Za-z0-9._-]{1,512}\"?")) throw new DiscoveryException(DiscoveryException.Code.SESSION); c.setRequestProperty("Cookie",cookie); }
            c.setFixedLengthStreamingMode(request.length); c.connect();
            verifyName(endpoint.getHost(),(X509Certificate)c.getServerCertificates()[0]);
            try(OutputStream out=c.getOutputStream()) { out.write(request); }
            int status=c.getResponseCode();
            if(status==401 || status==403) throw new DiscoveryException(DiscoveryException.Code.AUTHENTICATION);
            if(status!=200 && status!=500) throw new DiscoveryException(DiscoveryException.Code.HTTP);
            long size=c.getContentLengthLong(); if(size>SecureSoap.MAX_BYTES) throw new DiscoveryException(DiscoveryException.Code.LIMIT);
            InputStream raw=status==500?c.getErrorStream():c.getInputStream();
            if(raw==null) throw new DiscoveryException(DiscoveryException.Code.HTTP);
            ByteArrayOutputStream body=new ByteArrayOutputStream();long responseDeadline=System.nanoTime()+readMillis*1_000_000L;
            try(InputStream in=raw) { byte[] chunk=new byte[8192]; int n; while((n=in.read(chunk))!=-1) { if(Thread.currentThread().isInterrupted()) throw new DiscoveryException(DiscoveryException.Code.CANCELLED);if(System.nanoTime()>absolute||System.nanoTime()>responseDeadline||body.size()+n>SecureSoap.MAX_BYTES) throw new DiscoveryException(DiscoveryException.Code.LIMIT); body.write(chunk,0,n); } }
            String session=null;
            java.util.List<String> cookies=new java.util.ArrayList<>();
            for(java.util.Map.Entry<String,List<String>> field:c.getHeaderFields().entrySet()) if("Set-Cookie".equalsIgnoreCase(field.getKey())) cookies.addAll(field.getValue());
            for(String header:cookies) if(header.startsWith("vmware_soap_session=")) {
                if(session!=null) throw new DiscoveryException(DiscoveryException.Code.SESSION); session=header.split(";",2)[0];
                if(!session.matches("vmware_soap_session=\"?[A-Za-z0-9._-]{1,512}\"?")) throw new DiscoveryException(DiscoveryException.Code.SESSION);
            }
            if(Thread.currentThread().isInterrupted()) throw new DiscoveryException(DiscoveryException.Code.CANCELLED);
            if(System.nanoTime()>absolute) throw new DiscoveryException(DiscoveryException.Code.LIMIT);
            return new VimDiscoveryClient.Reply(body.toByteArray(),session);
        } catch(DiscoveryException ex) { throw ex; }
        catch(SSLHandshakeException|SSLPeerUnverifiedException ex) { throw new DiscoveryException(DiscoveryException.Code.TLS); }
        catch(Exception ex) { if(Thread.currentThread().isInterrupted()) throw new DiscoveryException(DiscoveryException.Code.CANCELLED);if(System.nanoTime()>absolute) throw new DiscoveryException(DiscoveryException.Code.LIMIT);throw new DiscoveryException(DiscoveryException.Code.CONNECTION); }
        finally { if(watchdog!=null) watchdog.shutdownNow();if(c!=null) c.disconnect(); }
    }
    static void verifyName(String host,X509Certificate cert) throws DiscoveryException {
        try {
            boolean ip=host.matches("[0-9.]+") || host.indexOf(':')>=0; boolean matched=false;
            Collection<List<?>> sans=cert.getSubjectAlternativeNames();
            if(sans!=null) for(List<?> san:sans) {
                if(san.size()!=2 || !(san.get(0) instanceof Integer) || !(san.get(1) instanceof String)) continue;
                int type=(Integer)san.get(0); String name=(String)san.get(1);
                if(ip && type==7) matched |= java.net.InetAddress.getByName(host).equals(java.net.InetAddress.getByName(name));
                if(!ip && type==2) {
                    String h=host.toLowerCase(java.util.Locale.ROOT), n=name.toLowerCase(java.util.Locale.ROOT);
                    if(n.startsWith("*.") && n.indexOf('*',1)<0) { String tail=n.substring(1); matched |= h.endsWith(tail)&&h.substring(0,h.length()-tail.length()).indexOf('.')<0&&!h.substring(0,h.length()-tail.length()).isEmpty(); }
                    else matched |= h.equals(n);
                }
            }
            if(!matched) throw new DiscoveryException(DiscoveryException.Code.TLS);
        } catch(DiscoveryException ex) { throw ex; }
        catch(Exception ex) { throw new DiscoveryException(DiscoveryException.Code.TLS); }
    }
}
