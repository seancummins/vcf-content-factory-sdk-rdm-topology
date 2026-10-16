package com.vcfcf.adapters.rdmtopologypoc.ops;

import com.vcfcf.adapters.rdmtopologypoc.discovery.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Dormant read-only provider. A batch uses one inventory scan, one token, and fresh detail per requested VM. */
public final class SuiteApiVmProvider {
    interface Transport { Reply exchange(String method, String path, byte[] body, String token, long remainingNanos) throws OpsException; }
    interface CredentialSource { OperationsBinding.Credentials acquire() throws OpsException; }
    static final class Reply {
        final int status; final byte[] body;
        Reply(int status, byte[] body) { this.status = status; this.body = body == null ? null : body.clone(); }
        @Override public String toString() { return "OperationsReply[redacted]"; }
    }
    private static final long CYCLE_NANOS = 120_000_000_000L, CLEANUP_NANOS = 5_000_000_000L;
    private static final int PAGE_SIZE = 200, MAX_PAGES = 100, MAX_RESOURCES = 20000, MAX_TARGETS = 1000;
    private final Transport transport;
    private final CredentialSource credentials;
    private final LongSupplier nanos, millis;
    private OpsException.Code cleanupFailure;
    private long responseBytes;
    SuiteApiVmProvider(Transport transport, CredentialSource credentials) { this(transport, credentials, System::nanoTime, System::currentTimeMillis); }
    SuiteApiVmProvider(Transport transport, CredentialSource credentials, LongSupplier nanos, LongSupplier millis) {
        this.transport = transport; this.credentials = credentials; this.nanos = nanos; this.millis = millis;
    }
    /** Static diagnostic only. Never contains remote text and never replaces the primary result. */
    public synchronized Optional<OpsException.Code> cleanupFailure() { return Optional.ofNullable(cleanupFailure); }
    public DiscoveryGraph.VmKey resolve(String vcenter, String moid) throws OpsException {
        return capture(vcenter, Collections.singleton(moid)).resolve(vcenter, moid);
    }
    /** Result is complete only for the caller-visible API scope, not a global RBAC/atomic-inventory guarantee. */
    public synchronized Snapshot capture(String vcenter, Set<String> moids) throws OpsException {
        cleanupFailure = null;
        responseBytes = 0;
        if (transport == null || credentials == null || nanos == null || millis == null || !uuid(vcenter)
                || moids == null || moids.isEmpty() || moids.size() > MAX_TARGETS)
            throw new OpsException(OpsException.Code.CONFIGURATION);
        Set<String> targets = new LinkedHashSet<>();
        for (String moid : moids) { if (!moid(moid)) throw new OpsException(OpsException.Code.IDENTITY_MISMATCH); targets.add(moid); }
        String token = null; long started = now(nanos); byte[] request = null;
        try {
            try (OperationsBinding.Credentials credential = credentials.acquire()) {
                if (credential == null) throw new OpsException(OpsException.Code.CREDENTIAL_UNAVAILABLE);
                request = credential.request();
                Map<String,Object> auth = object(call("POST", "/api/auth/token/acquire", request, null, started, -1));
                credential.close();
                String acquired = OpsJson.string(auth.get("token"));
                if (!OpsHttpsTransport.validToken(acquired)) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
                token = acquired; // Capture before validating the rest, so a malformed token response is still released.
                long expiry = OpsJson.integer(auth.get("validity"));
                if (expiry <= now(millis)) throw new OpsException(OpsException.Code.AUTHENTICATION);
                Arrays.fill(request, (byte) 0); request = null;
                List<Resource> rows = new ArrayList<>(); Set<String> ids = new HashSet<>(), scopes = new HashSet<>();
                long total = -1, rowCount = 0, selectedCharacters = 0;
                for (int page = 0; page < MAX_PAGES; page++) {
                    Map<String,Object> payload = object(call("GET", "/api/resources?adapterKind=VMWARE&resourceKind=VirtualMachine&page="
                            + page + "&pageSize=" + PAGE_SIZE, new byte[0], token, started, expiry));
                    Map<String,Object> info = object(payload.get("pageInfo"));
                    long count = OpsJson.integer(info.get("totalCount"));
                    if (count < 0 || count > MAX_RESOURCES || total != -1 && total != count
                            || OpsJson.integer(info.get("page")) != page || OpsJson.integer(info.get("pageSize")) != PAGE_SIZE)
                        throw new OpsException(OpsException.Code.INCOMPLETE);
                    total = count; List<Object> pageRows = OpsJson.array(payload.get("resourceList"));
                    if (pageRows.size() > PAGE_SIZE) throw new OpsException(OpsException.Code.INCOMPLETE);
                    for (Object value : pageRows) {
                        Resource resource = resource(value);
                        if (!ids.add(resource.id)) throw new OpsException(OpsException.Code.AMBIGUOUS);
                        String scope = resource.key.scoped("VMEntityVCID") + "/" + resource.key.scoped("VMEntityObjectID");
                        if (!scopes.add(scope)) throw new OpsException(OpsException.Code.AMBIGUOUS);
                        rowCount++;
                        if (vcenter.equals(resource.key.scoped("VMEntityVCID")) && targets.contains(resource.key.scoped("VMEntityObjectID"))) {
                            selectedCharacters += resource.key.displayName.length();
                            for (DiscoveryGraph.Identifier field : resource.key.identifiers) selectedCharacters += field.name.length() + field.value.length();
                            if (selectedCharacters > 2 * 1024 * 1024) throw new OpsException(OpsException.Code.RESPONSE_LIMIT);
                            rows.add(resource);
                        }
                    }
                    if (rowCount == total) break;
                    if (rowCount > total || pageRows.size() != PAGE_SIZE || page == MAX_PAGES - 1)
                        throw new OpsException(OpsException.Code.INCOMPLETE);
                }
                Map<String,DiscoveryGraph.VmKey> resolved = new LinkedHashMap<>();
                for (String target : targets) {
                    Resource selected = null;
                    for (Resource row : rows) if (vcenter.equals(row.key.scoped("VMEntityVCID")) && target.equals(row.key.scoped("VMEntityObjectID"))) {
                        if (selected != null) throw new OpsException(OpsException.Code.AMBIGUOUS); selected = row;
                    }
                    if (selected == null) throw new OpsException(OpsException.Code.NOT_FOUND);
                    Resource fresh = resource(call("GET", "/api/resources/" + selected.id, new byte[0], token, started, expiry));
                    if (!selected.id.equals(fresh.id) || !sameKey(selected.key, fresh.key)
                            || !vcenter.equals(fresh.key.scoped("VMEntityVCID")) || !target.equals(fresh.key.scoped("VMEntityObjectID")))
                        throw new OpsException(OpsException.Code.IDENTITY_MISMATCH);
                    resolved.put(target, fresh.key);
                }
                remaining(started);
                return new Snapshot(vcenter, resolved, started, nanos);
            }
        } catch (OpsException ex) { throw new OpsException(ex.code()); }
        catch (DiscoveryException ex) { throw new OpsException(OpsException.Code.INVALID_RESOURCE_KEY); }
        catch (RuntimeException | LinkageError ex) { throw new OpsException(OpsException.Code.INVALID_RESPONSE); }
        finally {
            if (request != null) Arrays.fill(request, (byte) 0);
            if (token != null) {
                try {
                    Reply release = transport.exchange("POST", "/api/auth/token/release", new byte[0], token, CLEANUP_NANOS);
                    if (release == null || release.status != 200) cleanupFailure = OpsException.Code.INVALID_RESPONSE;
                } catch (OpsException ex) { cleanupFailure = ex.code(); }
                catch (RuntimeException | LinkageError ex) { cleanupFailure = OpsException.Code.INVALID_RESPONSE; }
            }
        }
    }
    private Object call(String method, String path, byte[] body, String token, long start, long expiry) throws OpsException {
        long remaining = remaining(start);
        if (expiry != -1 && now(millis) >= expiry) throw new OpsException(OpsException.Code.AUTHENTICATION);
        Reply reply = transport.exchange(method, path, body, token, remaining);
        remaining(start);
        if (reply == null) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
        if (reply.status == 401) throw new OpsException(OpsException.Code.AUTHENTICATION);
        if (reply.status == 403) throw new OpsException(OpsException.Code.AUTHORIZATION);
        if (reply.status == 404 && path.startsWith("/api/resources/")) throw new OpsException(OpsException.Code.NOT_FOUND);
        if (reply.status != 200) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
        if (reply.body == null || (responseBytes += reply.body.length) > 32L * 1024 * 1024)
            throw new OpsException(OpsException.Code.RESPONSE_LIMIT);
        return OpsJson.parse(reply.body);
    }
    private long remaining(long start) throws OpsException {
        try {
            long elapsed = Math.subtractExact(now(nanos), start);
            if (elapsed < 0 || elapsed >= CYCLE_NANOS) throw new OpsException(OpsException.Code.TIMEOUT);
            return CYCLE_NANOS - elapsed;
        } catch (ArithmeticException ex) { throw new OpsException(OpsException.Code.TIMEOUT); }
    }
    private static long now(LongSupplier clock) throws OpsException {
        try { return clock.getAsLong(); }
        catch (RuntimeException | LinkageError ex) { throw new OpsException(OpsException.Code.INVALID_RESPONSE); }
    }
    private static Map<String,Object> object(Object value) throws OpsException { return OpsJson.object(value); }
    private static Resource resource(Object value) throws OpsException {
        try {
            Map<String,Object> row = object(value), key = object(row.get("resourceKey"));
            String id = OpsJson.string(row.get("identifier")); if (!uuid(id)) throw new OpsException(OpsException.Code.INVALID_RESOURCE_KEY);
            List<Object> entries = OpsJson.array(key.get("resourceIdentifiers"));
            if (entries.isEmpty() || entries.size() > 64) throw new OpsException(OpsException.Code.INVALID_RESOURCE_KEY);
            List<DiscoveryGraph.Identifier> identifiers = new ArrayList<>();
            for (Object entry : entries) {
                Map<String,Object> identifier = object(entry), type = object(identifier.get("identifierType"));
                Object unique = type.get("isPartOfUniqueness");
                if (!(unique instanceof Boolean)) throw new OpsException(OpsException.Code.INVALID_RESOURCE_KEY);
                // Explicit empty nonunique strings are supported; absent/null are not silently coerced.
                identifiers.add(new DiscoveryGraph.Identifier(OpsJson.string(type.get("name")), OpsJson.string(identifier.get("value")), (Boolean) unique));
            }
            DiscoveryGraph.VmKey vm = new DiscoveryGraph.VmKey(OpsJson.string(key.get("adapterKindKey")),
                    OpsJson.string(key.get("resourceKindKey")), OpsJson.string(key.get("name")), identifiers);
            return new Resource(id, vm);
        } catch (OpsException | DiscoveryException ex) { throw new OpsException(OpsException.Code.INVALID_RESOURCE_KEY); }
    }
    private static boolean sameKey(DiscoveryGraph.VmKey a, DiscoveryGraph.VmKey b) {
        if (!a.displayName.equals(b.displayName) || a.identifiers.size() != b.identifiers.size()) return false;
        for (int i = 0; i < a.identifiers.size(); i++) {
            DiscoveryGraph.Identifier x = a.identifiers.get(i), y = b.identifiers.get(i);
            if (!x.name.equals(y.name) || !x.value.equals(y.value) || x.unique != y.unique) return false;
        }
        return true;
    }
    static boolean uuid(String value) { return value != null && value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
            && !value.equals("00000000-0000-0000-0000-000000000000"); }
    private static boolean moid(String value) { return value != null && value.matches("vm-[1-9][0-9]{0,18}"); }
    private static final class Resource {
        final String id; final DiscoveryGraph.VmKey key;
        Resource(String id, DiscoveryGraph.VmKey key) { this.id = id; this.key = key; }
    }
    public static final class Snapshot {
        private final String vcenter; private final Map<String,DiscoveryGraph.VmKey> keys;
        private final long started; private final LongSupplier nanos;
        private Snapshot(String vcenter, Map<String,DiscoveryGraph.VmKey> keys, long started, LongSupplier nanos) {
            this.vcenter = vcenter; this.keys = Map.copyOf(keys); this.started = started; this.nanos = nanos;
        }
        public DiscoveryGraph.VmKey resolve(String vc, String moid) throws OpsException {
            if (!uuid(vc) || !vcenter.equals(vc) || !moid(moid)) throw new OpsException(OpsException.Code.IDENTITY_MISMATCH);
            try {
                long age = Math.subtractExact(now(nanos), started);
                if (age < 0 || age >= CYCLE_NANOS) throw new OpsException(OpsException.Code.TIMEOUT);
            } catch (ArithmeticException ex) { throw new OpsException(OpsException.Code.TIMEOUT); }
            DiscoveryGraph.VmKey key = keys.get(moid); if (key == null) throw new OpsException(OpsException.Code.NOT_FOUND); return key;
        }
        /** Explicit bridge only; rich sanitized categories remain available on resolve(). */
        public DiscoveryGraph.VmResolver resolver() {
            return (vc, moid) -> {
                try { return resolve(vc, moid); }
                catch (OpsException ex) { throw new DiscoveryException(ex.code() == OpsException.Code.AMBIGUOUS
                        ? DiscoveryException.Code.AMBIGUOUS : ex.code() == OpsException.Code.TIMEOUT
                        ? DiscoveryException.Code.INCOMPLETE : DiscoveryException.Code.IDENTITY); }
            };
        }
        @Override public String toString() { return "OperationsIdentitySnapshot[redacted]"; }
    }
    @Override public String toString() { return "SuiteApiVmProvider[dormant,redacted]"; }
}
