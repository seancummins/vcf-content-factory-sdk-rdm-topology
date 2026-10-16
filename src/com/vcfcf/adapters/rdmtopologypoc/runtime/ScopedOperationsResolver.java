package com.vcfcf.adapters.rdmtopologypoc.runtime;

import com.vcfcf.adapters.rdmtopologypoc.discovery.*;
import java.util.List;
import java.util.function.LongSupplier;

/** Offline provider boundary, NOT a Suite API HTTP/authentication implementation. */
public final class ScopedOperationsResolver implements DiscoveryGraph.VmResolver {
    public enum Failure { AUTHENTICATION, TRANSPORT, ROUTING_UNQUALIFIED, INCOMPLETE, STALE }
    public static final class ProviderException extends Exception {
        public final Failure failure;
        public ProviderException(Failure failure) { super("OPERATIONS_BRIDGE_" + failure.name()); this.failure = failure; }
    }
    public interface Provider { Snapshot capture() throws ProviderException; }
    public static final class Snapshot {
        private final List<DiscoveryGraph.VmKey> keys;
        private final boolean complete, authenticated, routeQualified;
        private final long startedNanos;
        private final String vcenter;
        private final long generation;
        public Snapshot(List<DiscoveryGraph.VmKey> keys, boolean complete, boolean authenticated,
                boolean routeQualified, long startedNanos, String vcenter, long generation) {
            this.keys = keys == null ? null : List.copyOf(keys);
            this.complete = complete; this.authenticated = authenticated;
            this.routeQualified = routeQualified; this.startedNanos = startedNanos;
            this.vcenter = vcenter; this.generation = generation;
        }
        @Override public String toString() { return "OperationsSnapshot[redacted]"; }
    }
    private final DiscoveryGraph.OfflineResolver delegate;
    private final LongSupplier clock;
    private final long started, maxAge;
    private final String vcenter;
    /** Explicit offline-only factory: attestations are synthetic inputs, not runtime proofs. */
    public static ScopedOperationsResolver offline(Provider provider, LongSupplier monotonicClock,
            long maxAgeNanos, String vcenter, long generation) throws DiscoveryException {
        return new ScopedOperationsResolver(provider, monotonicClock, maxAgeNanos, vcenter, generation);
    }
    public static DiscoveryGraph.VmResolver production() throws DiscoveryException {
        throw new DiscoveryException(DiscoveryException.Code.CONFIGURATION);
    }
    private ScopedOperationsResolver(Provider provider, LongSupplier monotonicClock, long maxAgeNanos,
            String vcenter, long generation) throws DiscoveryException {
        if (provider == null || monotonicClock == null || maxAgeNanos < 1 || maxAgeNanos > 120_000_000_000L
                || vcenter == null || !vcenter.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                || "00000000-0000-0000-0000-000000000000".equals(vcenter)
                || generation < 1)
            throw new DiscoveryException(DiscoveryException.Code.CONFIGURATION);
        this.clock = monotonicClock; this.maxAge = maxAgeNanos; this.vcenter = vcenter;
        Snapshot snapshot;
        try { snapshot = provider.capture(); }
        catch (ProviderException ex) {
            throw new DiscoveryException(ex.failure == Failure.AUTHENTICATION ? DiscoveryException.Code.AUTHENTICATION
                    : ex.failure == Failure.TRANSPORT ? DiscoveryException.Code.CONNECTION : DiscoveryException.Code.INCOMPLETE);
        } catch (RuntimeException | LinkageError ex) { throw new DiscoveryException(DiscoveryException.Code.INCOMPLETE); }
        if (snapshot == null || !snapshot.authenticated || !snapshot.routeQualified || !snapshot.complete
                || !vcenter.equals(snapshot.vcenter) || generation != snapshot.generation)
            throw new DiscoveryException(DiscoveryException.Code.INCOMPLETE);
        this.started = snapshot.startedNanos;
        checkFresh();
        this.delegate = new DiscoveryGraph.OfflineResolver(snapshot.keys, true);
        for (DiscoveryGraph.VmKey key : snapshot.keys)
            if (!vcenter.equals(key.scoped("VMEntityVCID"))) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
    }
    private void checkFresh() throws DiscoveryException {
        try {
            long age = Math.subtractExact(clock.getAsLong(), started);
            if (age < 0 || age > maxAge) throw new DiscoveryException(DiscoveryException.Code.INCOMPLETE);
        } catch (RuntimeException | LinkageError ex) { throw new DiscoveryException(DiscoveryException.Code.INCOMPLETE); }
    }
    @Override public DiscoveryGraph.VmKey resolve(String vc, String moid) throws DiscoveryException {
        checkFresh();
        if (!vcenter.equals(vc)) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
        DiscoveryGraph.VmKey result = delegate.resolve(vc, moid);
        checkFresh();
        return result;
    }
    @Override public String toString() { return "ScopedOperationsResolver[offline-provider,redacted]"; }
}
