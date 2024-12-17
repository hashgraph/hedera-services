/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading;

import com.swirlds.common.threading.framework.internal.AbstractBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This queue implementation allows us to artificially cause poll() to block.
 */
public class ControllableQueue extends AbstractBlockingQueue<Integer> {
    private final ReentrantLock pollLock = new ReentrantLock();
    private final AtomicInteger pollBlockedCount = new AtomicInteger(0);

    public ControllableQueue() {
        super(new LinkedBlockingQueue<>());
    }

    @Override
    public synchronized Integer poll(long timeout, TimeUnit unit) throws InterruptedException {
        pollBlockedCount.incrementAndGet();
        pollLock.lock();
        pollLock.unlock();
        pollBlockedCount.decrementAndGet();
        return super.poll(timeout, unit);
    }

    /**
     * Cause poll() to block forever on all threads.
     */
    public synchronized void blockPolling() {
        pollLock.lock();
    }

    /**
     * Allow poll() to proceed.
     */
    public void unblockPolling() {
        pollLock.unlock();
    }

    /**
     * Get the number of threads currently waiting to acquire the poll lock.
     */
    public int getPollBlockedCount() {
        return pollBlockedCount.get();
    }
}
