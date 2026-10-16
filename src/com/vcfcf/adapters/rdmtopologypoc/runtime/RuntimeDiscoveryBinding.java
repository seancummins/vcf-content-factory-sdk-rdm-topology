package com.vcfcf.adapters.rdmtopologypoc.runtime;

import com.integrien.alive.common.adapter3.config.*;
import com.vcfcf.adapters.rdmtopologypoc.discovery.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.net.ssl.SSLContext;

/** Dormant SDK boundary. Neither reads ambient credentials nor publishes topology. */
public final class RuntimeDiscoveryBinding {
    public static final String CREDENTIAL_KIND = "rdm_vcenter_readonly";
    public final URI endpoint;
    private final SSLContext trust;
    private final CredentialFieldConfig username, password;
    private final ResourceConfig source;
    private final CredentialConfig sourceCredential;
    private final List<Object> configurationStamp;
    private final String expectedKind;

    private RuntimeDiscoveryBinding(URI endpoint, SSLContext trust,
            CredentialFieldConfig username, CredentialFieldConfig password, ResourceConfig source, String expectedKind) {
        this.endpoint = endpoint; this.trust = trust;
        this.username = username; this.password = password;
        this.source = source; this.sourceCredential = source.getResourceCredential();
        this.configurationStamp = stamp(source);
        this.expectedKind = expectedKind;
    }

    public static RuntimeDiscoveryBinding from(ResourceConfig config) throws DiscoveryException {
        return bind(config, "vcfcf_rdm_topology_poc", Set.of("vcenter_endpoint", "trust_mode", "discovery_scope"));
    }

