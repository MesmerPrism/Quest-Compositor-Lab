// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge.controller;
import android.Manifest;
import android.app.AppOpsManager;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.system.Os;

/** Ordinary framework reflection only; no hidden-API exemption or identity mutation. */
public final class GenuineShellApplication {
    private GenuineShellApplication(){}
    @SuppressWarnings("deprecation") // API 34 still exposes the required AppOps package/UID check.
    public static Application create(String vendorPermission)throws Exception{
        if(Process.myUid()!=2000||Os.getuid()!=2000||Os.geteuid()!=2000)throw new SecurityException("genuine Shell process required");
        Class<?> activityThread=Class.forName("android.app.ActivityThread");
        Object thread=activityThread.getMethod("systemMain").invoke(null);
        Context system=(Context)activityThread.getMethod("getSystemContext").invoke(thread);
        // No CONTEXT_INCLUDE_CODE: installed Shell custom Application code is never loaded.
        Context shell=system.createPackageContext("com.android.shell",0);
        if(shell.getPackageManager().getApplicationInfo("com.android.shell",0).uid!=2000)throw new SecurityException("framework Shell package UID differs");
        Application app=Instrumentation.newApplication(Application.class,shell);
        AppOpsManager appOps=app.getSystemService(AppOpsManager.class);if(appOps==null)throw new SecurityException("AppOps unavailable");
        appOps.checkPackage(2000,"com.android.shell");
        CaptureAdmission.validateShell(identity(app,vendorPermission));return app;
    }
    public static CaptureAdmission.ShellIdentity identity(Application app,String vendorPermission){
        if(app==null||vendorPermission==null)throw new IllegalArgumentException("actual framework application/permission required");
        return new CaptureAdmission.ShellIdentity(Process.myUid(),Os.geteuid(),app.getAttributionSource().getUid(),app.getPackageName(),app.getOpPackageName(),app.getAttributionSource().getPackageName(),app.getClass()==Application.class,app.checkSelfPermission(Manifest.permission.DUMP)==PackageManager.PERMISSION_GRANTED,app.checkSelfPermission(vendorPermission)==PackageManager.PERMISSION_GRANTED);
    }
}
