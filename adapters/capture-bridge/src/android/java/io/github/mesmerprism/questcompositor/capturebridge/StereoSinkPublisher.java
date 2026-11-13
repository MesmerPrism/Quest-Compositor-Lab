// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.BadParcelableException;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.Surface;

/** Closed synchronous Surface publisher. No effect controls or capture-service API. */
public final class StereoSinkPublisher extends Binder {
    public static final String DESCRIPTOR="io.github.mesmerprism.questcompositor.capturebridge.StereoSinkPublisher.v1";
    public static final int GET_SINK=IBinder.FIRST_CALL_TRANSACTION;
    public static final int GET_STATUS=IBinder.FIRST_CALL_TRANSACTION+1;
    private final Context context;
    private final SinkPublisherOwner owner;
    private volatile PublisherBinding binding;

    public StereoSinkPublisher(Context context, SinkPublisherOwner owner) {
        if (context == null || owner == null) throw new IllegalArgumentException("own Context and sink owner required");
        this.context=context; this.owner=owner;
        this.binding=PublisherBinding.bind(owner.snapshot());
    }
    public StereoSinkDescriptor descriptor() { return binding.descriptor; }
    public synchronized void close() {
        binding=binding.close(); owner.publisherDestroyed(binding.descriptor);
    }
    public interface Publisher {
        Snapshot getSink(StereoSinkDescriptor expected, IBinder clientLifetime) throws RemoteException;
        SinkStatus getStatus(StereoSinkDescriptor expected) throws RemoteException;
    }
    public static final class Snapshot implements AutoCloseable {
        public final StereoSinkDescriptor descriptor;
        public final Surface surface;
        private boolean released;
        Snapshot(StereoSinkDescriptor descriptor, Surface surface) {
            if (descriptor == null || surface == null || !surface.isValid()) {
                throw new IllegalArgumentException("valid app reader snapshot required");
            }
            this.descriptor=descriptor; this.surface=surface;
        }
        @Override public synchronized void close() {
            if (!released) { released=true; surface.release(); }
        }
    }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        PublisherAccess.require(Binder.getCallingUid(),
                context.checkCallingPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED,
                (flags & IBinder.FLAG_ONEWAY) != 0, reply != null);
        if (code == IBinder.INTERFACE_TRANSACTION) {
            requireEnd(data); reply.writeString(DESCRIPTOR); return true;
        }
        if (code != GET_SINK && code != GET_STATUS) return false;
        data.enforceInterface(DESCRIPTOR);
        StereoSinkDescriptor expected=readDescriptor(data);
        binding.requireRequest(expected);
        if (code == GET_SINK) {
            IBinder lifetime=data.readStrongBinder(); requireEnd(data);
            Snapshot snapshot=owner.acquire(expected,lifetime);
            try {
                reply.writeNoException(); writeWords(reply,snapshot.descriptor.toWords());
                snapshot.surface.writeToParcel(reply,0); return true;
            } catch (RuntimeException failure) {
                close(); // Unknown handoff must not leave this reader's admission live.
                throw failure;
            } finally { snapshot.close(); }
        }
        requireEnd(data);
        SinkStatus status=owner.status(expected);
        reply.writeNoException(); writeWords(reply,status.toWords()); return true;
    }
    public static Publisher asInterface(final IBinder binder) {
        if (binder == null) throw new IllegalArgumentException("publisher Binder required");
        // Always transact: a local-interface shortcut would bypass the actual Binder caller gate.
        return new Publisher() {
            @Override public Snapshot getSink(StereoSinkDescriptor expected, IBinder lifetime) throws RemoteException {
                if (expected == null || lifetime == null) throw new IllegalArgumentException("exact descriptor/client required");
                Parcel data=Parcel.obtain(), reply=Parcel.obtain();
                Surface surface=null;
                try {
                    data.writeInterfaceToken(DESCRIPTOR); writeWords(data,expected.toWords()); data.writeStrongBinder(lifetime);
                    if (!binder.transact(GET_SINK,data,reply,0)) throw new RemoteException("sink transaction rejected");
                    reply.readException();
                    StereoSinkDescriptor actual=readDescriptor(reply);
                    surface=Surface.CREATOR.createFromParcel(reply); requireEnd(reply);
                    if (!expected.equals(actual)) throw new BadParcelableException("publisher descriptor changed");
                    Snapshot result=new Snapshot(actual,surface); surface=null; return result;
                } finally {
                    if (surface != null) surface.release();
                    reply.recycle(); data.recycle();
                }
            }
            @Override public SinkStatus getStatus(StereoSinkDescriptor expected) throws RemoteException {
                if (expected == null) throw new IllegalArgumentException("exact descriptor required");
                Parcel data=Parcel.obtain(), reply=Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR); writeWords(data,expected.toWords());
                    if (!binder.transact(GET_STATUS,data,reply,0)) throw new RemoteException("status transaction rejected");
                    reply.readException();
                    SinkStatus result=SinkStatus.fromWords(readWords(reply,SinkStatus.WORD_COUNT)); requireEnd(reply);
                    if (result.toWords()[1] != expected.generation) throw new BadParcelableException("status generation differs");
                    return result;
                } finally { reply.recycle(); data.recycle(); }
            }
        };
    }
    private static StereoSinkDescriptor readDescriptor(Parcel data) {
        return StereoSinkDescriptor.fromWords(readWords(data,StereoSinkDescriptor.WORD_COUNT));
    }
    private static long[] readWords(Parcel data, int count) {
        if (data.dataAvail() < count*8) throw new BadParcelableException("truncated fixed-word contract");
        long[] words=new long[count];
        for (int i=0;i<count;i++) words[i]=data.readLong();
        return words;
    }
    private static void writeWords(Parcel data, long[] words) {
        for (long word:words) data.writeLong(word);
    }
    private static void requireEnd(Parcel data) {
        if (data == null || data.dataAvail() != 0) throw new BadParcelableException("unexpected contract fields");
    }
}
