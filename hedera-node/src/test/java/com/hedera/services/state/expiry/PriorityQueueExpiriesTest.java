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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PriorityQueueExpiriesTest {
    private String k1 = "first", k2 = "second", k3 = "third";
    private long expiry1 = 50, expiry2 = 1000, expiry3 = 200;

    private PriorityQueueExpiries<String> subject;
    private Comparator<ExpiryEvent<String>> testCmp =
            (aEvent, bEvent) -> {
                int order;
                return (order = Long.compare(aEvent.expiry(), bEvent.expiry())) != 0
                        ? order
                        : aEvent.id().compareTo(bEvent.id());
            };

    @BeforeEach
    void setup() {
        subject = new PriorityQueueExpiries<>(testCmp);
    }

    @Test
    void succeedsOnNonMonotonicClock() {
        // given:
        subject.track(k1, expiry1);

        // given:
        subject.track(k2, expiry1 - 1);

        // expect:
        assertTrue(subject.hasExpiringAt(expiry1 - 1));
        assertEquals(expiry1 - 1, subject.getNow());
        assertEquals(expiry1 - 1, subject.getAllExpiries().peek().expiry());
        assertEquals(k2, subject.expireNextAt(expiry1 - 1));
    }

    @Test
    void behavesWithValidOps() {
        // given:
        subject.track(k1, expiry1);
        subject.track(k2, expiry2);
        subject.track(k3, expiry3);

        // expect:
        assertTrue(subject.hasExpiringAt(expiry1 + 1));
        assertFalse(subject.hasExpiringAt(expiry1 - 1));
        // and:
        assertEquals(expiry1, subject.getNow());

        // when:
        var firstExpired = subject.expireNextAt(expiry1);
        var secondExpired = subject.expireNextAt(expiry3);

        // then:
        assertEquals(k1, firstExpired);
        assertEquals(k3, secondExpired);
        // and:
        assertEquals(1, subject.getAllExpiries().size());
        assertFalse(subject.hasExpiringAt(expiry3));
        assertTrue(subject.hasExpiringAt(expiry2));
    }

    @Test
    void resetWorks() {
        // given:
        subject.track(k1, expiry1);

        // when:
        subject.reset();

        // then:
        assertTrue(subject.getAllExpiries().isEmpty());
        assertEquals(0L, subject.getNow());
    }

    @Test
    void throwsIfNextExpiryIsFuture() {
        // given:
        subject.track(k1, expiry1);

        // expect:
        assertThrows(IllegalArgumentException.class, () -> subject.expireNextAt(expiry1 - 1));
    }

    @Test
    void throwsIfNoPossibleExpiries() {
        // expect:
        assertThrows(IllegalStateException.class, () -> subject.expireNextAt(expiry1));
    }

    @Test
    void noExpiringIfEmpty() {
        // expect:
        assertFalse(subject.hasExpiringAt(expiry1));
    }

    @Test
    void expiryFromNewPqWithDifferentGrowthOrderIsDeterministic() {
        // setup:
        /* Total number of expiring events to put in the PriorityQueueExpiries */
        var entitiesToTrack = 33;
        /* Number of consecutive events with the same expiration time. */
        var reuseOfEachExpiryTime = 3;
        /* Number of events to expire before putting what is left over in a second priority queue */
        var entitiesToRemoveBeforeRebuild = 12;

        // given:
        long startTime = 1L;
        List<ExpiryEvent<String>> events =
                buildTestEvents(entitiesToTrack, reuseOfEachExpiryTime, startTime);
        // and:
        var fullPq = pqFrom(events);

        // when:
        for (int i = 0; i < entitiesToRemoveBeforeRebuild; i++) {
            long now = events.get(i).expiry();
            fullPq.expireNextAt(now);
        }
        // and:
        var partialPq = pqFrom(events.subList(entitiesToRemoveBeforeRebuild, entitiesToTrack));

        // then:
        for (int i = entitiesToRemoveBeforeRebuild; i < entitiesToTrack; i++) {
            long now = events.get(i).expiry();
            var fromFull = fullPq.expireNextAt(now);
            var fromPartial = partialPq.expireNextAt(now);
            assertEquals(
                    fromFull,
                    fromPartial,
                    "The purge order of keys with the same expiry should be deterministic");
        }
    }

    private PriorityQueueExpiries<String> pqFrom(List<ExpiryEvent<String>> events) {
        var pq = new PriorityQueueExpiries<>(testCmp);
        for (var event : events) {
            pq.track(event.id(), event.expiry());
        }
        return pq;
    }

    private List<ExpiryEvent<String>> buildTestEvents(int n, int reuses, long start) {
        List<ExpiryEvent<String>> events = new ArrayList<>();
        var id = 0;
        var now = start;
        var reusesLeft = reuses;
        while (n > 0) {
            var name = "Event" + (id++);
            if (reusesLeft == 0) {
                now++;
                reusesLeft = reuses;
            } else {
                reusesLeft--;
            }
            events.add(new ExpiryEvent<>(name, now));
            n--;
        }
        return events;
    }
}
