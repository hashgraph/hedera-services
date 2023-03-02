/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import com.swirlds.platform.event.EventIntakeTask;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class DummyEventQueue implements BlockingQueue<EventIntakeTask> {
    private DummyHashgraph hashgraph;

    public DummyEventQueue(DummyHashgraph hashgraph) {
        this.hashgraph = hashgraph;
    }

    @Override
    public boolean add(final EventIntakeTask eventIntakeTask) {
        return false;
    }

    @Override
    public boolean offer(final EventIntakeTask eventIntakeTask) {
        return false;
    }

    @Override
    public EventIntakeTask remove() {
        return null;
    }

    @Override
    public EventIntakeTask poll() {
        return null;
    }

    @Override
    public EventIntakeTask element() {
        return null;
    }

    @Override
    public EventIntakeTask peek() {
        return null;
    }

    @Override
    public void put(final EventIntakeTask eventIntakeTask) throws InterruptedException {}

    @Override
    public boolean offer(final EventIntakeTask eventIntakeTask, final long timeout, final TimeUnit unit)
            throws InterruptedException {
        return false;
    }

    @Override
    public EventIntakeTask take() throws InterruptedException {
        return null;
    }

    @Override
    public EventIntakeTask poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        return null;
    }

    @Override
    public int remainingCapacity() {
        return 0;
    }

    @Override
    public boolean remove(final Object o) {
        return false;
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(final Collection<? extends EventIntakeTask> c) {
        return false;
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {}

    @Override
    public int size() {
        return hashgraph.eventIntakeQueueSize;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(final Object o) {
        return false;
    }

    @Override
    public Iterator<EventIntakeTask> iterator() {
        return null;
    }

    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @Override
    public <T> T[] toArray(final T[] a) {
        return null;
    }

    @Override
    public int drainTo(final Collection<? super EventIntakeTask> c) {
        return 0;
    }

    @Override
    public int drainTo(final Collection<? super EventIntakeTask> c, final int maxElements) {
        return 0;
    }
}
