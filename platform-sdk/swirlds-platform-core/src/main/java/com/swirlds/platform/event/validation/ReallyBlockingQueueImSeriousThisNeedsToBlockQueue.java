/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.validation;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

// TODO add final and nullity annotations

/**
 * A special queue implementation where offer() behaves just like put(). Needed to hack an executor service
 * so that submit() blocks when the queue is full.
 * @param <T>
 */
public class ReallyBlockingQueueImSeriousThisNeedsToBlockQueue<T> implements BlockingQueue<T> {

    private final BlockingQueue<T> baseQueue;

    public ReallyBlockingQueueImSeriousThisNeedsToBlockQueue(@NonNull final BlockingQueue<T> baseQueue) {
        this.baseQueue = baseQueue;
    }

    @Override
    public boolean add(T t) {
        return baseQueue.add(t);
    }

    @Override
    public boolean offer(T t) {
        // intentional
        try {
            baseQueue.put(t);
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while adding to blocking queue", e);
        }
    }

    @Override
    public T remove() {
        return baseQueue.remove();
    }

    @Override
    public T poll() {
        return baseQueue.poll();
    }

    @Override
    public T element() {
        return baseQueue.element();
    }

    @Override
    public T peek() {
        return baseQueue.peek();
    }

    @Override
    public void put(T t) throws InterruptedException {
        baseQueue.put(t);
    }

    @Override
    public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
        // intentional
        baseQueue.put(t);
        return true;
    }

    @Override
    public T take() throws InterruptedException {
        return baseQueue.take();
    }

    @Override
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        return baseQueue.poll(timeout, unit);
    }

    @Override
    public int remainingCapacity() {
        return baseQueue.remainingCapacity();
    }

    @Override
    public boolean remove(Object o) {
        return baseQueue.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return baseQueue.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return baseQueue.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return baseQueue.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return baseQueue.retainAll(c);
    }

    @Override
    public void clear() {
        baseQueue.clear();
    }

    @Override
    public boolean equals(Object o) {
        return baseQueue.equals(o);
    }

    @Override
    public int hashCode() {
        return baseQueue.hashCode();
    }

    @Override
    public int size() {
        return baseQueue.size();
    }

    @Override
    public boolean isEmpty() {
        return baseQueue.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return baseQueue.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return baseQueue.iterator();
    }

    @Override
    public Object[] toArray() {
        return baseQueue.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return baseQueue.toArray(a);
    }

    @Override
    public int drainTo(Collection<? super T> c) {
        return baseQueue.drainTo(c);
    }

    @Override
    public int drainTo(Collection<? super T> c, int maxElements) {
        return baseQueue.drainTo(c, maxElements);
    }
}
