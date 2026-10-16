package com.vcfcf.adapters.rdmtopologypoc.ops;

import java.io.*;
import java.net.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Independent Operations HTTPS transport. No proxy inheritance, redirects, cookies or global TLS mutation. */
final class OpsHttpsTransport implements SuiteApiVmProvider.Transport {
    private final URI endpoint;
    private final SSLContext tls;
    private final int connectMillis, readMillis;
    private volatile boolean unusable;
    OpsHttpsTransport(URI endpoint, SSLContext tls, int connectMillis, int readMillis) throws OpsException {
        if (endpoint == null || tls == null || connectMillis < 1 || connectMillis > 60000
                || readMillis < 1 || readMillis > 120000) throw new OpsException(OpsException.Code.CONFIGURATION);
        this.endpoint = endpoint; this.tls = tls; this.connectMillis = connectMillis; this.readMillis = readMillis;
    }
    @Override public synchronized SuiteApiVmProvider.Reply exchange(String method, String path, byte[] request,
            String token, long remainingNanos) throws OpsException {
        if (unusable) throw new OpsException(OpsException.Code.UNREACHABLE);
        if (remainingNanos <= 0) throw new OpsException(OpsException.Code.TIMEOUT);
        if (Thread.currentThread().isInterrupted()) throw new OpsException(OpsException.Code.CANCELLED);
        validate(method, path, request, token);
        long timeout = Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(connectMillis + (long) readMillis));
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "rdm-ops-https"); t.setDaemon(true); return t; });
        final java.util.concurrent.atomic.AtomicReference<Socket> active = new java.util.concurrent.atomic.AtomicReference<>();
        Future<SuiteApiVmProvider.Reply> result = worker.submit(() -> perform(method, path, request, token, active));
        try { return result.get(timeout, TimeUnit.NANOSECONDS); }
        catch (TimeoutException ex) { unusable = true; throw new OpsException(OpsException.Code.TIMEOUT); }
        catch (InterruptedException ex) { unusable = true; Thread.currentThread().interrupt(); throw new OpsException(OpsException.Code.CANCELLED); }
        catch (ExecutionException ex) {
            Throwable cause = ex.getCause(); if (cause instanceof OpsException) throw (OpsException) cause;
            throw new OpsException(OpsException.Code.UNREACHABLE);
        } finally {
            result.cancel(true); worker.shutdownNow();
            close(active.get());
        }
    }
    static void validate(String method, String path, byte[] request, String token) throws OpsException {
        boolean acquire = "POST".equals(method) && "/api/auth/token/acquire".equals(path);
        boolean release = "POST".equals(method) && "/api/auth/token/release".equals(path);
        boolean read = "GET".equals(method) && (path.matches("/api/resources\\?adapterKind=VMWARE&resourceKind=VirtualMachine&page=[0-9]{1,3}&pageSize=[1-9][0-9]{0,2}")
                || path.matches("/api/resources/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
        if (!(acquire || release || read) || request == null || request.length > 32768
                || (acquire ? request.length == 0 || token != null : request.length != 0 || !validToken(token)))
            throw new OpsException(OpsException.Code.CONFIGURATION);
    }
    static boolean validToken(String token) { return token != null && token.matches("[\\x21-\\x7e]{1,8192}"); }
    private SuiteApiVmProvider.Reply perform(String method, String path, byte[] request, String token,
            java.util.concurrent.atomic.AtomicReference<Socket> active) throws OpsException {
        SSLSocket socket = null; Socket direct = null;
        try {
            direct = new Socket(Proxy.NO_PROXY); active.set(direct);
            if (Thread.currentThread().isInterrupted()) throw new OpsException(OpsException.Code.CANCELLED);
            int port = endpoint.getPort() == -1 ? 443 : endpoint.getPort();
            direct.connect(new InetSocketAddress(endpoint.getHost(), port), connectMillis);
            if (Thread.currentThread().isInterrupted()) throw new OpsException(OpsException.Code.CANCELLED);
            socket = (SSLSocket) tls.getSocketFactory().createSocket(direct, endpoint.getHost(), port, true); active.set(socket);
            socket.setSoTimeout(readMillis);
            SSLParameters parameters = socket.getSSLParameters(); parameters.setEndpointIdentificationAlgorithm("HTTPS");
            parameters.setServerNames(List.of(new SNIHostName(endpoint.getHost()))); socket.setSSLParameters(parameters);
            socket.startHandshake(); verifyName(endpoint.getHost(), (X509Certificate) socket.getSession().getPeerCertificates()[0]);
            OutputStream out = socket.getOutputStream();
            String headers = method + " /suite-api" + path + " HTTP/1.1\r\nHost: " + endpoint.getRawAuthority()
                    + "\r\nAccept: application/json\r\nConnection: close\r\n"
                    + (token == null ? "" : "Authorization: OpsToken " + token + "\r\n")
                    + ("POST".equals(method) ? "Content-Type: application/json; charset=utf-8\r\nContent-Length: " + request.length + "\r\n" : "") + "\r\n";
            out.write(headers.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); out.write(request); out.flush();
            InputStream in = new BufferedInputStream(socket.getInputStream());
            HeaderBudget budget = new HeaderBudget(); String statusLine = line(in, budget);
            if (!statusLine.matches("HTTP/1\\.[01] [1-5][0-9]{2}(?: [\\x20-\\x7e]*)?")) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
            int status = Integer.parseInt(statusLine.substring(9, 12)); Map<String,String> fields = new HashMap<>(); String field;
            while (!(field = line(in, budget)).isEmpty()) {
                int colon = field.indexOf(':');
                if (colon < 1 || !field.substring(0, colon).matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+")) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
                String name = field.substring(0, colon).toLowerCase(Locale.ROOT), value = field.substring(colon + 1).trim();
                if (fields.putIfAbsent(name, value) != null) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
            }
            // Never buffer or parse error bodies. Their contents are irrelevant to sanitized classification.
            if (status != 200) return new SuiteApiVmProvider.Reply(status, new byte[0]);
            String encoding = fields.get("content-encoding");
            if (encoding != null && !encoding.equalsIgnoreCase("identity")) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
            String type = fields.get("content-type");
            if (!path.equals("/api/auth/token/release") && (type == null || !type.toLowerCase(Locale.ROOT).matches("application/json(?:\\s*;\\s*charset=utf-8)?")))
                throw new OpsException(OpsException.Code.INVALID_RESPONSE);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            String transfer = fields.get("transfer-encoding"), length = fields.get("content-length");
            if (transfer != null && length != null) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
            if (transfer != null) {
                if (!transfer.equalsIgnoreCase("chunked")) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
                while (true) {
                    String chunk = line(in, budget);
                    if (!chunk.matches("[0-9a-fA-F]{1,8}")) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
                    long size = Long.parseLong(chunk, 16);
                    if (size == 0) { if (!line(in, budget).isEmpty()) throw new OpsException(OpsException.Code.INVALID_RESPONSE); break; }
                    readBody(in, body, size);
                    if (in.read() != '\r' || in.read() != '\n') throw new OpsException(OpsException.Code.INVALID_RESPONSE);
                }
            } else if (length != null) {
                if (!length.matches("0|[1-9][0-9]{0,9}")) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
                readBody(in, body, Long.parseLong(length));
            } else {
                byte[] bytes = new byte[8192]; int n;
                while ((n = in.read(bytes)) != -1) { bounded(body.size(), n); body.write(bytes, 0, n); }
            }
            return new SuiteApiVmProvider.Reply(status, body.toByteArray());
        } catch (OpsException ex) { throw ex; }
        catch (SSLException ex) { throw new OpsException(OpsException.Code.TLS); }
        catch (SocketTimeoutException ex) { throw new OpsException(OpsException.Code.TIMEOUT); }
        catch (Exception | LinkageError ex) { throw new OpsException(OpsException.Code.UNREACHABLE); }
        finally { close(socket); close(direct); }
    }
    private static void close(Socket socket) { if (socket != null) try { socket.close(); } catch (IOException ignored) { } }
    private static final class HeaderBudget { int bytes, lines; }
    private static String line(InputStream in, HeaderBudget budget) throws IOException, OpsException {
        if (++budget.lines > 512) throw new OpsException(OpsException.Code.RESPONSE_LIMIT);
        StringBuilder line = new StringBuilder(); int ch;
        while ((ch = in.read()) != -1) {
            if (++budget.bytes > 65536 || line.length() > 8192) throw new OpsException(OpsException.Code.RESPONSE_LIMIT);
            if (ch == '\r') {
                if (++budget.bytes > 65536) throw new OpsException(OpsException.Code.RESPONSE_LIMIT);
                if (in.read() != '\n') throw new OpsException(OpsException.Code.INVALID_RESPONSE); return line.toString();
            }
            if (ch < 32 && ch != '\t' || ch > 126) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
            line.append((char) ch);
        }
        throw new OpsException(OpsException.Code.INVALID_RESPONSE);
    }
    private static void bounded(int current, long length) throws OpsException {
        if (Thread.currentThread().isInterrupted()) throw new OpsException(OpsException.Code.CANCELLED);
        if (length < 0 || current + length > OpsJson.MAX_BYTES) throw new OpsException(OpsException.Code.RESPONSE_LIMIT);
    }
    private static void readBody(InputStream in, ByteArrayOutputStream body, long length) throws IOException, OpsException {
        bounded(body.size(), length); byte[] bytes = new byte[8192];
        while (length > 0) {
            int n = in.read(bytes, 0, (int) Math.min(bytes.length, length));
            if (n == -1) throw new OpsException(OpsException.Code.INVALID_RESPONSE); body.write(bytes, 0, n); length -= n;
        }
    }
    private static void verifyName(String host, X509Certificate certificate) throws OpsException {
        try {
            boolean matched = false, ip = host.matches("[0-9.]+") || host.indexOf(':') >= 0;
            Collection<List<?>> sans = certificate.getSubjectAlternativeNames();
            if (sans != null) for (List<?> san : sans) {
                if (san.size() != 2 || !(san.get(0) instanceof Integer) || !(san.get(1) instanceof String)) continue;
                String name = (String) san.get(1); int type = (Integer) san.get(0);
                if (ip && type == 7) matched |= InetAddress.getByName(host).equals(InetAddress.getByName(name));
                if (!ip && type == 2) {
                    String h = host.toLowerCase(Locale.ROOT), n = name.toLowerCase(Locale.ROOT);
                    if (n.startsWith("*.") && n.indexOf('*', 1) < 0) {
                        String tail = n.substring(1);
                        matched |= h.endsWith(tail) && h.length() > tail.length()
                                && h.substring(0, h.length() - tail.length()).indexOf('.') < 0;
                    } else matched |= h.equals(n);
                }
            }
            if (!matched) throw new OpsException(OpsException.Code.TLS);
        } catch (OpsException ex) { throw ex; }
        catch (Exception ex) { throw new OpsException(OpsException.Code.TLS); }
    }
}
