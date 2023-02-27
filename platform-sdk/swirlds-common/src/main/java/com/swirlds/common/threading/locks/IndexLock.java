/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

/**
 * <p>
 * This interface provides a useful abstraction of locking on an index value. That is, two threads could lock on the
 * primitive value "17" and will contend for the same lock.
 * </p>
 *
 * <p>
 * It is possible that a lock on two different indices will contend for the same lock. That probability
 * can be reduced by increasing the parallelism, at the cost of additional memory overhead.
 * </p>
 *
 * <p>
 * The locks acquired by this interface are reentrant.
 * </p>
 */
public interface IndexLock {

    /**
     * Lock on a given index value. May contend for the same lock as other index values.
     *
     * @param index
     * 		the value to lock
     */
    void lock(final long index);

    /**
     * Lock using the hash code of an object as the index. Two objects with the same hash code will contend
     * for the same lock.
     *
     * @param object
     * 		the object to lock, can be null
     */
    void lock(final Object object);

    /**
     * Unlock on a given index value.
     *
     * @param index
     * 		the value to unlock
     */
    void unlock(final long index);

    /**
     * Unlock using the hash code of an object as the index. Two objects with the same hash code will contend
     * for the same lock.
     *
     * @param object
     * 		the object to unlock, can be null
     */
    void unlock(final Object object);

    /**
     * Acquire a lock and return an autocloseable object that will release the lock.
     *
     * @param index
     * 		the index to lock
     * @return an object that will unlock the lock once it is closed
     */
    Locked autoLock(final long index);

    /**
     * Acquire a lock and return an autocloseable object that will release the lock. Uses the hash
     * code of the provided object.
     *
     * @param object
     * 		the object to lock, can be null
     * @return an object that will unlock the lock once it is closed
     */
    Locked autoLock(final Object object);

    /**
     * Lock every index. This is expensive, use with caution.
     */
    void fullyLock();

    /**
     * Lock every index. This is expensive, use with caution.
     */
    void fullyUnlock();

    /**
     * Acquire a lock on every index and return an autocloseable object that will release the lock.
     * This is expensive, use with caution.
     *
     * @return an object that will unlock the lock once it is closed
     */
    Locked autoFullLock();
}
