package com.vcfcf.adapters.rdmtopologypoc.discovery;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/** Two-pass parser: bounded streaming validation before a hardened, bounded DOM. */
public final class SecureSoap {
    public static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final String VIM = "urn:vim25";
    public static final String XSI = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private SecureSoap() { }
    public static Element response(byte[] bytes, String operation) throws DiscoveryException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) fail(DiscoveryException.Code.LIMIT);
        try {
            XMLInputFactory stream = XMLInputFactory.newFactory();
            stream.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            stream.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            stream.setXMLResolver((a,b,c,d) -> { throw new javax.xml.stream.XMLStreamException(); });
            XMLStreamReader reader = stream.createXMLStreamReader(new ByteArrayInputStream(bytes));
            int depth = 0, nodes = 0, text = 0;
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) fail(DiscoveryException.Code.XML);
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        if (++depth > 32 || ++nodes > 100000 || reader.getAttributeCount() > 16 || reader.getNamespaceCount()>16) fail(DiscoveryException.Code.LIMIT);
                        for (int i=0; i<reader.getAttributeCount(); i++) if (reader.getAttributeValue(i).length()>8192) fail(DiscoveryException.Code.LIMIT);
                    } else if (event == XMLStreamConstants.END_ELEMENT) depth--;
                    else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                        text += reader.getTextLength();
                        if (reader.getTextLength()>1048576 || text>MAX_BYTES) fail(DiscoveryException.Code.LIMIT);
                    }
                }
            } finally { reader.close(); }
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true); f.setXIncludeAware(false); f.setExpandEntityReferences(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            javax.xml.parsers.DocumentBuilder builder = f.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            builder.setEntityResolver((a,b) -> { throw new org.xml.sax.SAXException(); });
            Document doc = builder.parse(new ByteArrayInputStream(bytes));
            Element envelope = doc.getDocumentElement();
            if (!is(envelope, SOAP, "Envelope")) fail(DiscoveryException.Code.STRUCTURE);
            List<Element> outer = elements(envelope); Element body = null; boolean header=false;
            for (Element e: outer) {
                if (is(e,SOAP,"Body") && body==null) body=e;
                else if (!is(e,SOAP,"Header") || header || body!=null || elements(e).size()!=0) fail(DiscoveryException.Code.STRUCTURE);
                else header=true;
            }
            if (body==null || elements(body).size()!=1) fail(DiscoveryException.Code.STRUCTURE);
            Element result = elements(body).get(0);
            if (is(result,SOAP,"Fault")) {
                // Fault detail is never propagated into diagnostics.
                if (operation.equals("Login")) fail(DiscoveryException.Code.AUTHENTICATION);
                fail(DiscoveryException.Code.SOAP_FAULT);
            }
            if (!is(result,VIM,operation+"Response")) fail(DiscoveryException.Code.STRUCTURE);
            validateNamespaces(result);
            return result;
        } catch (DiscoveryException ex) { throw ex; }
        catch (Exception ex) { throw new DiscoveryException(DiscoveryException.Code.XML); }
    }
    public static List<Element> elements(Element e) throws DiscoveryException {
        List<Element> out = new ArrayList<>();
        for (Node n=e.getFirstChild(); n!=null; n=n.getNextSibling()) {
            if (n instanceof Element) out.add((Element)n);
            else if ((n.getNodeType()==Node.TEXT_NODE || n.getNodeType()==Node.CDATA_SECTION_NODE) && !n.getTextContent().trim().isEmpty()) fail(DiscoveryException.Code.STRUCTURE);
        }
        return out;
    }
    public static List<Element> children(Element e, String name) throws DiscoveryException {
        List<Element> out = new ArrayList<>();
        for (Element child: elements(e)) if (is(child,VIM,name)) out.add(child);
        return out;
    }
    public static Element one(Element e, String name) throws DiscoveryException {
        List<Element> list=children(e,name); if (list.size()!=1) fail(DiscoveryException.Code.INCOMPLETE); return list.get(0);
    }
    public static String text(Element e) throws DiscoveryException {
        for (Node n=e.getFirstChild();n!=null;n=n.getNextSibling()) if (n instanceof Element) fail(DiscoveryException.Code.STRUCTURE);
        String s=e.getTextContent(); if(s.length()>8192 || e.hasAttributeNS(XSI,"nil")) fail(DiscoveryException.Code.STRUCTURE);
        if(e.hasAttributeNS(XSI,"type")) {String q=e.getAttributeNS(XSI,"type");int colon=q.indexOf(':');String prefix=colon<0?null:q.substring(0,colon),local=colon<0?q:q.substring(colon+1);
            if(!XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(e.lookupNamespaceURI(prefix))||!java.util.Set.of("string","byte","short","int","long","boolean","dateTime").contains(local)) fail(DiscoveryException.Code.STRUCTURE);}
        return s;
    }
    public static String required(Element e,String name) throws DiscoveryException { return nonblank(text(one(e,name))); }
    public static String nonblank(String value) throws DiscoveryException {
        if(value==null || value.trim().isEmpty() || value.length()>8192) fail(DiscoveryException.Code.STRUCTURE);
        for(int i=0;i<value.length();i++) if(Character.isISOControl(value.charAt(i))) fail(DiscoveryException.Code.STRUCTURE);
        return value;
    }
    public static String type(Element e) throws DiscoveryException {
        String q=e.getAttributeNS(XSI,"type"); int split=q.indexOf(':');
        String prefix=split<0?null:q.substring(0,split); String local=split<0?q:q.substring(split+1);
        if(local.isEmpty() || !VIM.equals(e.lookupNamespaceURI(prefix))) fail(DiscoveryException.Code.STRUCTURE);
        return local;
    }
    public static String mor(Element e,String kind) throws DiscoveryException {
        if(!kind.equals(e.getAttribute("type"))) fail(DiscoveryException.Code.STRUCTURE);
        if(e.hasAttributeNS(XSI,"type")&&!type(e).equals("ManagedObjectReference")) fail(DiscoveryException.Code.STRUCTURE);
        for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element) fail(DiscoveryException.Code.STRUCTURE);
        if(e.hasAttributeNS(XSI,"nil")) fail(DiscoveryException.Code.STRUCTURE);
        return nonblank(e.getTextContent());
    }
    public static boolean is(Element e,String ns,String local) { return ns.equals(e.getNamespaceURI())&&local.equals(e.getLocalName()); }
    private static void validateNamespaces(Element e) throws DiscoveryException {
        if(!VIM.equals(e.getNamespaceURI())) fail(DiscoveryException.Code.STRUCTURE);
        for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element) validateNamespaces((Element)n);
    }
    public static String escape(String value) { return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;"); }
    public static byte[] request(String operation,String content) {
        return ("<s:Envelope xmlns:s=\""+SOAP+"\" xmlns=\""+VIM+"\"><s:Body><"+operation+">"+content+"</"+operation+"></s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8);
    }
    static void fail(DiscoveryException.Code code) throws DiscoveryException { throw new DiscoveryException(code); }
}
