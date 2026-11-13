// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge;

public final class PublisherAccessTest {
    private PublisherAccessTest() {}
    public static void main(String[] args) {
        // Removing either genuine UID or permission check must fail this suite.
        for (int uid : new int[] {0, 1000, 10001, -1}) {
            final int caller = uid;
            TestSupport.rejects(SecurityException.class,
                    () -> PublisherAccess.require(caller, true, false, true), "wrong UID");
        }
        TestSupport.rejects(SecurityException.class,
                () -> PublisherAccess.require(2000, false, false, true), "DUMP absent");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> PublisherAccess.require(2000, true, true, true), "oneway");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> PublisherAccess.require(2000, true, false, false), "no reply");
        PublisherAccess.require(2000, true, false, true);
        System.out.println("PASS PublisherAccessTest: shell identity, DUMP, synchronous reply");
    }
}
