package com.vcfcf.adapters.rdmtopologypoc;

import com.integrien.alive.common.adapter3.*;
import com.integrien.alive.common.adapter3.config.*;
import java.util.*;

/** Actual platform lifecycle readiness, deliberately separate from deterministic delegate seam tests. */
public final class FixtureLifecycleProbe {
    private static int checks;
    private static java.nio.file.Path descriptor;
    private static String stage="CONSTRUCT";
    private static String rule="NONE";
    private static String scenario="NONE";
    private static void check(boolean value){if(!value)throw new AssertionError();checks++;}
    private static final String NAA="600000000000000000000000000000ab";
    private static String fixture(boolean lun){
        if(!lun)return RuntimeFixtureTest.JSON;
        String attachment=RuntimeFixtureTest.JSON.substring(0,RuntimeFixtureTest.JSON.length()-1)
                +",\"canonical_lun\":{\"provenance\":\"vsphere-soap-fixture\","
                +"\"backing_lun_uuid\":\"synthetic-scsi\",\"scsi_lun_uuid\":\"synthetic-scsi\","
                +"\"canonical_name\":\"naa."+NAA+"\",\"storage_reference\":\"0x"+NAA+"\","
                +"\"descriptors\":[{\"namespace\":\"naa\",\"namespace_id\":\"1\","
                +"\"data_hex\":\"01030010"+NAA+"\"}],\"properties\":{\"vendor\":\"synthetic vendor\","
                +"\"model\":\"synthetic model\",\"capacity_blocks\":\"1024\",\"capacity_block_size\":\"512\"}}}";
        return "{\"schema_version\":\"2\",\"attachments\":["+attachment+"]}";
    }
    private static AdapterConfig config(String json,int mode){
        AdapterConfig c=DispatcherTest.fixture();DispatcherTest.set(c,"fixture_json",json);
        c.getAdapterInstResource().setResourceIdUuid(17,UUID.fromString("11111111-1111-1111-1111-111111111111"));
        c.getAdapterInstResource().setInterval(300);if(mode==1)DispatcherTest.add(c,"execution_mode","fixture");
        if(mode==2)try{E493ColdStartTest.synchronizeWithDescriptor(c,descriptor);}catch(Exception ex){throw new IllegalStateException("SDK test template unavailable");}
        RuntimeFixture f=RuntimeFixture.parse(json);
        List<ResourceKey> keys=new ArrayList<>();keys.add(f.mapping.toResourceKey(RdmTopologyDispatcher.KIND));
        if(f.lun!=null)keys.add(f.lun.toResourceKey(RdmTopologyDispatcher.KIND));
        int id=101;AttributeConfigs policies=new AttributeConfigs();
        for(ResourceKey key:keys){
            ResourceConfig r=new ResourceConfig(key,id++,UUID.nameUUIDFromBytes(key.getResourceKind().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            r.setInterval(300);r.setToBeMonitored(true);
            // AdapterBase filters collected data using AdapterConfig policy carriers,
            // not ResourceConfig's attribute list alone (actual AdapterCache contract).
            AttributePolicyConfigs policy=new AttributePolicyConfigs(UUID.nameUUIDFromBytes("synthetic-policy".getBytes(java.nio.charset.StandardCharsets.UTF_8)),1L,RdmTopologyDispatcher.KIND,key.getResourceKind());
            if(key.getResourceKind().equals(CanonicalLunIdentity.KIND))for(String property:f.lun.properties.keySet())policy.addAttribute(new MetricKey(true,property),true,null,false);
            policies.addPolicyConfig(r.getPolicyResourceKindKey(),policy);
            c.addMonitoringResource(r);
        }
        c.addAttributeConfigs(policies);
        return c;
    }
    private static String key(ResourceKey k){
        StringBuilder b=new StringBuilder(k.getAdapterKind()).append('/').append(k.getResourceKind()).append('/').append(k.getResourceName());
        for(ResourceIdentifierConfig id:k.getIdentifiers())b.append('|').append(id.getKey()).append('=').append(id.getValue()).append(':').append(id.isPartOfUniqueness());
        return b.toString();
    }
    private static List<String> discovered(DiscoveryResult d){
        check(d!=null);List<String> rows=new ArrayList<>();
        for(DiscoveryResult.DiscoveryResultItem i:d.getDiscoveryMap().values())for(Set<ResourceConfig> rs:i.getResources().values())for(ResourceConfig r:rs)rows.add(key(r.getResourceKey()));
        Collections.sort(rows);return rows;
    }
    private static List<String> snapshot(CollectResult r,RuntimeFixture f,boolean lun,boolean initial){
        check(r!=null);List<String> rows=discovered(r.getDiscoveryResult());
        check(rows.size()==(lun?2:1));
        Relationships rel=r.getDiscoveryResult().getRelationships();check(rel!=null);
        int edges=0;
        for(Relationships.RelationshipItem i:rel.getRelationshipItems()){
            check(i.isAdd()&&!i.isClearFirst());check(i.getChildKindsToClear()==null||i.getChildKindsToClear().isEmpty());
            for(ResourceKey child:i.getChildren()){
                check(child.getAdapterKind().equals(RdmTopologyDispatcher.KIND));
                if(i.getParent().getResourceKind().equals("VirtualMachine")){
                    check(key(i.getParent()).equals(key(f.vm.toResourceKey(f.vmName))));
                    check(key(child).equals(key(f.mapping.toResourceKey(RdmTopologyDispatcher.KIND))));
                }else{check(lun);check(key(i.getParent()).equals(key(f.mapping.toResourceKey(RdmTopologyDispatcher.KIND))));check(key(child).equals(key(f.lun.toResourceKey(RdmTopologyDispatcher.KIND))));}
                rows.add("edge:"+key(i.getParent())+">"+key(child));edges++;
            }
        }
        check(edges==(lun?2:1));
        rule="PROPERTY_PAYLOAD";Map<String,String> properties=new TreeMap<>();
        for(ResourceCollectResult target:r.getResourceCollectResultMap().values()){
            // SDK-generated adapter health/collection timing is not topology fixture output.
            if(lun&&Integer.valueOf(102).equals(target.getResourceId())&&target.getPropertyChanges()!=null)
                for(MetricData p:target.getPropertyChanges().getPropertyData().values()){
                    check(p.getMetricKey().isProperty());properties.put(p.getMetricKey().getAttribute(),p.getStringValue());
                }
        }
        // AdapterCache.updatePropertyChanges deliberately suppresses unchanged
        // properties until its resend window, including after same-config refresh;
        // topology remains complete each cycle. Require every initial LUN value.
        rule="PROPERTY_SET_COUNT_"+properties.size();
        check(properties.equals(lun?f.lun.properties:Collections.emptyMap())||!initial&&properties.isEmpty());rule="NONE";
        if(lun&&initial){check(properties.size()==7);check("524288".equals(properties.get("storage|capacity_bytes")));}
        Collections.sort(rows);return rows;
    }
    private static AdapterBase actualFixture(AdapterInterface3 adapter)throws ReflectiveOperationException{
        if(adapter instanceof RdmTopologyDispatcher){java.lang.reflect.Field field=RdmTopologyDispatcher.class.getDeclaredField("fixture");field.setAccessible(true);adapter=(AdapterInterface3)field.get(adapter);}
        return (AdapterBase)adapter;
    }
    private static AdapterConfig delta(AdapterConfig full,boolean nameOnly){
        AdapterConfig delta=new AdapterConfig(RdmTopologyDispatcher.KIND,17);
        if(nameOnly)delta.setAdapterInstResourceId(full.getAdapterInstResourceId(),full.getAdapterInstResourceName());
        else delta.setAdapterInstResourceId(full.getAdapterInstResourceId(),full.getAdapterInstResourceUuid(),full.getAdapterInstResourceKey());
        check(delta.getAdapterInstResource()==null);return delta;
    }
    private static void partialLifecycle(AdapterInterface3 adapter,AdapterConfig full,RuntimeFixture f,boolean lun,List<String> expected)throws ReflectiveOperationException{
        AdapterBase actual=actualFixture(adapter);
        for(boolean nameOnly:List.of(false,true)){
            stage="PARTIAL_MONITORING_UPDATE";AdapterConfig update=delta(full,nameOnly);
            update.addMonitoringResources(full.getMonitoringResources());update.addAttributeConfigs(full.getAttributeConfigs());
            check(adapter.configure(update).getStatus()!=AdapterStatus.Status.as_failed);
            check(actual.getAdapterInstResource()==full.getAdapterInstResource());
            check(actual.getAllMonitoringResources().size()==(lun?2:1));
            check(expected.equals(snapshot(adapter.collect(),f,lun,false)));
        }
        ResourceConfig mapping=full.getMonitoringResources().stream().filter(r->Integer.valueOf(101).equals(r.getResourceId())).findFirst().orElseThrow();
        stage="PARTIAL_STOP_RESOURCE";AdapterConfig stop=delta(full,false);stop.addStoppedResource(mapping);
        check(adapter.configure(stop).getStatus()!=AdapterStatus.Status.as_failed);
        check(actual.getMonitoringResource(101)==null);check(actual.getStoppedResource(101)!=null);
        stage="PARTIAL_REMOVE_RESOURCE";AdapterConfig remove=delta(full,false);remove.addRemovedResource(mapping);
        check(adapter.configure(remove).getStatus()!=AdapterStatus.Status.as_failed);
        check(actual.getMonitoringResource(101)==null);check(actual.getStoppedResource(101)==null);
        stage="PARTIAL_RESTORE_RESOURCE";AdapterConfig restore=delta(full,false);restore.addMonitoringResource(mapping);
        check(adapter.configure(restore).getStatus()!=AdapterStatus.Status.as_failed);check(actual.getMonitoringResource(101)!=null);
        check(expected.equals(snapshot(adapter.collect(),f,lun,false)));
        if(lun){
            stage="PARTIAL_ATTRIBUTE_POLICY";ResourceConfig target=actual.getMonitoringResource(102);
            AttributePolicyConfigs policy=new AttributePolicyConfigs(UUID.nameUUIDFromBytes("synthetic-policy-disabled".getBytes(java.nio.charset.StandardCharsets.UTF_8)),2L,RdmTopologyDispatcher.KIND,CanonicalLunIdentity.KIND);
            for(String property:f.lun.properties.keySet())policy.addAttribute(new MetricKey(true,property),false,null,false);
            AttributeConfigs disabled=new AttributeConfigs();disabled.addPolicyConfig(target.getPolicyResourceKindKey(),policy);
            AdapterConfig update=delta(full,false);update.addAttributeConfigs(disabled);
            check(adapter.configure(update).getStatus()!=AdapterStatus.Status.as_failed);
            MetricKey property=new MetricKey(true,"storage|capacity_bytes");check(!actual.shouldCollect(target,property));
            check(expected.equals(snapshot(adapter.collect(),f,lun,false)));
            stage="PARTIAL_ATTRIBUTE_POLICY_RESTORE";update=delta(full,false);update.addAttributeConfigs(full.getAttributeConfigs());
            check(adapter.configure(update).getStatus()!=AdapterStatus.Status.as_failed);check(actual.shouldCollect(target,property));
            check(expected.equals(snapshot(adapter.collect(),f,lun,false)));
        }
        check(actual.getAdapterInstResource()==full.getAdapterInstResource());
    }
    private static List<String> lifecycle(AdapterInterface3 adapter,int mode,boolean lun){
        AdapterConfig c=config(fixture(lun),mode);RuntimeFixture f=RuntimeFixture.parse(fixture(lun));
        try{
            stage="CONFIGURE";AdapterStatus s=adapter.configure(c);check(s!=null&&s.getStatus()!=AdapterStatus.Status.as_failed);
            if(scenario.endsWith("COLLECTOR")){
                AdapterInterface3 actual=actualFixture(adapter);
                java.lang.reflect.Field factory=AdapterBase.class.getDeclaredField("adapterLoggerFactory");factory.setAccessible(true);Object logger=factory.get(actual);
                java.lang.reflect.Field id=logger.getClass().getDeclaredField("instanceId");id.setAccessible(true);check(Integer.valueOf(17).equals(id.get(logger)));
                java.lang.reflect.Field interfaceLogger=AdapterBase.class.getDeclaredField("adapterInterfaceLogger");interfaceLogger.setAccessible(true);Object logging=interfaceLogger.get(actual);
                java.lang.reflect.Field collectLogger=logging.getClass().getDeclaredField("collectCycleLogger");collectLogger.setAccessible(true);
                com.integrien.alive.common.adapter3.logger.AdapterInterfaceLoggerBase context=(com.integrien.alive.common.adapter3.logger.AdapterInterfaceLoggerBase)collectLogger.get(logging);
                check("/tmp/rdm-collector-synthetic".equals(context.getAdapterName()));check(Integer.valueOf(17).equals(context.getAdapterId()));
            }
            stage="DISCOVER";List<String> found=discovered(adapter.discover(new DiscoveryParam(c.getAdapterInstResource())));check(found.size()==(lun?2:1));
            stage="COLLECT";List<String> first=snapshot(adapter.collect(),f,lun,true);
            stage="REPEAT_COLLECT";List<String> again=snapshot(adapter.collect(),f,lun,false);check(first.equals(again));
            stage="RECONFIGURE";s=adapter.configure(c);check(s!=null&&s.getStatus()!=AdapterStatus.Status.as_failed);
            stage="RECONFIGURED_COLLECT";check(first.equals(snapshot(adapter.collect(),f,lun,false)));
            partialLifecycle(adapter,c,f,lun,first);
            stage="STOP";adapter.stopCollection();
            if(adapter instanceof RdmTopologyDispatcher){empty(adapter.collect());check(adapter.discover(new DiscoveryParam(c.getAdapterInstResource())).isEmpty());}
            return first;
        }catch(ReflectiveOperationException ex){throw new AssertionError("SDK initialization introspection failed",ex);}
        finally{String primary=stage;try{check(adapter.discard()!=null);if(adapter instanceof RdmTopologyDispatcher)empty(adapter.collect());}finally{stage=primary;}}
    }
    private static void empty(CollectResult r){check(r!=null&&r.getResourceCollectResultMap().isEmpty());check(r.getDiscoveryResult()==null);check(r.getAdapterStatus().getResourceStatusMap().isEmpty());}
    public static void main(String[] args){
        java.io.PrintStream out=System.out,err=System.err;
        String report="FIXTURE_LIFECYCLE code=FAILED";int exit=1;
        try(java.io.PrintStream sink=new java.io.PrintStream(java.io.OutputStream.nullOutputStream())){
            System.setOut(sink);System.setErr(sink);
            descriptor=java.nio.file.Path.of(args[0]);
            for(boolean lun:List.of(false,true))for(int mode:List.of(0,1,2)){
                scenario=(lun?"V2_LUN":"V1_MAPPING")+(mode==0?"_DEFAULT":mode==1?"_EXPLICIT":"_SDK_EMPTY")+"_BASELINE";
                stage="CONSTRUCT_BASELINE";List<String> baseline=lifecycle(new RdmTopologyPocAdapter(),mode,lun);
                scenario=(lun?"V2_LUN":"V1_MAPPING")+(mode==0?"_DEFAULT":mode==1?"_EXPLICIT":"_SDK_EMPTY")+"_DISPATCHER";
                stage="CONSTRUCT_DISPATCHER";List<String> dispatched=lifecycle(new RdmTopologyDispatcher(),mode,lun);
                check(baseline.equals(dispatched));
                scenario=(lun?"V2_LUN":"V1_MAPPING")+(mode==0?"_DEFAULT":mode==1?"_EXPLICIT":"_SDK_EMPTY")+"_BASELINE_COLLECTOR";
                stage="CONSTRUCT_BASELINE_COLLECTOR";List<String> baselineCollector=lifecycle(new RdmTopologyPocAdapter("/tmp/rdm-collector-synthetic",17),mode,lun);
                scenario=(lun?"V2_LUN":"V1_MAPPING")+(mode==0?"_DEFAULT":mode==1?"_EXPLICIT":"_SDK_EMPTY")+"_DISPATCHER_COLLECTOR";
                stage="CONSTRUCT_DISPATCHER_COLLECTOR";List<String> dispatcherCollector=lifecycle(RdmTopologyDispatcher.class.getConstructor(String.class,Integer.class).newInstance("/tmp/rdm-collector-synthetic",Integer.valueOf(17)),mode,lun);
                check(baseline.equals(baselineCollector));check(baselineCollector.equals(dispatcherCollector));
            }
            report="FIXTURE_LIFECYCLE code=PASS assertions="+checks+" scenarios=6 routes=4";exit=0;
        }catch(NoClassDefFoundError ex){
            String name=ex.getMessage();
            if(name!=null&&name.matches("[A-Za-z_$][A-Za-z0-9_$/]{0,255}"))report="FIXTURE_LIFECYCLE code=BLOCKED_PLATFORM_DEPENDENCY class="+name.replace('/','.');
            else report="FIXTURE_LIFECYCLE code=BLOCKED_PLATFORM_DEPENDENCY";
            exit=2;
        }catch(Exception|LinkageError|AssertionError ex){report="FIXTURE_LIFECYCLE code=FAILED stage="+stage+" scenario="+scenario+" rule="+rule+" class="+ex.getClass().getName()+" assertions="+checks;}
        finally{System.setOut(out);System.setErr(err);}
        out.println(report);System.exit(exit);
    }
}
