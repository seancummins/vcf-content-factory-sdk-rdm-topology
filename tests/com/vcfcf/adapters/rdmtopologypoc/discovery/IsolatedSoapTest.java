package com.vcfcf.adapters.rdmtopologypoc.discovery;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Deterministic strict framing/fault/cookie bounds for the diagnostic-only SOAP transport. */
public final class IsolatedSoapTest{
    static int count;static void check(boolean value){if(!value)throw new AssertionError("synthetic wire assertion");count++;}
    interface Action{void run()throws Exception;}
    static VimDiscoveryClient.Reply decode(String wire)throws Exception{return IsolatedSoapTransport.decode(new ByteArrayInputStream(wire.getBytes(StandardCharsets.US_ASCII)));}
    static void reject(String wire,DiscoveryException.Code code)throws Exception{
        try{decode(wire);throw new AssertionError("expected rejection");}catch(DiscoveryException ex){check(ex.code()==code&&ex.getCause()==null&&!ex.toString().contains("SENSITIVE"));}
    }
    public static void main(String[]args)throws Exception{
        String head="HTTP/1.1 200 OK\r\nContent-Type: text/xml; charset=utf-8\r\n";
        byte[] body=DiagnosticSoapFixture.reply("Login","<returnval/>");String text=new String(body,StandardCharsets.US_ASCII);
        check(decode(head+"Content-Length: "+body.length+"\r\nSet-Cookie: vmware_soap_session=synthetic; Secure; HttpOnly\r\n\r\n"+text)!=null);
        check(decode(head+"Transfer-Encoding: chunked\r\n\r\n"+Integer.toHexString(body.length)+"\r\n"+text+"\r\n0\r\n\r\n")!=null);
        check(decode(head+"\r\n"+text)!=null);
        String fault="<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault><faultstring>SENSITIVE</faultstring></s:Fault></s:Body></s:Envelope>";
        check(decode("HTTP/1.1 500 Fault\r\nContent-Type: text/xml\r\nContent-Length: "+fault.length()+"\r\n\r\n"+fault)!=null);
        reject(head+"Content-Length: 1\r\nTransfer-Encoding: chunked\r\n\r\n",DiscoveryException.Code.HTTP);
        reject(head+"Content-Length: 1\r\nContent-Length: 1\r\n\r\nx",DiscoveryException.Code.HTTP);
        reject(head+"Content-Length: 9999999999\r\n\r\n",DiscoveryException.Code.LIMIT);
        reject(head+"Content-Length: 5\r\n\r\nx",DiscoveryException.Code.HTTP);
        reject(head+"Set-Cookie: vmware_soap_session=one\r\nSet-Cookie: vmware_soap_session=two\r\n\r\n",DiscoveryException.Code.SESSION);
        reject(head+"Set-Cookie: vmware_soap_session=bad token\r\n\r\n",DiscoveryException.Code.SESSION);
        reject(head+"Set-Cookie: vmware_soap_session=\"unbalanced\r\n\r\n",DiscoveryException.Code.SESSION);
        reject(head+"Set-Cookie: vmware_soap_session=unbalanced\"\r\n\r\n",DiscoveryException.Code.SESSION);
        reject(head+"Content-Encoding: gzip\r\n\r\n",DiscoveryException.Code.HTTP);
        reject("HTTP/1.1 302 Found\r\n\r\nSENSITIVE",DiscoveryException.Code.HTTP);
        reject("HTTP/1.1 401 Unauthorized\r\n\r\nSENSITIVE",DiscoveryException.Code.AUTHENTICATION);
        reject("HTTP/1.1 403 Forbidden\r\n\r\nSENSITIVE",DiscoveryException.Code.AUTHENTICATION);
        reject("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n",DiscoveryException.Code.HTTP);
        reject(head+"Transfer-Encoding: chunked\r\n\r\n1;ext=yes\r\nx\r\n0\r\n\r\n",DiscoveryException.Code.HTTP);
        reject(head+"Transfer-Encoding: chunked\r\n\r\n0\r\nX-Trailer: nope\r\n\r\n",DiscoveryException.Code.HTTP);
        reject(head+"X-Long: "+"x".repeat(8193)+"\r\n\r\n",DiscoveryException.Code.LIMIT);
        StringBuilder bounded=new StringBuilder(head);for(int i=0;i<8;i++)bounded.append("X-").append(i).append(": ").append("x".repeat(8173)).append("\r\n");
        int pad=65535-bounded.length()-2;bounded.append("X-Pad: ").append("x".repeat(pad-7)).append("\r\n");check(bounded.length()==65535);
        reject(bounded+"\r\n",DiscoveryException.Code.LIMIT);
        System.out.println("IsolatedSoapTest: PASS ("+count+" deterministic assertions)");
    }
}
