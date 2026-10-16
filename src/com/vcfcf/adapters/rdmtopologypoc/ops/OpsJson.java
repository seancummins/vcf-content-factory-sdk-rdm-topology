package com.vcfcf.adapters.rdmtopologypoc.ops;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;

/** Small strict JSON reader: no polymorphism, duplicate keys, lenient booleans or replacement UTF-8. */
final class OpsJson {
    static final int MAX_BYTES = 4 * 1024 * 1024;
    private final String text;
    private int at, nodes;
    private OpsJson(String text) { this.text = text; }
    static Object parse(byte[] bytes) throws OpsException {
        if (bytes == null || bytes.length > MAX_BYTES) throw new OpsException(OpsException.Code.RESPONSE_LIMIT);
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            OpsJson parser = new OpsJson(text); Object result = parser.value(0); parser.space();
            if (parser.at != text.length()) bad(); return result;
        } catch (OpsException ex) { throw ex; }
        catch (Exception ex) { throw new OpsException(OpsException.Code.INVALID_RESPONSE); }
    }
    private static void bad() throws OpsException { throw new OpsException(OpsException.Code.INVALID_RESPONSE); }
    private void space() { while (at < text.length() && " \r\n\t".indexOf(text.charAt(at)) >= 0) at++; }
    private boolean take(char c) { space(); if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
    private Object value(int depth) throws OpsException {
        if (depth > 24 || ++nodes > 100000) throw new OpsException(OpsException.Code.RESPONSE_LIMIT);
        space(); if (at >= text.length()) { bad(); return null; }
        char c = text.charAt(at);
        if (c == '"') return string();
        if (c == '{') {
            at++; Map<String,Object> map = new LinkedHashMap<>(); if (take('}')) return map;
            do { space(); if (at >= text.length() || text.charAt(at) != '"') bad(); String key = string();
                if (!take(':') || map.containsKey(key)) bad(); map.put(key, value(depth + 1));
            } while (take(',')); if (!take('}')) bad(); return map;
        }
        if (c == '[') {
            at++; List<Object> list = new ArrayList<>(); if (take(']')) return list;
            do { list.add(value(depth + 1)); } while (take(',')); if (!take(']')) bad(); return list;
        }
        for (String literal : List.of("true", "false", "null")) if (text.startsWith(literal, at)) {
            at += literal.length(); return literal.equals("null") ? null : Boolean.valueOf(literal);
        }
        int start = at;
        while (at < text.length() && "0123456789eE+-.".indexOf(text.charAt(at)) >= 0) at++;
        String number = text.substring(start, at);
        if (number.length() > 64 || !number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) bad();
        try { return new BigDecimal(number); } catch (NumberFormatException ex) { bad(); return null; }
    }
    private String string() throws OpsException {
        at++; StringBuilder out = new StringBuilder(); boolean ended = false;
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') { ended = true; break; }
            if (c < 32) bad();
            if (c == '\\') {
                if (at >= text.length()) bad(); c = text.charAt(at++);
                switch (c) {
                    case '"': case '\\': case '/': break;
                    case 'b': c = '\b'; break; case 'f': c = '\f'; break;
                    case 'n': c = '\n'; break; case 'r': c = '\r'; break; case 't': c = '\t'; break;
                    case 'u':
                        if (at + 4 > text.length()) bad();
                        if (!text.substring(at, at + 4).matches("[0-9a-fA-F]{4}")) bad();
                        try { c = (char) Integer.parseInt(text.substring(at, at + 4), 16); }
                        catch (NumberFormatException ex) { bad(); } at += 4; break;
                    default: bad();
                }
            }
            out.append(c); if (out.length() > 16384) throw new OpsException(OpsException.Code.RESPONSE_LIMIT);
        }
        if (!ended) bad();
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (Character.isHighSurrogate(c)) { if (++i >= out.length() || !Character.isLowSurrogate(out.charAt(i))) bad(); }
            else if (Character.isLowSurrogate(c)) bad();
        }
        return out.toString();
    }
    @SuppressWarnings("unchecked") static Map<String,Object> object(Object value) throws OpsException {
        if (!(value instanceof Map)) throw new OpsException(OpsException.Code.INVALID_RESPONSE); return (Map<String,Object>) value;
    }
    @SuppressWarnings("unchecked") static List<Object> array(Object value) throws OpsException {
        if (!(value instanceof List)) throw new OpsException(OpsException.Code.INVALID_RESPONSE); return (List<Object>) value;
    }
    static String string(Object value) throws OpsException {
        if (!(value instanceof String)) throw new OpsException(OpsException.Code.INVALID_RESPONSE); return (String) value;
    }
    static long integer(Object value) throws OpsException {
        if (!(value instanceof BigDecimal)) throw new OpsException(OpsException.Code.INVALID_RESPONSE);
        try { return ((BigDecimal) value).longValueExact(); }
        catch (ArithmeticException ex) { throw new OpsException(OpsException.Code.INVALID_RESPONSE); }
    }
    static String quote(String value) throws OpsException {
        if (value == null || value.length() > 16384) throw new OpsException(OpsException.Code.CREDENTIAL_UNAVAILABLE);
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1)))
                    throw new OpsException(OpsException.Code.CREDENTIAL_UNAVAILABLE);
                result.append(c).append(value.charAt(++i));
            } else if (Character.isLowSurrogate(c)) throw new OpsException(OpsException.Code.CREDENTIAL_UNAVAILABLE);
            else if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32) result.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
}
