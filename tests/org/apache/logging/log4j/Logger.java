package org.apache.logging.log4j;

/** Test-only no-op logging API, not a validation substitute or production dependency.
 * Signatures match the supplied SDK descriptor loaders' logging invocations. */
public interface Logger {
    default void debug(String message) { }
    default void warn(String message) { }
    default void warn(Object message) { }
    default void error(String message) { }
}
