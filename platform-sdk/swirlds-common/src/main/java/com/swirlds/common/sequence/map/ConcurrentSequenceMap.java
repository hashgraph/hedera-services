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

package com.swirlds.common.sequence.map;

import com.swirlds.common.sequence.map.internal.AbstractSequenceMap;
import com.swirlds.common.threading.locks.IndexLock;
import com.swirlds.common.threading.locks.Locks;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.ToLongFunction;

/**
 * A thread safe implementation of {@link SequenceMap}.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public class ConcurrentSequenceMap<K, V> extends AbstractSequenceMap<K, V> {

    private static final int MAX_PARALLELISM = 1024;
    private final AtomicLong firstSequenceNumberInWindow;
    private final int parallelism;

    /**
     * When inserting data into this data structure, it is critical that data is not inserted after the
     * data's sequence number has been purged (as this would lead to a memory leak). Whenever new data is inserted,
     * acquire a lock that prevents concurrent purging of that sequence number.
     */
    private final IndexLock lock;

    private final Lock windowLock = new ReentrantLock();

    /**
     * Construct a thread safe {@link SequenceMap}.
     *
     * @param firstSequenceNumberInWindow
     * 		the lowest allowed sequence number
     * @param sequenceNumberCapacity
     * 		the number of sequence numbers permitted to exist in this data structure. E.g. if
     * 		the lowest allowed sequence number is 100 and the capacity is 10, then values with
     * 		a sequence number between 100 and 109 (inclusive) will be allowed, and any value
     * 		with a sequence number outside that range will be rejected.
     * @param getSequenceNumberFromKey
     * 		a method that extracts the sequence number from a key
     */
    public ConcurrentSequenceMap(
            final long firstSequenceNumberInWindow,
            final int sequenceNumberCapacity,
            final ToLongFunction<K> getSequenceNumberFromKey) {

        super(firstSequenceNumberInWindow, sequenceNumberCapacity, getSequenceNumberFromKey);

        parallelism = Math.min(MAX_PARALLELISM, sequenceNumberCapacity);
        lock = Locks.createIndexLock(parallelism);

        this.firstSequenceNumberInWindow = new AtomicLong(firstSequenceNumberInWindow);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void lockSequenceNumber(final long sequenceNumber) {
        lock.lock(sequenceNumber);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void unlockSequenceNumber(final long sequenceNumber) {
        lock.unlock(sequenceNumber);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void fullLock() {
        lock.fullyLock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void fullUnlock() {
        lock.fullyUnlock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getFullLockThreshold() {
        return parallelism;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void windowLock() {
        windowLock.lock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void windowUnlock() {
        windowLock.unlock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFirstSequenceNumberInWindow() {
        return firstSequenceNumberInWindow.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setFirstSequenceNumberInWindow(final long firstSequenceNumberInWindow) {
        this.firstSequenceNumberInWindow.set(firstSequenceNumberInWindow);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<K, V> buildDataMap() {
        return new ConcurrentHashMap<>();
    }
}
