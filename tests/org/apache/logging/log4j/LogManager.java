package org.apache.logging.log4j;

/** Test-only logging linkage shim: actual SDK descriptor validation is unmodified.
 * Compiled only by scripts/run-tests.sh, never included in src/ or the PAK. */
public final class LogManager {
    private static final Logger SILENT = new Logger() { };
    private LogManager() { }
    public static Logger getLogger(String name) { return SILENT; }
    public static Logger getLogger(Class<?> type) { return SILENT; }
}