    /** Unregistered E4.6 diagnostic only; never re-labels or copies an SDK credential. */
    public static RuntimeDiscoveryBinding diagnostic(ResourceConfig config) throws DiscoveryException {
        return bind(config, "vcfcf_rdm_discovery_diagnostic", Set.of("vcenter_endpoint", "trust_mode", "discovery_scope",
                "ops_endpoint", "ops_trust_mode", "target_vcenter_uuid", "target_vm_moid", "target_disk_key",
                "diagnostic_enabled", "authorize_once"));
    }
    /** Approved same-kind dispatcher boundary; retains the original SDK carrier. */
    public static RuntimeDiscoveryBinding sameKindDiagnostic(ResourceConfig config) throws DiscoveryException {
        return bind(config, "vcfcf_rdm_topology_poc", Set.of("vcenter_endpoint", "trust_mode", "discovery_scope",
                "ops_endpoint", "ops_trust_mode", "target_vcenter_uuid", "target_vm_moid", "target_disk_key",
                "diagnostic_enabled", "authorize_once", "execution_mode", "fixture_json"));
    }
    private static RuntimeDiscoveryBinding bind(ResourceConfig config, String expectedKind, Set<String> allowed) throws DiscoveryException {
        try {
            if (config == null || !expectedKind.equals(config.getAdapterKind())
                    || !expectedKind.equals(config.getResourceKind())) fail();
            Map<String,String> values = new HashMap<>();
            Set<String> seen = new HashSet<>();
            if (config.getResourceIdentifiers() == null) fail();
            for (ResourceIdentifierConfig id : config.getResourceIdentifiers()) {
                if(id==null||!seen.add(id.getKey()))fail();
                if (allowed.contains("execution_mode") && id != null && ("execution_mode".equals(id.getKey()) || "fixture_json".equals(id.getKey()))) {
                    if (id.isPartOfUniqueness() || ("execution_mode".equals(id.getKey()) ? !"read_only_diagnostic".equals(id.getValue()) : id.getValue()!=null&&!id.getValue().isEmpty())) fail();
                    continue;
                }
                if (id == null || !allowed.contains(id.getKey()) || id.isPartOfUniqueness()
                        || values.putIfAbsent(id.getKey(), id.getValue()) != null || id.getValue() == null) fail();
            }
            Set<String> required=new HashSet<>(allowed);required.remove("execution_mode");required.remove("fixture_json");
            if(allowed.contains("execution_mode")&&!seen.contains("execution_mode"))fail();
            if (!values.keySet().equals(required) || !((expectedKind.equals("vcfcf_rdm_discovery_diagnostic")||allowed.contains("execution_mode"))
                    ? "single_vm_current_host" : "caller_visible_inventory").equals(values.get("discovery_scope"))) fail();
            URI endpoint = new URI(values.get("vcenter_endpoint"));
            if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null
                    || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null
                    || endpoint.getRawFragment() != null || !"/sdk".equals(endpoint.getRawPath())
                    || endpoint.getPort() == 0 || endpoint.getPort() > 65535) fail();
            SSLContext trust = certificates(config, values.get("trust_mode"));
            CredentialConfig credential = config.getResourceCredential();
            if (credential == null || !CREDENTIAL_KIND.equals(credential.getCredentialKey())
                    || credential.getCredenialFields() == null || credential.getCredenialFields().size() != 2) fail();
            CredentialFieldConfig username = null, password = null;
            for (CredentialFieldConfig field : credential.getCredenialFields()) {
                if (field == null) fail();
                if ("username".equals(field.getKey()) && !field.isPassword() && username == null) username = field;
                else if ("password".equals(field.getKey()) && field.isPassword() && password == null) password = field;
                else fail();
            }
            if (username == null || password == null) fail();
            return new RuntimeDiscoveryBinding(endpoint, trust, username, password, config, expectedKind);
        } catch (DiscoveryException ex) { throw ex; }
        catch (Exception | LinkageError ex) { throw new DiscoveryException(DiscoveryException.Code.CONFIGURATION); }
    }

    private static SSLContext certificates(ResourceConfig config, String mode) throws Exception {
        List<CertificateConfig> material = config.getTrustedCertificates();
        List<String> ids = config.getTrustedCertificateIds();
        // IDs alone do not contain trust material. No platform store/file lookup is guessed.
        if (ids != null && !ids.isEmpty()) fail();
        if ("jvm".equals(mode)) {
            if (material != null && !material.isEmpty()) fail();
            javax.net.ssl.TrustManagerFactory managers=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            managers.init((java.security.KeyStore)null);SSLContext context=SSLContext.getInstance("TLS");
            context.init(null,managers.getTrustManagers(),null);return context;
        }
        if (!"enterprise_ca".equals(mode) || material == null || material.isEmpty() || material.size() > 64) fail();
        List<X509Certificate> certificates = new ArrayList<>();
        Set<String> encoded = new HashSet<>();
        for (CertificateConfig c : material) {
            if (c == null || Boolean.FALSE.equals(c.getEnforceHostnameVerification())
                    || Boolean.FALSE.equals(c.getEnforceDateValidityChecking())) fail();
            String pem = c.getCertificateString();
            if (pem == null || pem.length() > 32768 || !pem.matches("(?s)-----BEGIN CERTIFICATE-----\\s+[A-Za-z0-9+/=\\r\\n]+-----END CERTIFICATE-----\\s*")) fail();
            byte[] der = Base64.getMimeDecoder().decode(pem.substring(pem.indexOf('\n') + 1, pem.indexOf("-----END CERTIFICATE-----")));
            ByteArrayInputStream in = new ByteArrayInputStream(der);
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
            if (in.available() != 0 || !Arrays.equals(cert.getEncoded(), der) || cert.getBasicConstraints() < 0) fail();
            cert.checkValidity();
            boolean[] usage = cert.getKeyUsage();
            if (usage != null && (usage.length <= 5 || !usage[5])) fail();
            if (!encoded.add(Base64.getEncoder().encodeToString(der))) fail();
            certificates.add(cert);
        }
        return HttpsSoapTransport.trust(certificates);
    }

    /** Creates an inert transport; constructing it does not connect. Fixed E4.2 budgets apply. */
    public HttpsSoapTransport transport() throws DiscoveryException {
        try {
            if (!configurationStamp.equals(stamp(source))) fail();
            return new HttpsSoapTransport(endpoint, trust, 10000, 30000);
        } catch (Exception | LinkageError ex) { throw new DiscoveryException(DiscoveryException.Code.CONFIGURATION); }
    }
    /** E4.6 path is isolated from JVM-global proxy/authentication/cookie handlers. */
    public IsolatedSoapTransport diagnosticTransport() throws DiscoveryException {
        try {
            boolean sameKind="vcfcf_rdm_topology_poc".equals(expectedKind)&&source.getResourceIdentifiers().stream()
                    .anyMatch(id->"execution_mode".equals(id.getKey())&&"read_only_diagnostic".equals(id.getValue())&&!id.isPartOfUniqueness());
            if((!"vcfcf_rdm_discovery_diagnostic".equals(expectedKind)&&!sameKind)||!configurationStamp.equals(stamp(source))) fail();
            return new IsolatedSoapTransport(endpoint,trust,10000,30000);
        }catch(Exception|LinkageError ex){throw new DiscoveryException(DiscoveryException.Code.CONFIGURATION);}
    }

    private static List<Object> stamp(ResourceConfig source) {
        List<Object> values = new ArrayList<>();
        values.add(source.getAdapterKind()); values.add(source.getResourceKind());
        for (ResourceIdentifierConfig id : source.getResourceIdentifiers()) {
            values.add(id.getKey()); values.add(id.getValue()); values.add(id.isPartOfUniqueness());
        }
        values.add(source.getTrustedCertificateIds() == null ? null : new ArrayList<>(source.getTrustedCertificateIds()));
        if (source.getTrustedCertificates() != null) for (CertificateConfig cert : source.getTrustedCertificates()) {
            values.add(cert.getCertificateString()); values.add(cert.getThumbprint());
            values.add(cert.getEnforceHostnameVerification()); values.add(cert.getEnforceDateValidityChecking());
        }
        return Collections.unmodifiableList(values);
    }

    /** Platform accessor decrypts password through Crypt. Missing platform linkage fails closed. */
    public Credentials credentials() throws DiscoveryException { return credentials(CredentialFieldConfig::getValue); }

    // Package-private injection is test-only; never a plaintext production fallback.
    Credentials credentials(ValueReader reader) throws DiscoveryException {
        try {
            // SDK carriers are mutable; reject metadata/association changes after binding.
            if (!configurationStamp.equals(stamp(source)) || !expectedKind.equals(source.getAdapterKind())
                    || !expectedKind.equals(source.getResourceKind())
                    || source.getResourceCredential() != sourceCredential
                    || !CREDENTIAL_KIND.equals(sourceCredential.getCredentialKey())
                    || sourceCredential.getCredenialFields().size() != 2
                    || !sourceCredential.getCredenialFields().contains(username)
                    || !sourceCredential.getCredenialFields().contains(password)
                    || !"username".equals(username.getKey()) || username.isPassword()
                    || !"password".equals(password.getKey()) || !password.isPassword()) fail();
            String user = reader.read(username), secret = reader.read(password);
            if (user == null || user.isEmpty() || user.length() > 1024 || !user.equals(user.trim())
                    || user.chars().anyMatch(Character::isISOControl) || !xmlText(user)
                    || secret == null || secret.isEmpty() || secret.length() > 4096 || !xmlText(secret)) fail();
            return new Credentials(user, secret.toCharArray());
        } catch (Exception | LinkageError ex) { throw new DiscoveryException(DiscoveryException.Code.AUTHENTICATION); }
    }
    interface ValueReader { String read(CredentialFieldConfig field); }

    private static boolean xmlText(String value) {
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i); i += Character.charCount(cp);
            if (!(cp == 9 || cp == 10 || cp == 13 || cp >= 0x20 && cp <= 0xd7ff
                    || cp >= 0xe000 && cp <= 0xfffd || cp >= 0x10000 && cp <= 0x10ffff)) return false;
        }
        return true;
    }

    public static final class Credentials implements AutoCloseable {
        private String username; private char[] password;
        private Credentials(String username, char[] password) { this.username = username; this.password = password; }
        /** Callback owns no secret array after returning. SDK's immutable Strings cannot be erased. */
        public synchronized DiscoveryGraph discover(VimDiscoveryClient client) throws DiscoveryException {
            try {
                if (password == null || client == null) throw new DiscoveryException(DiscoveryException.Code.AUTHENTICATION);
                return client.discover(username, password);
            }
            catch (DiscoveryException ex) { throw new DiscoveryException(ex.code()); }
            catch (RuntimeException | LinkageError ex) { throw new DiscoveryException(DiscoveryException.Code.CONNECTION); }
            finally { close(); }
        }
        @Override public synchronized void close() {
            if (password != null) Arrays.fill(password, '\0'); password = null; username = null;
        }
        @Override public String toString() { return "Credentials[redacted]"; }
    }
    private static void fail() throws DiscoveryException { throw new DiscoveryException(DiscoveryException.Code.CONFIGURATION); }
    @Override public String toString() { return "RuntimeDiscoveryBinding[redacted,dormant]"; }
}
