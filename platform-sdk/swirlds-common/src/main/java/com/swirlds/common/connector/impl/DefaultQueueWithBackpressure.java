/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.common.connector.impl;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultQueueWithBackpressure<DATA> implements QueueWithBackpressure<DATA> {

    private final int capacity;

    private ConcurrentLinkedQueue<DATA> innerQueue = new ConcurrentLinkedQueue<>();

    private Lock lock = new ReentrantLock();

    private Condition isFull = lock.newCondition();

    private Condition isNotFull = lock.newCondition();

    public DefaultQueueWithBackpressure(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public void put(DATA data) throws InterruptedException {
        lock.lock();
        try {
            if (innerQueue.size() >= capacity) {
                isFull.await();
            }
            innerQueue.add(data);
            isNotFull.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean offer(DATA data) {
        lock.lock();
        try {
            if (innerQueue.size() >= capacity) {
                return false;
            }
            innerQueue.add(data);
            isNotFull.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void bypassBackpressure(DATA data) {
        lock.lock();
        try {
            innerQueue.add(data);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public DATA take() throws InterruptedException {
        lock.lock();
        try {
            if (innerQueue.isEmpty()) {
                isNotFull.await();
            }
            final DATA data = innerQueue.poll();
            isFull.signal();
            return data;
        } finally {
            lock.unlock();
        }
    }
}
