package com.vcfcf.adapters.rdmtopologypoc.diagnostic;

import com.integrien.alive.common.adapter3.*;
import com.integrien.alive.common.adapter3.config.AdapterConfig;
import com.integrien.alive.common.adapter3.describe.AdapterDescribe;

/** UNREGISTERED candidate. Direct interface deliberately avoids AdapterBase automatic resource/metric/event emission. */
public final class ReadOnlyDiagnosticAdapter implements AdapterInterface3 {
    private final DiagnosticRuntime runtime;
    private volatile Integer instanceId;
    public ReadOnlyDiagnosticAdapter(){this(new DiagnosticRuntime());}
    ReadOnlyDiagnosticAdapter(DiagnosticRuntime runtime){this.runtime=runtime;}
    private AdapterStatus status(DiagnosticRuntime.Report report){return new AdapterStatus(instanceId,
            report.previousRunPassed()||report.state==DiagnosticRuntime.State.READY||report.state==DiagnosticRuntime.State.DISABLED
                    ||report.state==DiagnosticRuntime.State.NOT_AUTHORIZED?AdapterStatus.Status.as_succeeded:AdapterStatus.Status.as_failed,report.toString());}
    @Override public AdapterStatus configure(AdapterConfig config){
        try{instanceId=config==null?null:config.getAdapterInstResourceId();}
        catch(RuntimeException|LinkageError ex){instanceId=null;}
        return status(runtime.configure(config));
    }
    /** Bare controller invocation is injection-independent. Candidate descriptor is not installed by this increment. */
    @Override public AdapterDescribe describe(){
        try(java.io.InputStream in=ReadOnlyDiagnosticAdapter.class.getResourceAsStream("/diagnostic-describe.xml")){
            if(in==null)throw new IllegalStateException();
            AdapterDescribe result=AdapterDescribe.make(in);if(result==null)throw new IllegalStateException();return result;
        }catch(Exception|LinkageError ex){throw new IllegalStateException("RDM_DIAGNOSTIC_DESCRIPTOR_RUNTIME_DEPENDENCY_UNAVAILABLE");}
    }
    @Override public DiscoveryResult discover(DiscoveryParam ignored){return new DiscoveryResult(DiagnosticRuntime.KIND,instanceId);}
    @Override public CollectResult collect(){
        DiagnosticRuntime.Report report=runtime.executeOnce();
        CollectResult result=new CollectResult(DiagnosticRuntime.KIND,instanceId);
        result.getAdapterStatus().setStatus(status(report).getStatus(),report.toString());
        return result;
    }
    /** Configuration validation never proves discovery/authentication and never decrypts a secret. */
    @Override public boolean test(TestParam param){
        if(param==null)return false;
        try{
            DiagnosticRuntime.Report result=DiagnosticRuntime.validateConfiguration(param.getAdapterConfig());
            param.setErrorMsg(result.toString());return result.state!=DiagnosticRuntime.State.FAILED;
        }catch(RuntimeException|LinkageError ex){param.setErrorMsg("RDM_DIAGNOSTIC INVALID_CONFIGURATION");return false;}
    }
    @Override public boolean changePassword(PasswordParam ignored){return false;}
    @Override public CheckCertificateResult checkCertificate(CheckCertificateParam ignored){
        CheckCertificateResult result=new CheckCertificateResult();result.setState(CheckCertificateResult.CheckCertificateResultState.NOT_SUPPORTED);
        result.setMessage("RDM_DIAGNOSTIC MANAGED_CA_DELIVERY_REQUIRED; no certificate fetched or accepted");return result;
    }
    @Override public void stopCollection(){runtime.cancel();}
    @Override public AdapterStatus discard(){runtime.close();return status(runtime.latest());}
}
