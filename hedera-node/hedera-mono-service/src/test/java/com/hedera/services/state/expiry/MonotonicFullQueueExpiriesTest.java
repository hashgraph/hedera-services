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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonotonicFullQueueExpiriesTest {
    String k1 = "first", k2 = "second", k3 = "third";
    long expiry1 = 50, expiry2 = 100, expiry3 = 1000;

    MonotonicFullQueueExpiries<String> subject;

    @BeforeEach
    void setup() {
        subject = new MonotonicFullQueueExpiries<>();
    }

    @Test
    void throwsOnNonMonotonicClock() {
        // given:
        subject.track(k1, expiry1);

        // expect:
        assertThrows(IllegalArgumentException.class, () -> subject.track(k2, expiry1 - 1));
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
        assertEquals(expiry3, subject.getNow());

        // when:
        var firstExpired = subject.expireNextAt(expiry1);
        var secondExpired = subject.expireNextAt(expiry2);

        // then:
        assertEquals(k1, firstExpired);
        assertEquals(k2, secondExpired);
        // and:
        assertEquals(1, subject.getAllExpiries().size());
        assertFalse(subject.hasExpiringAt(expiry2));
        assertTrue(subject.hasExpiringAt(expiry3));
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
    void expiryInNonMonotonicOrder() {
        String k4 = "fourth";
        long expiry1 = 50, expiry2 = 1000, expiry3 = 200, expiry4 = 10;
        // given:
        subject.track(k1, expiry1);
        subject.track(k2, expiry2);
        assertThrows(IllegalArgumentException.class, () -> subject.track(k3, expiry3));
        assertThrows(IllegalArgumentException.class, () -> subject.track(k4, expiry4));

        // expect:
        assertEquals(expiry2, subject.getNow());

        // when:
        var firstExpired = subject.expireNextAt(expiry1);
        var secondExpired = subject.expireNextAt(expiry2);

        // then:
        assertEquals(k1, firstExpired);
        assertEquals(k2, secondExpired);
        // and:
        assertEquals(0, subject.getAllExpiries().size());
        assertFalse(subject.hasExpiringAt(expiry2));
        assertThrows(IllegalStateException.class, () -> subject.expireNextAt(expiry3));
    }
}
