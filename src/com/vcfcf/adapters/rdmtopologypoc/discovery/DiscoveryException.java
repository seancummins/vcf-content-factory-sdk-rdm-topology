package com.vcfcf.adapters.rdmtopologypoc.discovery;

/** Deliberately contains no remote text, credentials, identifiers, or unsafe cause. */
public final class DiscoveryException extends Exception {
    public enum Code { CONFIGURATION, TLS, CONNECTION, AUTHENTICATION, HTTP, XML, SOAP_FAULT,
        STRUCTURE, INCOMPLETE, AMBIGUOUS, LIMIT, SESSION, IDENTITY, UNSTABLE, LUN_UNRESOLVED, CANCELLED }
    private final Code code;
    public DiscoveryException(Code code) { super("RDM_DISCOVERY_" + code.name()); this.code = code; }
    public Code code() { return code; }
    @Override public String toString() { return getMessage(); }
}
