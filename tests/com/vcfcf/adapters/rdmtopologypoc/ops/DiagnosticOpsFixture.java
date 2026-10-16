package com.vcfcf.adapters.rdmtopologypoc.ops;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLContext;

/** Test-only bridge to actual provider and direct TLS transport with synthetic credentials. */
public final class DiagnosticOpsFixture {
    private static final String VC="11111111-1111-1111-1111-111111111111",ID="33333333-3333-3333-3333-333333333333";
    public static String row(){return "{\"identifier\":\""+ID+"\",\"resourceKey\":{\"adapterKindKey\":\"VMWARE\",\"resourceKindKey\":\"VirtualMachine\",\"name\":\"SENSITIVE-NAME\",\"resourceIdentifiers\":["
            +id("VMEntityObjectID","vm-71",true)+","+id("VMEntityVCID",VC,true)+","+id("unused","",false)+","+id("case-preserved","MiXeD",false)+"]}}";}
    private static String id(String key,String value,boolean unique){return "{\"identifierType\":{\"name\":\""+key+"\",\"isPartOfUniqueness\":"+unique+"},\"value\":\""+value+"\"}";}
    public static String response(String path){
        if(path.endsWith("acquire"))return "{\"token\":\"synthetic-token\",\"validity\":9999999999999}";
        if(path.endsWith("release"))return "";
        if(path.contains("?"))return "{\"pageInfo\":{\"page\":0,\"pageSize\":200,\"totalCount\":1},\"resourceList\":["+row()+"]}";
        return row();
    }
    public static SuiteApiVmProvider provider(int failureStatus,boolean cleanupFailure){
        return new SuiteApiVmProvider((method,path,body,token,budget)->{
            OpsHttpsTransport.validate(method,path,body,token);
            if(path.endsWith("release")&&cleanupFailure)throw new OpsException(OpsException.Code.TLS);
            return new SuiteApiVmProvider.Reply(failureStatus==0?200:failureStatus,response(path).getBytes(StandardCharsets.UTF_8));
        },()->new OperationsBinding.Credentials("synthetic-instance","SENSITIVE-SYNTHETIC".toCharArray()));
    }
    public static SuiteApiVmProvider tlsProvider(SSLContext context,int port)throws OpsException{
        return new SuiteApiVmProvider(new OpsHttpsTransport(URI.create("https://localhost:"+port+"/suite-api"),context,1000,1000),
                ()->new OperationsBinding.Credentials("synthetic-instance","SENSITIVE-SYNTHETIC".toCharArray()));
    }
    public static SSLContext jvmContext()throws OpsException{return OperationsBinding.trust("jvm",null,null);}
}
