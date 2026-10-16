package com.vcfcf.adapters.rdmtopologypoc.discovery;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;
import static com.vcfcf.adapters.rdmtopologypoc.discovery.SecureSoap.*;

/** Inert bounded SOAP collector. It never invokes adapter collection/publication APIs. */
public final class VimDiscoveryClient {
    public interface Transport {
        Reply exchange(String operation,byte[] request,String cookie) throws DiscoveryException;
        default Reply exchange(String operation,byte[] request,String cookie,long remainingNanos) throws DiscoveryException {return exchange(operation,request,cookie);}
    }
    public static final class Reply {
        private final byte[] bytes; private final String cookie;
        public Reply(byte[] bytes,String cookie) { this.bytes=bytes.clone();this.cookie=cookie; }
        @Override public String toString() { return "SoapReply[redacted]"; }
    }
    private final Transport transport; private final DiscoveryGraph.VmResolver resolver;
    private final java.util.function.LongSupplier clock;
    private String cookie,manager,collector,view,pending; private int requests; private long deadline;
    private boolean used,cleaning; private int cleanupRequests;private long cleanupDeadline;
    private String targetVcenter, targetMoid;
    public VimDiscoveryClient(Transport transport,DiscoveryGraph.VmResolver resolver) throws DiscoveryException {
        this(transport,resolver,System::nanoTime);
    }
    /** Direct-MOR diagnostic scope: no ContainerView or unrelated VM/host traversal. */
    public VimDiscoveryClient(Transport transport,DiscoveryGraph.VmResolver resolver,String vcenter,String moid) throws DiscoveryException {
        this(transport,resolver);
        targetVcenter=DiscoveryGraph.uuid(vcenter);
        if(moid==null||!moid.matches("vm-[1-9][0-9]{0,18}")) fail(DiscoveryException.Code.CONFIGURATION);
        targetMoid=moid;
    }
    VimDiscoveryClient(Transport transport,DiscoveryGraph.VmResolver resolver,java.util.function.LongSupplier clock) throws DiscoveryException {
        if(transport==null||resolver==null) fail(DiscoveryException.Code.CONFIGURATION);this.transport=transport;this.resolver=resolver;
        this.clock=clock;
    }
    private Element call(String op,String content) throws DiscoveryException {
        if(!cleaning&&Thread.currentThread().isInterrupted()) fail(DiscoveryException.Code.CANCELLED);
        if(cleaning) {if(++cleanupRequests>3||clock.getAsLong()>cleanupDeadline) fail(DiscoveryException.Code.LIMIT);}
        else if(++requests>128 || clock.getAsLong()>deadline) fail(DiscoveryException.Code.LIMIT);
        Reply reply=transport.exchange(op,request(op,content),cookie,(cleaning?cleanupDeadline:deadline)-clock.getAsLong());
        if(op.equals("Login")&&reply.cookie!=null) cookie=reply.cookie;
        if(!cleaning&&Thread.currentThread().isInterrupted()) fail(DiscoveryException.Code.CANCELLED);
        if(clock.getAsLong()>(cleaning?cleanupDeadline:deadline)) fail(DiscoveryException.Code.LIMIT);
        Element result=response(reply.bytes,op);
        if(op.equals("Login")&&cookie==null) fail(DiscoveryException.Code.SESSION);
        return result;
    }
    private static String ref(String name,String type,String id) { return "<"+name+" type=\""+type+"\">"+escape(id)+"</"+name+">"; }
    public synchronized DiscoveryGraph discover(String username,char[] password) throws DiscoveryException {
        if(used) fail(DiscoveryException.Code.SESSION);used=true;deadline=clock.getAsLong()+120_000_000_000L;
        nonblank(username);if(password==null||password.length==0||password.length>4096) fail(DiscoveryException.Code.CONFIGURATION);
        List<DiscoveryException.Code> diagnostics=new ArrayList<>();
        try {
            Element service=one(call("RetrieveServiceContent",ref("_this","ServiceInstance","ServiceInstance")),"returnval");
            String vc=DiscoveryGraph.uuid(required(one(service,"about"),"instanceUuid"));required(one(service,"about"),"version");
            if(targetVcenter!=null&&!targetVcenter.equals(vc)) fail(DiscoveryException.Code.IDENTITY);
            manager=mor(one(service,"sessionManager"),"SessionManager");collector=mor(one(service,"propertyCollector"),"PropertyCollector");
            String root=mor(one(service,"rootFolder"),"Folder"),views=mor(one(service,"viewManager"),"ViewManager");
            Element login=call("Login",ref("_this","SessionManager",manager)+"<userName>"+escape(username)+"</userName><password>"+escape(new String(password))+"</password>");
            one(login,"returnval");
            if(targetMoid==null) view=mor(one(call("CreateContainerView",ref("_this","ViewManager",views)+ref("container","Folder",root)+"<type>VirtualMachine</type><recursive>true</recursive>"),"returnval"),"ContainerView");
            String[] paths={"config.instanceUuid","config.changeVersion","runtime.host","config.hardware.device"};
            Map<String,Map<String,Element>> vms=properties("VirtualMachine",paths,targetMoid==null?null:Set.of(targetMoid));
            Set<String> hosts=new LinkedHashSet<>();
            for(Map<String,Element> props:vms.values()) try {hosts.add(mor(props.get("runtime.host"),"HostSystem"));}catch(DiscoveryException ex){diagnostics.add(ex.code());}
            Map<String,Map<String,Element>> inventory=new LinkedHashMap<>();
            if(!hosts.isEmpty()) try {inventory=properties("HostSystem",new String[]{"config.storageDevice.scsiLun"},hosts);}
            catch(DiscoveryException ex) {diagnostics.add(ex.code());
                if(pending!=null) {String abandoned=pending;pending=null;try {call("CancelRetrievePropertiesEx",ref("_this","PropertyCollector",collector)+"<token>"+escape(abandoned)+"</token>");}catch(DiscoveryException secondary){diagnostics.add(secondary.code());}}
            }
            Map<String,Map<String,Element>> fence=vms.isEmpty()?Map.of():properties("VirtualMachine",new String[]{"config.instanceUuid","config.changeVersion","runtime.host"},vms.keySet());
            List<DiscoveryGraph.Attachment> graph=new ArrayList<>();
            Map<String,String> uuidTokens=new HashMap<>();Set<String> conflictingTokens=new HashSet<>();
            for(Map.Entry<String,Map<String,Element>> entry:vms.entrySet()) {
                List<DiscoveryGraph.Attachment> vmAttachments=new ArrayList<>();
                try {
                String moid=entry.getKey();Map<String,Element> p=entry.getValue(),f=fence.get(moid);
                String host=mor(p.get("runtime.host"),"HostSystem"),instance=DiscoveryGraph.uuid(text(p.get("config.instanceUuid")));
                String version=nonblank(text(p.get("config.changeVersion")));
                if(!instance.equals(DiscoveryGraph.uuid(text(f.get("config.instanceUuid"))))||!version.equals(nonblank(text(f.get("config.changeVersion"))))||!host.equals(mor(f.get("runtime.host"),"HostSystem"))) {diagnostics.add(DiscoveryException.Code.UNSTABLE);continue;}
                DiscoveryGraph.VmKey vm;
                try { vm=resolver.resolve(vc,moid); if(!vc.equals(vm.scoped("VMEntityVCID"))||!moid.equals(vm.scoped("VMEntityObjectID"))) fail(DiscoveryException.Code.IDENTITY); }
                catch(DiscoveryException ex) { diagnostics.add(ex.code());continue; }
                List<Element> disks=deviceArray(p.get("config.hardware.device"));Set<String> diskKeys=new HashSet<>();
                for(Element disk:disks) {
                    String deviceType=type(disk);
                    if(!deviceType.equals("VirtualDisk")) { if(Set.of("VirtualIDEController","VirtualAHCIController","VirtualLsiLogicController","VirtualLsiLogicSASController","VirtualBusLogicController","ParaVirtualSCSIController","VirtualNVMEController","VirtualSIOController","VirtualPCIController","VirtualPS2Controller","VirtualUSBController","VirtualUSBXHCIController","VirtualVMCIDevice","VirtualCdrom","VirtualFloppy","VirtualSerialPort","VirtualParallelPort","VirtualMachineVideoCard","VirtualMachineVMIROM","VirtualKeyboard","VirtualPointingDevice","VirtualE1000","VirtualE1000e","VirtualVmxnet","VirtualVmxnet2","VirtualVmxnet3","VirtualPCNet32","VirtualSriovEthernetCard","VirtualTPM","VirtualSoundBlaster16","VirtualEnsoniq1371","VirtualHdAudioCard","VirtualUSB").contains(deviceType)) continue;fail(DiscoveryException.Code.STRUCTURE); }
                    String key=required(disk,"key");if(!diskKeys.add(key)) fail(DiscoveryException.Code.AMBIGUOUS);
                    Element backing=one(disk,"backing");String backingType=type(backing);
                    if(!backingType.equals("VirtualDiskRawDiskMappingVer1BackingInfo")) {
                        if(Set.of("VirtualDiskFlatVer1BackingInfo","VirtualDiskFlatVer2BackingInfo","VirtualDiskSparseVer1BackingInfo","VirtualDiskSparseVer2BackingInfo","VirtualDiskSeSparseBackingInfo","VirtualDiskVVolBackingInfo","VirtualDiskLocalPMemBackingInfo").contains(backingType)) continue;
                        fail(DiscoveryException.Code.STRUCTURE);
                    }
                    String uuid=required(backing,"lunUuid");required(backing,"deviceName");required(backing,"fileName");
                    String compatibility=required(backing,"compatibilityMode");if(!Set.of("physicalMode","virtualMode").contains(compatibility)) fail(DiscoveryException.Code.STRUCTURE);
                    DiscoveryGraph.Lun lun=null;DiscoveryException.Code unresolved=null;
                    try {
                        if(!inventory.containsKey(host)) fail(DiscoveryException.Code.INCOMPLETE);
                        List<Element> matches=new ArrayList<>();Element array=inventory.get(host).get("config.storageDevice.scsiLun");
                        if(!type(array).equals("ArrayOfScsiLun")) fail(DiscoveryException.Code.STRUCTURE);
                        for(Element item:elements(array)) {
                            if(!is(item,VIM,"ScsiLun")||!Set.of("ScsiLun","HostScsiDisk").contains(type(item))) fail(DiscoveryException.Code.STRUCTURE);
                            if(required(item,"uuid").equals(uuid)) matches.add(item);
                        }
                        if(matches.size()!=1) fail(matches.isEmpty()?DiscoveryException.Code.LUN_UNRESOLVED:DiscoveryException.Code.AMBIGUOUS);
                        lun=decodeLun(uuid,matches.get(0));
                        String old=uuidTokens.putIfAbsent(uuid,lun.token);
                        if(old!=null&&!old.equals(lun.token)) { conflictingTokens.add(old);conflictingTokens.add(lun.token); }
                    } catch(DiscoveryException ex) { unresolved=ex.code();diagnostics.add(ex.code()); }
                    vmAttachments.add(new DiscoveryGraph.Attachment(vm,vc,instance,key,host,lun,unresolved));
                }
                graph.addAll(vmAttachments);
                } catch(DiscoveryException ex) {diagnostics.add(ex.code());}
            }
            if(!conflictingTokens.isEmpty()) {
                List<DiscoveryGraph.Attachment> safe=new ArrayList<>();
                for(DiscoveryGraph.Attachment a:graph) if(a.lun!=null&&conflictingTokens.contains(a.lun.token)) { safe.add(new DiscoveryGraph.Attachment(a.vm,vc,a.vmInstanceUuid,a.diskKey,a.currentHost,null,DiscoveryException.Code.AMBIGUOUS));diagnostics.add(DiscoveryException.Code.AMBIGUOUS); } else safe.add(a);
                graph=safe;
            }
            DiscoveryGraph result=new DiscoveryGraph(graph,diagnostics);
            // Cleanup is performed before immutable completion so its secondary status is visible.
            cleanup(diagnostics);
            return diagnostics.equals(result.diagnostics)?result:new DiscoveryGraph(graph,diagnostics);
        } finally { Arrays.fill(password,'\0');cleanup(diagnostics); }
    }
    private void cleanup(List<DiscoveryException.Code> diagnostics) {
        boolean interrupted=Thread.interrupted();
        try {
        if(!cleaning) {cleaning=true;cleanupDeadline=clock.getAsLong()+30_000_000_000L;}
        if(pending!=null&&collector!=null) {String token=pending;pending=null;try {call("CancelRetrievePropertiesEx",ref("_this","PropertyCollector",collector)+"<token>"+escape(token)+"</token>");} catch(DiscoveryException ex){diagnostics.add(ex.code());}}
        if(view!=null) {String v=view;view=null;try {call("DestroyView",ref("_this","ContainerView",v));} catch(DiscoveryException ex){diagnostics.add(ex.code());}}
        if(cookie!=null&&manager!=null) {try {call("Logout",ref("_this","SessionManager",manager));} catch(DiscoveryException ex){diagnostics.add(ex.code());} finally {cookie=null;}}
        } finally {if(interrupted)Thread.currentThread().interrupt();}
    }
    private Map<String,Map<String,Element>> properties(String kind,String[] paths,Set<String> objects) throws DiscoveryException {
        StringBuilder spec=new StringBuilder(ref("_this","PropertyCollector",collector)).append("<specSet><propSet><type>").append(kind).append("</type><all>false</all>");
        for(String path:paths) spec.append("<pathSet>").append(path).append("</pathSet>");spec.append("</propSet>");
        if(objects==null) spec.append("<objectSet>").append(ref("obj","ContainerView",view)).append("<skip>true</skip><selectSet xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"TraversalSpec\"><name>viewWalk</name><type>ContainerView</type><path>view</path><skip>false</skip></selectSet></objectSet>");
        else for(String id:objects) spec.append("<objectSet>").append(ref("obj",kind,id)).append("<skip>false</skip></objectSet>");
        spec.append("</specSet><options><maxObjects>64</maxObjects></options>");
        Element page=call("RetrievePropertiesEx",spec.toString());Set<String> tokens=new HashSet<>();Map<String,Map<String,Element>> out=new LinkedHashMap<>();int pages=0;
        while(true) {
            if(++pages>64) fail(DiscoveryException.Code.LIMIT);List<Element> returns=children(page,"returnval");
            if(returns.size()!=1) fail(DiscoveryException.Code.INCOMPLETE);Element result=returns.get(0);
            List<Element> next=children(result,"token");if(next.size()>1) fail(DiscoveryException.Code.STRUCTURE);
            String nextToken=next.isEmpty()?null:nonblank(text(next.get(0)));
            if(nextToken!=null) {pending=nextToken;if(!tokens.add(nextToken)) fail(DiscoveryException.Code.AMBIGUOUS);}
            for(Element item:elements(result)) {
                if(is(item,VIM,"token")) continue;
                if(!is(item,VIM,"objects")) fail(DiscoveryException.Code.STRUCTURE);
                String id=mor(one(item,"obj"),kind);if(out.containsKey(id)||objects!=null&&!objects.contains(id)) fail(DiscoveryException.Code.AMBIGUOUS);
                Map<String,Element> props=new LinkedHashMap<>();
                for(Element part:elements(item)) {
                    if(is(part,VIM,"obj")) continue;
                    if(is(part,VIM,"missingSet")) fail(DiscoveryException.Code.INCOMPLETE);
                    if(!is(part,VIM,"propSet")) fail(DiscoveryException.Code.STRUCTURE);
                    String name=required(part,"name");if(!Arrays.asList(paths).contains(name)||props.putIfAbsent(name,one(part,"val"))!=null) fail(DiscoveryException.Code.STRUCTURE);
                }
                if(props.size()!=paths.length) fail(DiscoveryException.Code.INCOMPLETE);out.put(id,props);if(out.size()>10000) fail(DiscoveryException.Code.LIMIT);
            }
            if(nextToken==null) {pending=null;break;}
            page=call("ContinueRetrievePropertiesEx",ref("_this","PropertyCollector",collector)+"<token>"+escape(pending)+"</token>");
        }
        if(objects!=null&&!out.keySet().equals(objects)) fail(DiscoveryException.Code.INCOMPLETE);return out;
    }
    private static List<Element> deviceArray(Element array) throws DiscoveryException {
        if(!type(array).equals("ArrayOfVirtualDevice")) fail(DiscoveryException.Code.STRUCTURE);
        List<Element> devices=elements(array);if(devices.size()>1024) fail(DiscoveryException.Code.LIMIT);
        for(Element device:devices) if(!is(device,VIM,"VirtualDevice")) fail(DiscoveryException.Code.STRUCTURE);return devices;
    }
    private static DiscoveryGraph.Lun decodeLun(String backingUuid,Element lun) throws DiscoveryException {
        List<DiscoveryGraph.Descriptor> descriptors=new ArrayList<>();
        for(Element field:elements(lun)) if(is(field,VIM,"durableName")||is(field,VIM,"alternateName")) {
            for(Element part:elements(field)) if(!Set.of("namespace","namespaceId","data").contains(part.getLocalName())) fail(DiscoveryException.Code.STRUCTURE);
            String ns=required(field,"namespace"),number=required(field,"namespaceId");int id;
            try {id=Integer.parseInt(number);} catch(Exception ex){throw new DiscoveryException(DiscoveryException.Code.IDENTITY);}
            List<Element> data=children(field,"data");if(data.isEmpty()||data.size()>259) fail(DiscoveryException.Code.IDENTITY);
            byte[] bytes=new byte[data.size()];
            try {for(int i=0;i<data.size();i++) {String numberByte=text(data.get(i));if(!numberByte.matches("-?(0|[1-9][0-9]{0,2})")) fail(DiscoveryException.Code.IDENTITY);int n=Integer.parseInt(numberByte);if(n< -128||n>127) fail(DiscoveryException.Code.IDENTITY);bytes[i]=(byte)n;}}
            catch(NumberFormatException ex) {throw new DiscoveryException(DiscoveryException.Code.IDENTITY);}
            descriptors.add(new DiscoveryGraph.Descriptor(ns,id,bytes));
        }
        Map<String,String> descriptions=new LinkedHashMap<>();String[][] fields={{"vendor","vendor"},{"model","model"},{"revision","revision"},{"serialNumber","serial_number"},{"lunType","lun_type"},{"deviceType","device_type"}};
        for(String[] field:fields) {List<Element> values=children(lun,field[0]);if(values.size()>1) fail(DiscoveryException.Code.STRUCTURE);if(!values.isEmpty()) {String value=text(values.get(0));if(!value.isEmpty()) descriptions.put(field[1],nonblank(value));}}
        List<Element> capacity=children(lun,"capacity");if(capacity.size()>1) fail(DiscoveryException.Code.STRUCTURE);
        if(!capacity.isEmpty()) {
            try {String blocks=required(capacity.get(0),"block"),size=required(capacity.get(0),"blockSize");if(!blocks.matches("[1-9][0-9]{0,18}")||!size.matches("[1-9][0-9]{0,18}")) fail(DiscoveryException.Code.IDENTITY);descriptions.put("capacity_bytes",Long.toString(Math.multiplyExact(Long.parseLong(blocks),Long.parseLong(size))));}
            catch(ArithmeticException|NumberFormatException ex){throw new DiscoveryException(DiscoveryException.Code.IDENTITY);}
        }
        return DiscoveryGraph.qualify(backingUuid,required(lun,"uuid"),required(lun,"canonicalName"),descriptors,descriptions);
    }
}
