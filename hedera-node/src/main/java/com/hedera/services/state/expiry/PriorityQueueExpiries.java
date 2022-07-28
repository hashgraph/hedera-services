/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.expiry;

import java.util.Comparator;
import java.util.PriorityQueue;

/** Priority Queue of expiration events in which events are ordered based on expiry */
public class PriorityQueueExpiries<K> implements KeyedExpirations<K> {
    private long now = 0L;
    private final PriorityQueue<ExpiryEvent<K>> allExpiries;

    public PriorityQueueExpiries(Comparator<ExpiryEvent<K>> eventCmp) {
        allExpiries = new PriorityQueue<>(eventCmp);
    }

    @Override
    public void reset() {
        now = 0L;
        allExpiries.clear();
    }

    @Override
    public void track(K id, long expiry) {
        allExpiries.offer(new ExpiryEvent<>(id, expiry));
        now = allExpiries.peek().expiry();
    }

    @Override
    public boolean hasExpiringAt(long now) {
        return !allExpiries.isEmpty() && allExpiries.peek().isExpiredAt(now);
    }

    @Override
    public K expireNextAt(long now) {
        if (allExpiries.isEmpty()) {
            throw new IllegalStateException("No ids are queued for expiration!");
        }
        if (!allExpiries.peek().isExpiredAt(now)) {
            throw new IllegalArgumentException(
                    String.format("Argument 'now=%d' is earlier than the next expiry!", now));
        }
        return allExpiries.remove().id();
    }

    PriorityQueue<ExpiryEvent<K>> getAllExpiries() {
        return allExpiries;
    }

    long getNow() {
        return now;
    }
}
