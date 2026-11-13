// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge;

final class TestSupport {
    private TestSupport() {}
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void rejects(Class<? extends Throwable> type, Runnable action, String message) {
        try { action.run(); }
        catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError(message + ": unexpected failure", failure);
        }
        throw new AssertionError(message + ": accepted forbidden operation");
    }
}
