/*
 * Copyright (C) 2017-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform;

import java.time.Instant;

/**
 * A counting semaphore which provides methods to wait until it becomes zero. This is the opposite of a
 * standard counting semaphore.
 *
 * A standard semaphore can be thought of as counting available resources. It allows threads to take a
 * resource (decrementing the count) or return the resource (incrementing). And it provides methods that
 * allow a thread to wait until the resource count is greater than zero.
 *
 * The VetoSemaphore is the inverse of that. The semaphore maintains a count of the number of vetos. Any
 * thread can register a veto (incrementing), and later unregister their veto (decrementing). And it
 * provides methods to allow a thread to wait until the count reaches zero.
 *
 * The createVeto() method is a factory that produces a Veto object. That object starts by not having a veto
 * registered with the system. Later, the method myVetoObject.setVeto(lambda) can be called on some lambda
 * expression that has no parameters and that generates a boolean. If the boolean is true, then the object
 * is saying that wants a veto to be registered for it. If it is false, then it is saying that it doesn't
 * want a veto to be registered for it. If setVeto is called multiple times with the same boolean value, the
 * later calls have to effect. There is only an effect when it is called with the opposite boolean as the
 * previous call. The setVeto method is synchronized on the VetoSemaphore object that created this Veto
 * object, so if two threads call setVeto on the same object at the same time, then one will block while the
 * other is running its lambda expression and handling the result.
 */
class VetoSemaphore {
    private volatile long count = 0; // initially, no vetos have been registered yet

    /** a lambda that takes no parameters, and returns a boolean */
    public interface BooleanProducer {
        public boolean run();
    }

    /** a single veto that can have active set to true or false at any time */
    public class Veto {
        private boolean vetoActive = false; // is this veto currently stopping us
        private VetoSemaphore vetoSemaphore;

        // don't allow the constructor to be called without the semaphore
        private Veto() {}

        // private, so only the factory method can call it
        private Veto(VetoSemaphore vetoSemaphore) {
            this.vetoSemaphore = vetoSemaphore;
        }

        /**
         * Set whether this Veto object should be currently having an active veto registered with the
         * VetoSemaphore that created it. True means there should be n active veto registered with the
         * VetoSemaphore object that created this Veto object. False means there should not be an active
         * veto registered. So if the boolean for one call is the same as for the previous call, it has no
         * effect.
         *
         * The parameter isActive is a lambda expression that has no parameters and returns a boolean.
         *
         * This method synchronizes on the VetoSemaphore object that created this Veto object. Therefore, if
         * two threads call setActive on the same Veto object, or on two different Veto objects created by
         * the same VetoSemaphore object, then one of them will block while the other one is running its
         * lambda expression and updating the semaphore. So both the updates to the semaphore, and any
         * checks that are done in the lambda, will be combined into a single, atomic operation.
         *
         * @param isActive
         * 		a no-parameter lambda that returns true if there should be an active veto registered
         * 		with the VetoSemaphore, and false otherwise
         */
        public synchronized void setActive(BooleanProducer isActive) {
            boolean active = isActive.run();
            if (active == vetoActive) {
                return; // nothing is changing.
            }
            vetoActive = active;
            if (active) {
                vetoSemaphore.registerVeto();
            } else {
                vetoSemaphore.unregisterVeto();
            }
        }
    }

    /**
     * This factory method creates a new Veto object, associated with this VetoSemaphore. It is initially
     * not registering a veto. So it is initially not blocking anyone waiting for all the vetos to be
     * unregistered.
     *
     * @return the new Veto object
     */
    public Veto createVeto() {
        return new Veto(this);
    }

    /** the count of active vetos is initialized to zero */
    public VetoSemaphore() {}

    /**
     * increment the count, as if the calling thread has registered its veto. Any thread that calls
     * registerVeto must ensure that there is later a matching call to unregisterVeto.
     */
    private synchronized void registerVeto() {
        count++;
    }

    /**
     * decrement the count, as if the calling thread has canceled its veto. Any thread that calls
     * registerVeto must ensure that there is later a matching call to unregisterVeto.
     */
    private synchronized void unregisterVeto() {
        count--;
        if (count <= 0) {
            notifyAll(); // unblock everyone who was waiting for the veto count to reach zero
        }
    }

    /**
     * Returns true if there are no active vetos. So it returns true if a call to waitForNoVetos would have
     * returned without blocking. So it returns true if the current count is non-positive.
     *
     * @returns true if the count is zero or less
     */
    public synchronized boolean isNoVeto() {
        return (count <= 0);
    }

    /**
     * The caller will block, waiting until there are no active vetos. So it waits until the count reaches
     * zero. If the waiting thread is interrupted, it will unblock and throw the InterruptedException.
     *
     * The block will also end if the thread is interrupted.
     *
     * This method does not suffer from spurious wakeups.
     *
     * @throws InterruptedException
     * 		if the thread is interrupted
     */
    public synchronized void waitForNoVetos() throws InterruptedException {
        while (count > 0) {
            wait();
        }
    }

    /**
     * The caller will block, waiting until there are no active vetos. So it waits until the count reaches
     * zero. If the waiting thread is interrupted, it will unblock and throw the InterruptedException.
     *
     * The block will also end if the thread is interrupted, or if it has waited for timeoutMillis
     * milliseconds.
     *
     * This method does not suffer from spurious wakeups.
     *
     * @param timeoutMillis
     * 		maximum time to wait, in milliseconds
     * @throws InterruptedException
     * 		if the thread is interrupted
     */
    public synchronized void waitForNoVetos(long timeoutMillis) throws InterruptedException {
        long now = Instant.now().toEpochMilli();
        long deadline = now + timeoutMillis;
        while (count > 0 && now < deadline) {
            wait(deadline - now);
            now = Instant.now().toEpochMilli();
        }
    }
}
