// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge;

/** Pure policy; Android caller identity/permission are supplied by the Binder boundary. */
public final class PublisherAccess {
    private PublisherAccess() {}
    public static void require(int callingUid, boolean dumpGranted, boolean oneWay, boolean replyPresent) {
        if (callingUid != 2000 || !dumpGranted) {
            throw new SecurityException("genuine Shell UID and DUMP permission required");
        }
        if (oneWay || !replyPresent) {
            throw new IllegalArgumentException("synchronous transaction with reply required");
        }
    }
}
