package com.vcfcf.adapters.rdmtopologypoc.ops;

/** Only static categories escape this boundary; never retain a response or credential cause. */
public final class OpsException extends Exception {
    public enum Code { CONFIGURATION, RUNTIME_UNQUALIFIED, CREDENTIAL_UNAVAILABLE, UNREACHABLE,
        TLS, AUTHENTICATION, AUTHORIZATION, INVALID_RESPONSE, NOT_FOUND, AMBIGUOUS,
        INVALID_RESOURCE_KEY, IDENTITY_MISMATCH, TIMEOUT, RESPONSE_LIMIT, INCOMPLETE, CANCELLED }
    private final Code code;
    public OpsException(Code code) { super("OPERATIONS_IDENTITY_" + code.name(), null, false, false); this.code = code; }
    public Code code() { return code; }
}
