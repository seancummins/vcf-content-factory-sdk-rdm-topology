package com.vcfcf.adapters.rdmtopologypoc.diagnostic;

import com.integrien.alive.common.adapter3.describe.AdapterDescribe;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Offline readiness check using the real SDK loader, never a parser substitute. */
public final class DescriptorLoadingProbe {
    private DescriptorLoadingProbe() { }
    public static void main(String[] args) {
        PrintStream output = System.out;
        PrintStream errors = System.err;
        int status = 1;
        String report = "DESCRIPTOR_LOAD code=INVALID_INPUT";
        try {
            // This probe owns its standalone JVM, never a collector JVM. Apply
            // external-access restrictions to the SDK's second XML parser too.
            for (String property : new String[] { "javax.xml.accessExternalDTD",
                    "javax.xml.accessExternalSchema", "javax.xml.accessExternalStylesheet" }) {
                System.setProperty(property, "");
                if (!"".equals(System.getProperty(property))) throw new SecurityException();
            }
            if (args.length != 2 || !args[1].matches("[A-Za-z][A-Za-z0-9_]{0,127}"))
                throw new IllegalArgumentException();
            Path path = Path.of(args[0]);
            if (!Files.isRegularFile(path) || Files.size(path) > 1048576)
                throw new IllegalArgumentException();
            byte[] xml;
            try (java.io.InputStream input = Files.newInputStream(path)) {
                xml = input.readNBytes(1048577);
            }
            if (xml.length == 0 || xml.length > 1048576) throw new IllegalArgumentException();
            // Reject unsafe XML before handing the same bytes to the actual SDK.
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            try (PrintStream sink = new PrintStream(OutputStream.nullOutputStream())) {
                // SDK/parser diagnostics may include descriptor values; never forward them.
                System.setOut(sink);
                System.setErr(sink);
                javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
                builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                    @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
                    @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
                });
                builder.parse(new ByteArrayInputStream(xml));
                AdapterDescribe descriptor = AdapterDescribe.make(new ByteArrayInputStream(xml));
                if (descriptor == null || !args[1].equals(descriptor.getKey())) {
                    report = "DESCRIPTOR_LOAD code=INVALID_DESCRIPTOR";
                } else {
                    report = "DESCRIPTOR_LOAD code=PASS resource_kinds=" + descriptor.getResourceKinds().size()
                            + " credential_kinds=" + descriptor.getCredentialKinds().size();
                    status = 0;
                }
            }
        } catch (LinkageError failure) {
            status = 2;
            String missing = "unresolved";
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
                if (cause instanceof NoClassDefFoundError || cause instanceof ClassNotFoundException) {
                    String name = cause.getMessage();
                    if (name != null && name.matches("[A-Za-z_$][A-Za-z0-9_$/]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
                        missing = name.replace('/', '.');
                }
            }
            report = "DESCRIPTOR_LOAD code=BLOCKED_PLATFORM_DEPENDENCY missing_class=" + missing;
        } catch (SecurityException failure) {
            status = 2;
            report = "DESCRIPTOR_LOAD code=BLOCKED_SECURITY_CONFIGURATION";
        } catch (Exception failure) {
            report = "DESCRIPTOR_LOAD code=INVALID_INPUT_OR_DESCRIPTOR";
        } finally {
            System.setOut(output);
            System.setErr(errors);
        }
        output.println(report);
        System.exit(status);
    }
}
