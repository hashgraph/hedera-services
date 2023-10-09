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

package com.swirlds.common.wiring.components;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class EventPool {
    private static final int CAPACITY = 10000;
    private final BlockingQueue<Event> pool = new ArrayBlockingQueue<>(CAPACITY);

    public EventPool() {
        for (int i = 0; i < CAPACITY; i++) {
            pool.add(new Event());
        }
    }

    public Event checkout(long number) {
        try {
            Event event = pool.take(); // TODO
            event.reset(number);
            return event;
        } catch (InterruptedException iex) {
            throw new RuntimeException(iex);
        }
    }

    public void checkin(Event event) {
        pool.add(event);
    }
}
