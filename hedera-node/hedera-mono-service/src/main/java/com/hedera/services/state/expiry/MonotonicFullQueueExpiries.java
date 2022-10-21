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

import java.util.ArrayDeque;
import java.util.Deque;

/** Queue of expiration events in which events are in the order of insertion */
public class MonotonicFullQueueExpiries<K> implements KeyedExpirations<K> {
    private long now = 0L;
    private Deque<ExpiryEvent<K>> allExpiries = new ArrayDeque<>();

    @Override
    public void reset() {
        now = 0L;
        allExpiries.clear();
    }

    @Override
    public void track(K id, long expiry) {
        if (expiry < now) {
            throw new IllegalArgumentException(
                    String.format("Track time %d for %s not later than %d", expiry, id, now));
        }
        now = expiry;
        allExpiries.add(new ExpiryEvent<>(id, expiry));
    }

    @Override
    public boolean hasExpiringAt(long now) {
        return !allExpiries.isEmpty() && allExpiries.peekFirst().isExpiredAt(now);
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
        return allExpiries.removeFirst().id();
    }

    Deque<ExpiryEvent<K>> getAllExpiries() {
        return allExpiries;
    }

    long getNow() {
        return now;
    }
}
