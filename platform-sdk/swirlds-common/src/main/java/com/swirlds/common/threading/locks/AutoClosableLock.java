/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.locks;

import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * Similar to {@link java.util.concurrent.locks.Lock} but intended to be used by the try-with-resources statement.
 * Returns an object that will release the lock automatically at the end of the try block.
 */
public interface AutoClosableLock {

    /**
     * Acquires the lock, blocking until the lock becomes available.
     *
     * @return an instance used to release the lock
     */
    Locked lock();

    /**
     * Same as {@link #lock()}, but can unblock if interrupted
     */
    Locked lockInterruptibly() throws InterruptedException;

    /**
     * Tries to acquire the lock if it is available. Returns immediately
     *
     * @return an instance that tells the caller if the lock has been acquired and provides a method to unlock it if it
     * 		has
     */
    MaybeLocked tryLock();

    /**
     * {@link #tryLock()} but with a timeout
     */
    MaybeLocked tryLock(long time, TimeUnit unit) throws InterruptedException;

    /**
     * Returns a new {@link Condition} instance that is bound to this
     * {@code Lock} instance.
     *
     * <p>Before waiting on the condition the lock must be held by the
     * current thread.
     * A call to {@link Condition#await()} will atomically release the lock
     * before waiting and re-acquire the lock before the wait returns.
     *
     * @return A new {@link Condition} instance for this {@code Lock} instance
     */
    Condition newCondition();
}
