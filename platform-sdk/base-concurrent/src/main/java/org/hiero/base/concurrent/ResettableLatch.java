// SPDX-License-Identifier: Apache-2.0
package org.hiero.base.concurrent;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A latch that can be armed, waited on, and disarmed repeatedly.
 *
 * <p>Arming is idempotent: arming an already armed latch has no effect, so multiple signals before a
 * {@link #disarm()} collapse into one. Threads blocked in {@link #awaitArmed()} are released when the latch is
 * armed, and the latch stays armed until {@link #disarm()} is called.
 */
public class ResettableLatch {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition armedCondition = lock.newCondition();
    private boolean armed;

    /**
     * Arms the latch, releasing any threads blocked in {@link #awaitArmed()}. Has no effect if already armed.
     */
    public void arm() {
        lock.lock();
        try {
            if (!armed) {
                armed = true;
                armedCondition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Blocks until the latch is armed. Returns immediately if it is already armed.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void awaitArmed() throws InterruptedException {
        lock.lock();
        try {
            while (!armed) {
                armedCondition.await();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Disarms the latch so that subsequent calls to {@link #awaitArmed()} block until it is armed again.
     */
    public void disarm() {
        lock.lock();
        try {
            armed = false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return {@code true} if the latch is currently armed
     */
    public boolean isArmed() {
        lock.lock();
        try {
            return armed;
        } finally {
            lock.unlock();
        }
    }
}
