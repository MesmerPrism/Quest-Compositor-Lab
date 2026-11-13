// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge.controller;

import io.github.mesmerprism.questcompositor.capturebridge.StereoSinkDescriptor;
import java.util.Arrays;
import java.util.Objects;

/** Immutable expectations, not proof that live observations satisfy them. */
public final class CaptureAdmission {
    public enum LocalWrapperPolicy { ACCEPTED_STOP_GRACE_OR_ORIGINAL_DEATH }
    public static final class Artifact {
        public final String sha256; public final long sizeBytes;
        public Artifact(String hash,long size){sha256=hash;sizeBytes=size;}
        @Override public boolean equals(Object x){return x instanceof Artifact && sizeBytes==((Artifact)x).sizeBytes && Objects.equals(sha256,((Artifact)x).sha256);}
        @Override public int hashCode(){return Objects.hash(sha256,sizeBytes);}
    }
    public static final class Service {
        public final String packageName,className,permission;public final int uid;public final boolean exported,enabled;
        public Service(String p,String c,String permission,int uid,boolean exported,boolean enabled){packageName=p;className=c;this.permission=permission;this.uid=uid;this.exported=exported;this.enabled=enabled;}
        @Override public boolean equals(Object x){if(!(x instanceof Service))return false;Service s=(Service)x;return uid==s.uid&&exported==s.exported&&enabled==s.enabled&&Objects.equals(packageName,s.packageName)&&Objects.equals(className,s.className)&&Objects.equals(permission,s.permission);}
        @Override public int hashCode(){return Objects.hash(packageName,className,permission,uid,exported,enabled);}
    }
    public static final class CompatibilityExpectation {
        public final String firmwareId,apiId,abi,driverProfileSha256,binderDescriptor,vendorPermission,loaderPolicy,signaturePolicy,defaultPolicy,privacyPolicy;
        public final Artifact artifact;public final Service service;public final long interfaceVersion,serviceVersion;public final LocalWrapperPolicy localPolicy;
        public CompatibilityExpectation(String firmware,String api,String abi,String profile,Artifact artifact,Service service,String binder,long iv,long sv,String permission,String loader,String signature,String defaults,String privacy,LocalWrapperPolicy policy){
            firmwareId=firmware;apiId=api;this.abi=abi;driverProfileSha256=profile;this.artifact=artifact;this.service=service;binderDescriptor=binder;interfaceVersion=iv;serviceVersion=sv;vendorPermission=permission;loaderPolicy=loader;signaturePolicy=signature;defaultPolicy=defaults;privacyPolicy=privacy;localPolicy=policy;
        }
        @Override public boolean equals(Object x){if(!(x instanceof CompatibilityExpectation))return false;CompatibilityExpectation c=(CompatibilityExpectation)x;return interfaceVersion==c.interfaceVersion&&serviceVersion==c.serviceVersion&&localPolicy==c.localPolicy&&Objects.equals(artifact,c.artifact)&&Objects.equals(service,c.service)&&Objects.equals(firmwareId,c.firmwareId)&&Objects.equals(apiId,c.apiId)&&Objects.equals(abi,c.abi)&&Objects.equals(driverProfileSha256,c.driverProfileSha256)&&Objects.equals(binderDescriptor,c.binderDescriptor)&&Objects.equals(vendorPermission,c.vendorPermission)&&Objects.equals(loaderPolicy,c.loaderPolicy)&&Objects.equals(signaturePolicy,c.signaturePolicy)&&Objects.equals(defaultPolicy,c.defaultPolicy)&&Objects.equals(privacyPolicy,c.privacyPolicy);}
        @Override public int hashCode(){return Objects.hash(firmwareId,apiId,abi,driverProfileSha256,artifact,service,binderDescriptor,interfaceVersion,serviceVersion,vendorPermission,loaderPolicy,signaturePolicy,defaultPolicy,privacyPolicy,localPolicy);}
    }
    public static final class ShellIdentity {
        public final int uid,euid,attributionUid;public final String packageName,opPackageName,attributionPackage;
        public final boolean frameworkApplication,dumpGranted,vendorPermissionGranted;
        public ShellIdentity(int uid,int euid,int auid,String pkg,String op,String attr,boolean framework,boolean dump,boolean permission){this.uid=uid;this.euid=euid;attributionUid=auid;packageName=pkg;opPackageName=op;attributionPackage=attr;frameworkApplication=framework;dumpGranted=dump;vendorPermissionGranted=permission;}
    }
    public static final class SinkExpectation {
        public final Service service;public final Artifact apk;public final long pid,startTicks;public final StereoSinkDescriptor descriptor;public final Object originalPublisherIdentity;
        private final int[] uidRow;private final byte[] cmdline;
        public SinkExpectation(Service service,Artifact apk,long pid,long ticks,int[] row,byte[] cmdline,StereoSinkDescriptor descriptor,Object publisher){this.service=service;this.apk=apk;this.pid=pid;startTicks=ticks;uidRow=row==null?null:row.clone();this.cmdline=cmdline==null?null:cmdline.clone();this.descriptor=descriptor;originalPublisherIdentity=publisher;}
        public int[] uidRow(){return uidRow.clone();}public byte[] cmdline(){return cmdline.clone();}
    }
    public static final class Scope {
        public final String targetId,bootId,leaseId,clockDomain,requestId,sessionId,nonce;
        public final long nativePid,nativeStartTicks,initialObservationSerial,actionValidUntilMillis,cleanupValidUntilMillis;
        public final Object originalBinderIdentity;public final boolean priorCustodyResolved,soleOperatorDeclared,oneOwnGlobalStopAuthorized;
        public Scope(String target,String boot,String lease,String domain,long pid,long ticks,long serial,long actionUntil,long cleanupUntil,Object binder,String request,String session,String nonce,boolean prior,boolean sole,boolean stop){targetId=target;bootId=boot;leaseId=lease;clockDomain=domain;nativePid=pid;nativeStartTicks=ticks;initialObservationSerial=serial;actionValidUntilMillis=actionUntil;cleanupValidUntilMillis=cleanupUntil;originalBinderIdentity=binder;requestId=request;sessionId=session;this.nonce=nonce;priorCustodyResolved=prior;soleOperatorDeclared=sole;oneOwnGlobalStopAuthorized=stop;}
    }
    public static final class Budget {
        public final long runMillis,observationMillis,joinMillis,localGraceMillis;public final LocalWrapperPolicy localPolicy;
        public Budget(long run,long observation,long join,long grace,LocalWrapperPolicy policy){runMillis=run;observationMillis=observation;joinMillis=join;localGraceMillis=grace;localPolicy=policy;}
    }
    public final CompatibilityExpectation compatibility;public final SinkExpectation sink;public final Scope scope;public final Budget budget;public final ShellIdentity shell;
    public CaptureAdmission(CompatibilityExpectation c,SinkExpectation s,Scope scope,Budget b,ShellIdentity shell){compatibility=c;sink=s;this.scope=scope;budget=b;this.shell=shell;}
    public CaptureAdmission withShell(ShellIdentity identity){return new CaptureAdmission(compatibility,sink,scope,budget,identity);}
    public void validate(){
        need(compatibility!=null&&sink!=null&&scope!=null&&budget!=null&&shell!=null,"complete admission required");
        validateShell(shell);
        CompatibilityExpectation c=compatibility;artifact(c.artifact);service(c.service);
        need(token(c.firmwareId)&&token(c.apiId)&&token(c.abi)&&hash(c.driverProfileSha256)&&token(c.binderDescriptor)&&c.interfaceVersion>0&&c.serviceVersion>0&&token(c.vendorPermission)&&token(c.loaderPolicy)&&token(c.signaturePolicy)&&token(c.defaultPolicy)&&token(c.privacyPolicy)&&c.localPolicy!=null,"closed exact compatibility required");
        service(sink.service);artifact(sink.apk);need("android.permission.DUMP".equals(sink.service.permission)&&sink.pid>0&&sink.startTicks>0&&sink.descriptor!=null&&sink.originalPublisherIdentity!=null&&sink.uidRow!=null&&sink.uidRow.length==4,"exact sink identity/descriptor required");
        for(int uid:sink.uidRow)need(uid==sink.service.uid,"four exact UID rows required");
        need(sink.cmdline!=null&&Arrays.equals(sink.cmdline,(sink.service.packageName+"\0").getBytes(java.nio.charset.StandardCharsets.UTF_8)),"exact NUL-terminated application cmdline required");
        Scope s=scope;need(token(s.targetId)&&token(s.bootId)&&token(s.leaseId)&&token(s.clockDomain)&&s.nativePid>0&&s.nativeStartTicks>0&&s.initialObservationSerial>0&&s.actionValidUntilMillis>0&&s.cleanupValidUntilMillis>=s.actionValidUntilMillis&&s.originalBinderIdentity!=null&&token(s.requestId)&&token(s.sessionId)&&!s.requestId.equals(s.sessionId)&&s.nonce!=null&&s.nonce.matches("[0-9a-f]{32}")&&s.priorCustodyResolved&&s.soleOperatorDeclared&&s.oneOwnGlobalStopAuthorized,"exact original invocation/scope/prior custody required");
        need(budget.runMillis>0&&budget.runMillis<=600000&&budget.observationMillis>0&&budget.observationMillis<=10000&&budget.joinMillis>0&&budget.joinMillis<=10000&&budget.localGraceMillis>0&&budget.localGraceMillis<=60000&&budget.localPolicy==c.localPolicy,"bounded explicit local-wrapper policy required");
    }
    public static void validateShell(ShellIdentity i){need(i!=null&&i.uid==2000&&i.euid==2000&&i.attributionUid==2000&&"com.android.shell".equals(i.packageName)&&"com.android.shell".equals(i.opPackageName)&&"com.android.shell".equals(i.attributionPackage)&&i.frameworkApplication&&i.dumpGranted&&i.vendorPermissionGranted,"real framework Shell/attribution/permissions required");}
    private static void artifact(Artifact a){need(a!=null&&a.sizeBytes>0&&hash(a.sha256),"exact artifact size/hash required");}
    private static void service(Service s){need(s!=null&&token(s.packageName)&&token(s.className)&&s.className.startsWith(s.packageName+".")&&token(s.permission)&&s.uid>0&&s.exported&&s.enabled,"exact enabled exported service/owner/permission required");}
    static void need(boolean v,String message){if(!v)throw new IllegalArgumentException(message);}
    static boolean token(String v){return v!=null&&!v.isEmpty()&&v.length()<=256&&v.matches("[A-Za-z0-9._:-]+");}
    static boolean hash(String v){return v!=null&&v.matches("sha256:[0-9a-f]{64}");}
}
