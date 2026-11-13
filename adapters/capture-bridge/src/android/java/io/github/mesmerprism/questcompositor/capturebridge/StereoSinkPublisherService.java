// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;

/** Host subclass supplies its actual owner; no reader or native library is created here. */
public abstract class StereoSinkPublisherService extends Service {
    private SinkPublisherOwner boundOwner;
    private StereoSinkPublisher publisher;
    private boolean destroyed;
    protected abstract SinkPublisherOwner sinkOwner();
    @Override public final IBinder onBind(Intent intent) {
        if (destroyed || intent == null || intent.getAction() != null || intent.getData() != null
                || intent.getCategories() != null || intent.getExtras() != null) return null;
        ComponentName component=intent.getComponent();
        if (component == null || !getPackageName().equals(component.getPackageName())
                || !getClass().getName().equals(component.getClassName())) return null;
        if (boundOwner == null) boundOwner=sinkOwner();
        if (boundOwner == null || boundOwner.snapshot().phase != SinkAdmission.Phase.ACTIVE) return null;
        if (publisher != null && !publisher.descriptor().equals(boundOwner.snapshot().descriptor)) {
            publisher.close(); publisher=null;
        }
        if (publisher == null) publisher=new StereoSinkPublisher(this,boundOwner);
        return publisher;
    }
    @Override public final int onStartCommand(Intent intent, int flags, int startId) {
        stopSelf(startId); return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        destroyed=true;
        if (publisher != null) publisher.close();
        super.onDestroy();
    }
}
