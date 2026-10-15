package com.vcfcf.adapters.rdmtopologypoc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One bounded, immutable, fully validated instance setting. Never logs input. */
final class RuntimeFixture {
    final ForeignVmIdentity vm;
    final String vmName;
    final RdmMappingIdentity mapping;

    private RuntimeFixture(ForeignVmIdentity vm, String vmName, RdmMappingIdentity mapping) {
        this.vm = vm;
        this.vmName = vmName;
        this.mapping = mapping;
    }

    static RuntimeFixture parse(String input) {
        try {
            Map<String, Object> root = object(new Parser(input).parse(), "foreign_vm", "mapping");
            Map<String, Object> foreign = object(root.get("foreign_vm"),
                    "adapterKindKey", "resourceKindKey", "name", "resourceIdentifiers");
            require("VMWARE".equals(foreign.get("adapterKindKey"))
                    && "VirtualMachine".equals(foreign.get("resourceKindKey")));
            String name = string(foreign.get("name"));
            Object entries = foreign.get("resourceIdentifiers");
            require(entries instanceof List && !((List<?>) entries).isEmpty());
            List<ForeignVmIdentity.Identifier> identifiers = new ArrayList<>();
            Map<String, String> values = new LinkedHashMap<>();
            for (Object entry : (List<?>) entries) {
                Map<String, Object> id = object(entry, "identifierType", "value");
                Map<String, Object> type = object(id.get("identifierType"),
                        "name", "isPartOfUniqueness");
                String key = string(type.get("name"));
                String value = string(id.get("value"));
                require(type.get("isPartOfUniqueness") instanceof Boolean);
                require(!values.containsKey(key));
                values.put(key, value);
                identifiers.add(new ForeignVmIdentity.Identifier(key, value,
                        (Boolean) type.get("isPartOfUniqueness")));
            }
            ForeignVmIdentity vm = new ForeignVmIdentity(identifiers);
            require(string(values.get(ForeignVmIdentity.VM_MOID)).matches("vm-[1-9][0-9]{0,18}"));
            Map<String, Object> local = object(root.get("mapping"),
                    "vcenter_instance_uuid", "vm_instance_uuid", "virtual_disk_key");
            String vc = uuid(local.get("vcenter_instance_uuid"));
            String instance = uuid(local.get("vm_instance_uuid"));
            require(vc.equals(uuid(values.get(ForeignVmIdentity.VCENTER_INSTANCE_UUID))));
            // Preserve every supplied identifier; reject conflicting instance UUID evidence.
            if (values.containsKey("VMEntityInstanceUUID")) {
                require(instance.equals(uuid(values.get("VMEntityInstanceUUID"))));
            }
            String disk = string(local.get("virtual_disk_key"));
            require(disk.matches("[1-9][0-9]{0,9}"));
            require(Long.parseLong(disk) <= Integer.MAX_VALUE);
            return new RuntimeFixture(vm, name, new RdmMappingIdentity(vc, instance, disk));
        } catch (RuntimeException invalid) {
            // No payload, field value, parser location, or nested exception escapes.
            throw new IllegalArgumentException("Invalid runtime fixture configuration");
        }
    }

    private static Map<String, Object> object(Object value, String... fields) {
        require(value instanceof Map);
        Map<?, ?> source = (Map<?, ?>) value;
        require(source.keySet().equals(Set.of(fields)));
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : fields) result.put(key, source.get(key));
        return result;
    }

    private static String string(Object value) {
        require(value instanceof String);
        String result = (String) value;
        require(!result.isBlank() && result.length() <= 4096);
        for (int i = 0; i < result.length(); i++) {
            char current = result.charAt(i);
            require(!Character.isISOControl(current));
            if (Character.isHighSurrogate(current)) {
                require(i + 1 < result.length() && Character.isLowSurrogate(result.charAt(++i)));
            } else require(!Character.isLowSurrogate(current));
        }
        return result;
    }

    private static String uuid(Object value) {
        String result = string(value);
        require(result.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
        require(!UUID.fromString(result).equals(new UUID(0, 0)));
        return result;
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid fixture");
    }

    /** Strict JSON subset: schema permits only objects, arrays, strings and booleans.
     * Rejects numbers/null rather than coercing them; limits precede recursion. */
    private static final class Parser {
        private final String text;
        private int position;
        private int items;

        Parser(String text) {
            require(text != null && !text.isBlank() && text.length() <= 32768);
            this.text = text;
        }

        Object parse() {
            Object result = value(0);
            whitespace();
            require(position == text.length());
            return result;
        }

        private Object value(int depth) {
            require(depth <= 12 && ++items <= 512);
            whitespace();
            require(position < text.length());
            char next = text.charAt(position);
            if (next == '"') return quoted();
            if (next == '{') {
                position++;
                Map<String, Object> result = new LinkedHashMap<>();
                whitespace();
                if (consume('}')) return result;
                do {
                    whitespace();
                    String key = quoted();
                    whitespace();
                    require(consume(':') && !result.containsKey(key));
                    result.put(key, value(depth + 1));
                    whitespace();
                    if (consume('}')) return result;
                    require(consume(','));
                } while (true);
            }
            if (next == '[') {
                position++;
                List<Object> result = new ArrayList<>();
                whitespace();
                if (consume(']')) return result;
                do {
                    result.add(value(depth + 1));
                    whitespace();
                    if (consume(']')) return result;
                    require(consume(','));
                } while (true);
            }
            if (text.startsWith("true", position)) { position += 4; return true; }
            if (text.startsWith("false", position)) { position += 5; return false; }
            throw new IllegalArgumentException("Invalid JSON token");
        }

        private String quoted() {
            require(consume('"'));
            StringBuilder result = new StringBuilder();
            while (position < text.length()) {
                char current = text.charAt(position++);
                if (current == '"') return result.toString();
                require(current >= 0x20 && result.length() < 4096);
                if (current == '\\') {
                    require(position < text.length());
                    char escaped = text.charAt(position++);
                    switch (escaped) {
                        case '"': case '\\': case '/': current = escaped; break;
                        case 'b': current = '\b'; break;
                        case 'f': current = '\f'; break;
                        case 'n': current = '\n'; break;
                        case 'r': current = '\r'; break;
                        case 't': current = '\t'; break;
                        case 'u':
                            require(position + 4 <= text.length());
                            String hex = text.substring(position, position + 4);
                            require(hex.matches("[0-9a-fA-F]{4}"));
                            current = (char) Integer.parseInt(hex, 16);
                            position += 4;
                            break;
                        default: throw new IllegalArgumentException("Invalid JSON escape");
                    }
                }
                result.append(current);
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private boolean consume(char expected) {
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void whitespace() {
            while (position < text.length() && " \t\r\n".indexOf(text.charAt(position)) >= 0) position++;
        }
    }
}
