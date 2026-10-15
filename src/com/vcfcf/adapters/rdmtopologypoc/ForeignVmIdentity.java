package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.ResourceKey;
import com.integrien.alive.common.adapter3.config.ResourceIdentifierConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.Set;

/**
 * An existing Operations VM resource key reconstructed from a Suite API
 * resource-key response. The POC preserves the identifier flags it receives;
 * it never marks identifiers unique based on local assumptions.
 */
public final class ForeignVmIdentity {

    public static final String ADAPTER_KIND = "VMWARE";
    public static final String RESOURCE_KIND = "VirtualMachine";
    public static final String VM_MOID = "VMEntityObjectID";
    public static final String VCENTER_INSTANCE_UUID = "VMEntityVCID";

    /** One unmodified identifier entry from the Suite API response. */
    public static final class Identifier {
        private final String name;
        private final String value;
        private final boolean partOfUniqueness;

        public Identifier(String name, String value, boolean partOfUniqueness) {
            this.name = require("identifier name", name);
            this.value = !partOfUniqueness && "".equals(value)
                    ? value : require("identifier value", value);
            this.partOfUniqueness = partOfUniqueness;
        }

        public String name() { return name; }
        public String value() { return value; }
        public boolean partOfUniqueness() { return partOfUniqueness; }
    }

    private final List<Identifier> identifiers;

    public ForeignVmIdentity(Collection<Identifier> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            throw new IllegalArgumentException("Suite API identifiers are required");
        }
        this.identifiers = Collections.unmodifiableList(new ArrayList<>(identifiers));
        // SDK ResourceKey rejects case-insensitive duplicate names.
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Identifier identifier : this.identifiers) {
            if (identifier == null || !names.add(identifier.name())) {
                throw new IllegalArgumentException("Duplicate or null foreign identifier");
            }
        }
        requireUnique(VM_MOID);
        requireUnique(VCENTER_INSTANCE_UUID);
    }

    public ResourceKey toResourceKey(String displayName) {
        ResourceKey key = new ResourceKey(require("displayName", displayName),
                RESOURCE_KIND, ADAPTER_KIND);
        for (Identifier identifier : identifiers) {
            if (!key.addIdentifier(new ResourceIdentifierConfig(identifier.name(),
                    identifier.value(), identifier.partOfUniqueness()))) {
                throw new IllegalArgumentException("Foreign identifier cannot be preserved by SDK");
            }
        }
        return key;
    }

    public List<Identifier> identifiers() {
        return identifiers;
    }

    private void requireUnique(String identifierName) {
        for (Identifier identifier : identifiers) {
            if (identifierName.equals(identifier.name())
                    && identifier.partOfUniqueness()) {
                return;
            }
        }
        throw new IllegalArgumentException(identifierName
                + " must be supplied by the Suite API as uniqueness-bearing");
    }

    private static String require(String field, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException(field + " contains control characters");
            }
        }
        return value;
    }
}
