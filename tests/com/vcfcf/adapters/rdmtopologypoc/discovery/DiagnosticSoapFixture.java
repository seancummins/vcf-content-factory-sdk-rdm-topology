package com.vcfcf.adapters.rdmtopologypoc.discovery;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Direct-MOR synthetic replies, strict observed request-scope assertions. */
public final class DiagnosticSoapFixture implements VimDiscoveryClient.Transport {
    public String mutation="";public final List<String> calls=new ArrayList<>();
    public static byte[] reply(String op,String content){return ("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns=\"urn:vim25\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><s:Body><"+op+"Response>"+content+"</"+op+"Response></s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8);}
    @Override public VimDiscoveryClient.Reply exchange(String op,byte[] request,String cookie)throws DiscoveryException{
        byte[] bytes=response(op,new String(request,StandardCharsets.UTF_8));return new VimDiscoveryClient.Reply(bytes,op.equals("Login")?"vmware_soap_session=synthetic":null);
    }
    public byte[] response(String op,String request)throws DiscoveryException{
        calls.add(op);String value="";
        if(op.equals("CreateContainerView")||op.equals("DestroyView"))throw new AssertionError("Diagnostic must never enumerate VM container view");
        if(op.equals("RetrieveServiceContent"))value="<returnval><about><instanceUuid>11111111-1111-1111-1111-111111111111</instanceUuid><version>8.0</version></about><sessionManager type=\"SessionManager\">session</sessionManager><propertyCollector type=\"PropertyCollector\">collector</propertyCollector><rootFolder type=\"Folder\">root</rootFolder><viewManager type=\"ViewManager\">views</viewManager></returnval>";
        else if(op.equals("Login"))value="<returnval><key>synthetic</key></returnval>";
        else if(op.equals("RetrievePropertiesEx")){
            if(request.contains("ContainerView")||request.contains("TraversalSpec"))throw new AssertionError("Unbounded discovery forbidden");
            boolean host=request.contains("<type>HostSystem</type>");String props;
            if(host){
                if(!request.contains("<obj type=\"HostSystem\">host-a</obj>"))throw new AssertionError("Wrong host scope");
                StringBuilder data=new StringBuilder();for(byte b:new byte[]{1,3,0,8,0x50,0,0,0,0,0,0,1})data.append("<data>").append(b).append("</data>");
                String lun="<ScsiLun xsi:type=\"HostScsiDisk\"><uuid>opaque</uuid><canonicalName>naa.5000000000000001</canonicalName><durableName><namespace>NAA</namespace><namespaceId>-1</namespaceId>"+data+"</durableName><vendor>Synthetic</vendor><model>Fixture</model><capacity><block>100</block><blockSize>512</blockSize></capacity></ScsiLun>";
                if(mutation.equals("wrong-join"))lun=lun.replace("<uuid>opaque</uuid>","<uuid>other</uuid>");
                if(mutation.equals("naa-conflict"))lun=lun.replace("naa.5000000000000001","naa.5000000000000002");
                if(mutation.equals("duplicate-lun"))lun+=lun;
                if(mutation.equals("missing-lu-designator"))lun=lun.replace("<data>3</data>","<data>19</data>");
                props=prop("config.storageDevice.scsiLun","ArrayOfScsiLun",lun);
            }else{
                if(!request.contains("<obj type=\"VirtualMachine\">vm-71</obj>"))throw new AssertionError("Wrong VM scope");
                props=prop("config.instanceUuid",null,"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")+prop("config.changeVersion",null,"1")
                        +"<propSet><name>runtime.host</name><val type=\"HostSystem\" xsi:type=\"ManagedObjectReference\">host-a</val></propSet>";
                if(request.contains("config.hardware.device")){
                    String disk="<VirtualDevice xsi:type=\"VirtualDisk\"><key>2001</key><backing xsi:type=\"VirtualDiskRawDiskMappingVer1BackingInfo\"><lunUuid>opaque</lunUuid><deviceName>/synthetic</deviceName><fileName>synthetic.vmdk</fileName><compatibilityMode>physicalMode</compatibilityMode></backing></VirtualDevice>";
                    if(mutation.equals("no-rdm"))disk="";
                    if(mutation.equals("two-rdm"))disk+=disk.replace("2001","2002");
                    props+=prop("config.hardware.device","ArrayOfVirtualDevice",disk);
                }else if(mutation.equals("motion"))props=props.replace("<val>1</val>","<val>2</val>");
            }
            if(mutation.equals("missing-property"))props="";
            value="<returnval><objects><obj type=\""+(host?"HostSystem\">host-a":"VirtualMachine\">vm-71")+"</obj>"+props+"</objects></returnval>";
        }
        if(op.equals("Logout")&&mutation.equals("cleanup"))throw new DiscoveryException(DiscoveryException.Code.CONNECTION);
        if(mutation.equals("wrong-vcenter")&&op.equals("RetrieveServiceContent"))value=value.replace("11111111-1111-1111-1111-111111111111","22222222-2222-2222-2222-222222222222");
        return reply(op,value);
    }
    private static String prop(String name,String type,String value){return "<propSet><name>"+name+"</name><val"+(type==null?"":" xsi:type=\""+type+"\"")+">"+value+"</val></propSet>";}
}
