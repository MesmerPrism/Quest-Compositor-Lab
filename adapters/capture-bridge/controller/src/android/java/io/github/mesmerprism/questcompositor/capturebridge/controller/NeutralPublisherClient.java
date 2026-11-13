// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge.controller;
import android.Manifest;
import android.app.Application;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.system.Os;
import android.view.Surface;
import io.github.mesmerprism.questcompositor.capturebridge.*;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import static io.github.mesmerprism.questcompositor.capturebridge.controller.CapturePorts.*;

/** Exact original publisher client. Does not bind/start the app or discover a replacement. */
public final class NeutralPublisherClient implements SinkClient {
    public interface AndroidLease extends SinkLease { Surface borrowedSurface(); }
    private final Application application;private final CaptureAdmission.SinkExpectation expected;private final IBinder original;
    private final StereoSinkPublisher.Publisher publisher;private final Binder lifetime=new Binder();private boolean acquireAttempted,watchAttempted;
    private OwnedLease unresolvedAcquire;private IBinder.DeathRecipient originalDeathRecipient;
    public NeutralPublisherClient(Application app,CaptureAdmission.SinkExpectation expected,IBinder original){
        if(app==null||expected==null||original==null||expected.originalPublisherIdentity!=original)throw new IllegalArgumentException("actual original app publisher required");
        application=app;this.expected=expected;this.original=original;publisher=StereoSinkPublisher.asInterface(original);
    }
    @Override public synchronized SinkLease acquire(StereoSinkDescriptor descriptor,Object invocation)throws Exception{
        if(acquireAttempted||invocation==null)throw new IllegalStateException("one original acquire attempt per client");acquireAttempted=true;
        requireDescriptor(descriptor);verifyActual();StereoSinkPublisher.Snapshot snapshot=publisher.getSink(descriptor,lifetime);
        OwnedLease lease=new OwnedLease(snapshot,invocation);unresolvedAcquire=lease;
        verifyActual();if(!lease.valid())throw new IllegalStateException("original Surface unavailable");
        unresolvedAcquire=null;return lease;
        // On a failed post-acquire check, this exact client retains the known wrapper. No early close,
        // retry or invented join is provided; private original-operation reconciliation remains required.
    }
    @Override public SinkStatus currentStatus(StereoSinkDescriptor descriptor)throws Exception{requireDescriptor(descriptor);verifyActual();SinkStatus status=publisher.getStatus(descriptor);verifyActual();return status;}
    @Override public synchronized Monitor watchOriginal(SinkLease sink,final Runnable onDeath)throws Exception{
        requireOwned(sink,expected.descriptor);if(watchAttempted||onDeath==null)throw new IllegalStateException("one original publisher monitor required");watchAttempted=true;
        final IBinder.DeathRecipient recipient=()->onDeath.run();originalDeathRecipient=recipient;original.linkToDeath(recipient,0);
        return new Monitor(){private boolean attempted;@Override public synchronized boolean retire(){if(attempted)return false;attempted=true;return original.unlinkToDeath(recipient,0);}};
    }
    public AndroidLease requireOwned(SinkLease lease,StereoSinkDescriptor descriptor){
        requireDescriptor(descriptor);if(!(lease instanceof OwnedLease)||((OwnedLease)lease).issuer()!=this||!lease.descriptor().equals(descriptor))throw new IllegalArgumentException("exact owned neutral Surface lease required");return (AndroidLease)lease;
    }
    private void requireDescriptor(StereoSinkDescriptor d){if(!expected.descriptor.equals(d))throw new IllegalArgumentException("frozen descriptor differs");}
    private final class OwnedLease implements AndroidLease {
        final StereoSinkPublisher.Snapshot snapshot;final Object invocation;boolean closed;
        OwnedLease(StereoSinkPublisher.Snapshot snapshot,Object invocation){this.snapshot=snapshot;this.invocation=invocation;}
        NeutralPublisherClient issuer(){return NeutralPublisherClient.this;}
        public StereoSinkDescriptor descriptor(){return snapshot.descriptor;}public Object publisherIdentity(){return original;}public Object invocationIdentity(){return invocation;}
        public synchronized boolean valid(){return !closed&&original.isBinderAlive()&&snapshot.surface.isValid();}
        public synchronized Surface borrowedSurface(){if(!valid())throw new IllegalStateException("live borrowed Surface required");return snapshot.surface;}
        public synchronized void close(){if(closed)throw new IllegalStateException("wrapper close replay");closed=true;snapshot.close();}
    }
    private void verifyActual()throws Exception{
        if(Process.myUid()!=2000||Os.geteuid()!=2000||application.checkSelfPermission(Manifest.permission.DUMP)!=PackageManager.PERMISSION_GRANTED||!original.isBinderAlive())throw new SecurityException("actual Shell/DUMP/original publisher required");
        verifyProcess();PackageManager pm=application.getPackageManager();CaptureAdmission.Service s=expected.service;
        ServiceInfo service=pm.getServiceInfo(new ComponentName(s.packageName,s.className),0);ApplicationInfo app=pm.getApplicationInfo(s.packageName,0);
        if(!s.packageName.equals(service.packageName)||!s.className.equals(service.name)||!s.permission.equals(service.permission)||service.exported!=s.exported||service.enabled!=s.enabled||service.applicationInfo.uid!=s.uid||app.uid!=s.uid)throw new SecurityException("installed sink manifest/UID differs");
        File apk=new File(app.sourceDir);if(apk.length()!=expected.apk.sizeBytes)throw new SecurityException("installed APK size differs");
        MessageDigest hash=MessageDigest.getInstance("SHA-256");long count=0;byte[] block=new byte[65536];
        try(FileInputStream input=new FileInputStream(apk)){for(int n;(n=input.read(block))!=-1;){count+=n;if(count>expected.apk.sizeBytes)throw new SecurityException("APK changed during hash");hash.update(block,0,n);}}
        StringBuilder digest=new StringBuilder("sha256:");for(byte b:hash.digest()){digest.append(Character.forDigit((b>>>4)&15,16));digest.append(Character.forDigit(b&15,16));}
        if(count!=expected.apk.sizeBytes||!expected.apk.sha256.equals(digest.toString()))throw new SecurityException("installed APK hash differs");verifyProcess();
    }
    private byte[] proc(String file)throws Exception{byte[] bytes=Files.readAllBytes(Paths.get("/proc/"+expected.pid+"/"+file));if(bytes.length==0||bytes.length>16384)throw new SecurityException("bounded original process data required");return bytes;}
    private void verifyProcess()throws Exception{
        String stat=new String(proc("stat"),StandardCharsets.US_ASCII);int right=stat.lastIndexOf(')');int first=stat.indexOf(' ');
        if(first<1||right<first||Long.parseLong(stat.substring(0,first))!=expected.pid)throw new SecurityException("original process stat differs");
        String[] fields=stat.substring(right+1).trim().split("\\s+");if(fields.length<=19||Long.parseLong(fields[19])!=expected.startTicks)throw new SecurityException("original process birth differs");
        String status=new String(proc("status"),StandardCharsets.US_ASCII);String found=null;for(String row:status.split("\n"))if(row.startsWith("Uid:")){if(found!=null)throw new SecurityException("duplicate UID row");found=row;}
        if(found==null)throw new SecurityException("UID row absent");String[] uids=found.trim().split("\\s+");int[] row=expected.uidRow();if(uids.length!=5)throw new SecurityException("exact four UID values required");
        for(int i=0;i<4;i++)if(Integer.parseInt(uids[i+1])!=row[i])throw new SecurityException("original process UID differs");
        if(!Arrays.equals(proc("cmdline"),expected.cmdline()))throw new SecurityException("original NUL cmdline differs");
    }
}
