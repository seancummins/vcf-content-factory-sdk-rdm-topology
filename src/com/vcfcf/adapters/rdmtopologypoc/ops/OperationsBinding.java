package com.vcfcf.adapters.rdmtopologypoc.ops;

import com.integrien.alive.common.adapter3.config.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;

/** Dormant in-memory binding; no descriptor migration, credential files or associated target credential. */
public final class OperationsBinding {
    final URI endpoint;
    final SSLContext tls;
    private final AdapterConfig source;
    private final AdapterCredentialConfig injected;
    private final String expectedKind;
    private OperationsBinding(AdapterConfig source, URI endpoint, SSLContext tls) {
        this.source = source; this.injected = source.getAdapterCredentials(); this.endpoint = endpoint; this.tls = tls;
        this.expectedKind = source.getAdapterKindKey();
    }
    public static SuiteApiVmProvider production() throws OpsException { throw new OpsException(OpsException.Code.RUNTIME_UNQUALIFIED); }
    /** Explicit developer boundary only. Configuration/trust delivery and injected RBAC require live qualification. */
    public static OperationsBinding dormant(AdapterConfig source, String endpoint, String trustMode,
            List<CertificateConfig> certificates, List<String> certificateIds) throws OpsException {
        return bind(source, endpoint, trustMode, certificates, certificateIds, "vcfcf_rdm_topology_poc");
    }
    /** Distinct unregistered diagnostic carrier, not a production activation or identity fallback. */
    public static OperationsBinding diagnostic(AdapterConfig source, String endpoint, String trustMode,
            List<CertificateConfig> certificates, List<String> certificateIds) throws OpsException {
        return bind(source, endpoint, trustMode, certificates, certificateIds, "vcfcf_rdm_discovery_diagnostic");
    }
    private static OperationsBinding bind(AdapterConfig source, String endpoint, String trustMode,
            List<CertificateConfig> certificates, List<String> certificateIds, String expectedKind) throws OpsException {
        try {
            if (source == null || !expectedKind.equals(source.getAdapterKindKey())) invalid();
            URI uri = endpoint(endpoint, false);
            return new OperationsBinding(source, uri, trust(trustMode, certificates, certificateIds));
        } catch (OpsException ex) { throw ex; }
        catch (Exception | LinkageError ex) { throw new OpsException(OpsException.Code.CONFIGURATION); }
    }
    static URI endpoint(String endpoint, boolean loopbackTest) throws OpsException {
        try {
            URI uri = new URI(endpoint); String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || !"/suite-api".equals(uri.getRawPath())
                    || uri.getPort() == 0 || uri.getPort() > 65535) invalid();
            if (!loopbackTest && (!host.matches("(?i)(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
                    || host.matches("[0-9.]+") || host.toLowerCase(Locale.ROOT).endsWith(".localhost") || host.equalsIgnoreCase("localhost"))) invalid();
            for (String label : host.split("\\.")) if (label.length() > 63) invalid();
            if (loopbackTest && !Set.of("localhost", "127.0.0.1").contains(host)) invalid();
            return uri;
        } catch (OpsException ex) { throw ex; }
        catch (Exception ex) { throw new OpsException(OpsException.Code.CONFIGURATION); }
    }
    static SSLContext trust(String mode, List<CertificateConfig> material, List<String> ids) throws OpsException {
        try {
            if (ids != null && !ids.isEmpty()) invalid();
            if ("jvm".equals(mode)) {
                if (material != null && !material.isEmpty()) invalid();
                TrustManagerFactory managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                managers.init((KeyStore)null);SSLContext context=SSLContext.getInstance("TLS");
                context.init(null,managers.getTrustManagers(),null);return context;
            }
            if (!"enterprise_ca".equals(mode) || material == null || material.isEmpty() || material.size() > 64) invalid();
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType()); store.load(null, null);
            Set<String> seen = new HashSet<>(); int i = 0;
            for (CertificateConfig c : material) {
                if (c == null || Boolean.FALSE.equals(c.getEnforceHostnameVerification())
                        || Boolean.FALSE.equals(c.getEnforceDateValidityChecking())) invalid();
                String pem = c.getCertificateString();
                if (pem == null || pem.length() > 32768 || !pem.matches("(?s)-----BEGIN CERTIFICATE-----\\s+[A-Za-z0-9+/=\\r\\n]+-----END CERTIFICATE-----\\s*")) invalid();
                byte[] der = Base64.getMimeDecoder().decode(pem.substring(pem.indexOf('\n') + 1, pem.indexOf("-----END CERTIFICATE-----")));
                ByteArrayInputStream in = new ByteArrayInputStream(der);
                X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
                if (in.available() != 0 || !Arrays.equals(cert.getEncoded(), der) || cert.getBasicConstraints() < 0) invalid();
                cert.checkValidity(); boolean[] usage = cert.getKeyUsage();
                if (usage != null && (usage.length <= 5 || !usage[5])) invalid();
                if (!seen.add(Base64.getEncoder().encodeToString(der))) invalid(); store.setCertificateEntry("ca" + i++, cert);
            }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); factory.init(store);
            SSLContext context = SSLContext.getInstance("TLS"); context.init(null, factory.getTrustManagers(), null); return context;
        } catch (OpsException ex) { throw ex; }
        catch (Exception | LinkageError ex) { throw new OpsException(OpsException.Code.CONFIGURATION); }
    }
    /** No network occurs at construction. Callers must separately authorize any eventual execution. */
    public SuiteApiVmProvider dormantProvider() throws OpsException {
        return new SuiteApiVmProvider(new OpsHttpsTransport(endpoint, tls, 10000, 30000), this::credentials);
    }
    Credentials credentials() throws OpsException { return credentials(AdapterCredentialConfig::getUserName, AdapterCredentialConfig::getPassword); }
    interface Reader { String read(AdapterCredentialConfig source); }
    Credentials credentials(Reader username, Reader password) throws OpsException {
        try {
            if (injected == null || source.getAdapterCredentials() != injected
                    || !expectedKind.equals(source.getAdapterKindKey())) unavailable();
            String user = username.read(injected), secret = password.read(injected);
            if (user == null || user.isEmpty() || user.length() > 1024 || !user.equals(user.trim())
                    || user.chars().anyMatch(Character::isISOControl) || secret == null || secret.isEmpty() || secret.length() > 4096) unavailable();
            OpsJson.quote(user); OpsJson.quote(secret); return new Credentials(user, secret.toCharArray());
        } catch (Exception | LinkageError ex) { throw new OpsException(OpsException.Code.CREDENTIAL_UNAVAILABLE); }
    }
    static final class Credentials implements AutoCloseable {
        private String username; private char[] password;
        // Package-private constructor transfers ownership of the synthetic/SDK-derived array to this lease.
        Credentials(String username, char[] password) { this.username = username; this.password = password; }
        byte[] request() throws OpsException {
            if (password == null) unavailable();
            return ("{\"username\":" + OpsJson.quote(username) + ",\"password\":" + OpsJson.quote(new String(password)) + "}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        @Override public void close() { if (password != null) Arrays.fill(password, '\0'); username = null; password = null; }
        @Override public String toString() { return "OperationsCredentials[redacted]"; }
    }
    private static void invalid() throws OpsException { throw new OpsException(OpsException.Code.CONFIGURATION); }
    private static void unavailable() throws OpsException { throw new OpsException(OpsException.Code.CREDENTIAL_UNAVAILABLE); }
    @Override public String toString() { return "OperationsBinding[dormant,redacted]"; }
}
