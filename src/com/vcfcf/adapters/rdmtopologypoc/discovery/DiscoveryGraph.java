package com.vcfcf.adapters.rdmtopologypoc.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable, publication-free snapshot. No mutable platform SDK ResourceKey escapes. */
public final class DiscoveryGraph {
    public static final class Identifier {
        public final String name,value; public final boolean unique;
        public Identifier(String name,String value,boolean unique) throws DiscoveryException {
            this.name=SecureSoap.nonblank(name); this.unique=unique;
            if(value==null || (unique&&value.isEmpty())) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
            if(!value.isEmpty()) SecureSoap.nonblank(value); this.value=value;
        }
        @Override public String toString() { return "Identifier[redacted]"; }
    }
    public static final class VmKey {
        public final String displayName; public final List<Identifier> identifiers;
        public VmKey(String adapter,String kind,String displayName,List<Identifier> identifiers) throws DiscoveryException {
            if(!"VMWARE".equals(adapter)||!"VirtualMachine".equals(kind)||identifiers==null||identifiers.isEmpty()||identifiers.size()>64) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
            this.displayName=SecureSoap.nonblank(displayName); Set<String> names=new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for(Identifier id:identifiers) if(id==null||!names.add(id.name)) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
            this.identifiers=List.copyOf(identifiers); uuid(scoped("VMEntityVCID"));
            if(!scoped("VMEntityObjectID").matches("vm-[1-9][0-9]{0,18}")) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
        }
        public String scoped(String name) throws DiscoveryException {
            for(Identifier id:identifiers) if(name.equals(id.name)&&id.unique&&!id.value.isEmpty()) return id.value;
            throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
        }
        boolean sameUnique(VmKey other) {
            java.util.SortedMap<String,String> a=new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER),b=new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for(Identifier id:identifiers) if(id.unique)a.put(id.name,id.value);
            for(Identifier id:other.identifiers) if(id.unique)b.put(id.name,id.value);
            return a.equals(b)&&b.equals(a);
        }
        @Override public String toString() { return "VmKey[redacted]"; }
    }
    /** Production Suite API credentials/RBAC remain explicitly outside this boundary. */
    public interface VmResolver { VmKey resolve(String vcenterInstanceUuid,String moid) throws DiscoveryException; }
    static String uuid(String value) throws DiscoveryException {
        SecureSoap.nonblank(value);
        if(!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")||java.util.UUID.fromString(value).equals(new java.util.UUID(0,0))) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
        return value;
    }
    public static final class OfflineResolver implements VmResolver {
        private final List<VmKey> keys;
        public OfflineResolver(List<VmKey> completeCandidates,boolean completeSnapshot) throws DiscoveryException {
            if(!completeSnapshot || completeCandidates==null || completeCandidates.size()>20000) throw new DiscoveryException(DiscoveryException.Code.INCOMPLETE);
            this.keys=List.copyOf(completeCandidates);
        }
        @Override public VmKey resolve(String vc,String moid) throws DiscoveryException {
            SecureSoap.nonblank(vc); SecureSoap.nonblank(moid); VmKey found=null;
            for(VmKey key:keys) if(vc.equals(key.scoped("VMEntityVCID"))&&moid.equals(key.scoped("VMEntityObjectID"))) {
                if(found!=null) throw new DiscoveryException(DiscoveryException.Code.AMBIGUOUS); found=key;
            }
            if(found==null) throw new DiscoveryException(DiscoveryException.Code.IDENTITY); return found;
        }
    }
    public static final class Descriptor {
        public final String namespace; public final int namespaceId; private final byte[] bytes;
        public Descriptor(String namespace,int namespaceId,byte[] bytes) throws DiscoveryException {
            this.namespace=SecureSoap.nonblank(namespace);
            if(namespaceId< -128||namespaceId>255||bytes==null||bytes.length>259) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
            this.namespaceId=namespaceId; this.bytes=bytes.clone();
        }
        public byte[] bytes() { return bytes.clone(); }
        @Override public String toString() { return "Descriptor[redacted]"; }
    }
    public static final class Lun {
        public final String token,canonicalName; public final Map<String,String> properties;
        private Lun(String payload,Map<String,String> properties) { this.token="scsi-vpd83:naa:"+payload; this.canonicalName="naa."+payload; this.properties=Map.copyOf(properties); }
        @Override public String toString() { return "Lun[redacted]"; }
    }
    public static Lun qualify(String backingUuid,String scsiUuid,String canonical,List<Descriptor> descriptors,Map<String,String> descriptions) throws DiscoveryException {
        if(!SecureSoap.nonblank(backingUuid).equals(SecureSoap.nonblank(scsiUuid))) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
        String payload=naa(canonical); if(descriptors==null||descriptors.isEmpty()||descriptors.size()>16) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
        Set<String> eligible=new HashSet<>();
        for(Descriptor descriptor:descriptors) {
            // Unrelated vendor namespaces are not LU identity claims. NAA claims are validated completely.
            if(!descriptor.namespace.equalsIgnoreCase("naa")) continue;
            byte[] b=descriptor.bytes; if(b.length<4) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
            int a=b[0]&255, flags=b[1]&255, assoc=(flags>>4)&3;
            if(a!=1||(flags&0xc0)!=0||(flags&15)!=3||b[2]!=0||b.length!=4+(b[3]&255)||assoc==3) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
            StringBuilder hex=new StringBuilder(); for(int i=4;i<b.length;i++) hex.append(String.format(Locale.ROOT,"%02x",b[i]&255));
            String candidate=naa("naa."+hex); if(assoc==0) eligible.add(candidate);
        }
        if(eligible.size()!=1||!eligible.contains(payload)) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
        Map<String,String> props=new LinkedHashMap<>();
        Set<String> allowed=Set.of("vendor","model","revision","serial_number","lun_type","device_type","capacity_bytes");
        if(descriptions==null||!allowed.containsAll(descriptions.keySet())) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
        for(Map.Entry<String,String> entry:descriptions.entrySet()) props.put(entry.getKey(),SecureSoap.nonblank(entry.getValue()));
        if(props.containsKey("capacity_bytes")) try {String n=props.get("capacity_bytes");if(!n.matches("[1-9][0-9]{0,18}")||Long.parseLong(n)<=0) throw new NumberFormatException();} catch(NumberFormatException ex){throw new DiscoveryException(DiscoveryException.Code.IDENTITY);}
        props.put("provenance","vsphere-soap-intrinsic"); props.put("assessment","qualified-intrinsic-single-authority");
        return new Lun(payload,props);
    }
    static String naa(String canonical) throws DiscoveryException {
        SecureSoap.nonblank(canonical); if(!canonical.regionMatches(true,0,"naa.",0,4)) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
        String payload=canonical.substring(4).toLowerCase(Locale.ROOT);
        if(!payload.matches("5[0-9a-f]{15}|6[0-9a-f]{31}")) throw new DiscoveryException(DiscoveryException.Code.IDENTITY); return payload;
    }
    public static final class Attachment {
        public final VmKey vm; public final String vcenterInstanceUuid,vmInstanceUuid,diskKey,currentHost;
        public final Lun lun; public final DiscoveryException.Code unresolved;
        public Attachment(VmKey vm,String vc,String instance,String key,String host,Lun lun,DiscoveryException.Code unresolved) throws DiscoveryException {
            this.vm=vm; this.vcenterInstanceUuid=uuid(vc);this.vmInstanceUuid=uuid(instance);
            if(!key.matches("[1-9][0-9]{0,9}")||Long.parseLong(key)>Integer.MAX_VALUE) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
            this.diskKey=key;this.currentHost=SecureSoap.nonblank(host);this.lun=lun;this.unresolved=unresolved;
            if(vm==null || (lun==null)==(unresolved==null)) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
        }
        /** Same tuple/order as existing RdmMappingIdentity, not a replacement SDK key. */
        public List<String> mappingIdentity() { return List.of(vcenterInstanceUuid,vmInstanceUuid,diskKey); }
        @Override public String toString() { return "Attachment[redacted]"; }
    }
    public final List<Attachment> attachments; public final Map<String,Lun> luns;
    public final List<DiscoveryException.Code> diagnostics; public final boolean complete;
    public DiscoveryGraph(List<Attachment> attachments,List<DiscoveryException.Code> diagnostics) throws DiscoveryException {
        if(attachments.size()>20000) throw new DiscoveryException(DiscoveryException.Code.LIMIT);
        Map<String,Lun> unique=new LinkedHashMap<>();Set<List<String>> mappings=new HashSet<>();
        Map<List<String>,String> scopedInstances=new HashMap<>();Map<List<String>,List<String>> instanceScopes=new HashMap<>();
        Map<List<String>,VmKey> scopedKeys=new HashMap<>();
        Set<String> conflict=new HashSet<>();
        for(Attachment a:attachments) {
            List<String> scope=List.of(a.vcenterInstanceUuid,a.vm.scoped("VMEntityObjectID"));
            if(!a.vcenterInstanceUuid.equals(a.vm.scoped("VMEntityVCID"))) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
            String oldInstance=scopedInstances.putIfAbsent(scope,a.vmInstanceUuid);List<String> oldScope=instanceScopes.putIfAbsent(List.of(a.vcenterInstanceUuid,a.vmInstanceUuid),scope);
            VmKey prior=scopedKeys.putIfAbsent(scope,a.vm);if(prior!=null&&!prior.sameUnique(a.vm)) throw new DiscoveryException(DiscoveryException.Code.AMBIGUOUS);
            if(oldInstance!=null&&!oldInstance.equals(a.vmInstanceUuid)||oldScope!=null&&!oldScope.equals(scope)) throw new DiscoveryException(DiscoveryException.Code.AMBIGUOUS);
            for(Identifier id:a.vm.identifiers) if(id.name.equals("VMEntityInstanceUUID")&&!id.value.equals(a.vmInstanceUuid)) throw new DiscoveryException(DiscoveryException.Code.IDENTITY);
            if(!mappings.add(a.mappingIdentity())) throw new DiscoveryException(DiscoveryException.Code.AMBIGUOUS);
            if(a.lun!=null) { Lun old=unique.putIfAbsent(a.lun.token,a.lun); if(old!=null&&!old.properties.equals(a.lun.properties)) conflict.add(a.lun.token); }
        }
        List<Attachment> safe=new ArrayList<>();List<DiscoveryException.Code> codes=new ArrayList<>(diagnostics);
        for(Attachment a:attachments) if(a.lun!=null&&conflict.contains(a.lun.token)) {
            safe.add(new Attachment(a.vm,a.vcenterInstanceUuid,a.vmInstanceUuid,a.diskKey,a.currentHost,null,DiscoveryException.Code.AMBIGUOUS));
            codes.add(DiscoveryException.Code.AMBIGUOUS);
        } else safe.add(a);
        for(String token:conflict) unique.remove(token);
        this.attachments=List.copyOf(safe);this.luns=Collections.unmodifiableMap(unique);this.diagnostics=List.copyOf(codes);
        this.complete=codes.isEmpty()&&safe.stream().allMatch(a->a.lun!=null);
    }
    @Override public String toString() { return "DiscoveryGraph[attachments="+attachments.size()+",qualifiedLuns="+luns.size()+",complete="+complete+"]"; }
}
