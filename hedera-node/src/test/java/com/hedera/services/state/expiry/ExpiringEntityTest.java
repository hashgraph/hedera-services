package com.hedera.services.state.expiry;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.state.submerkle.EntityId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class ExpiringEntityTest {

    Consumer<EntityId> consumer, otherConsumer;
    long expiry = 1_234_567L, otherExpiry = 1_567_234L;
    EntityId id, otherId;

    ExpiringEntity subject, other;

    @BeforeEach
    public void setup() {
        consumer = mock(Consumer.class);
        otherConsumer = mock(Consumer.class);

        id = new EntityId(0, 0, 123);
        otherId = new EntityId(0, 0, 456);

        subject = new ExpiringEntity(id, consumer, expiry);
    }

    @Test
    public void validGetters() {
        assertEquals(id, subject.id());
        assertEquals(consumer, subject.consumer());
        assertEquals(expiry, subject.expiry());
    }

    @Test
    public void validEqualityChecks() {
        // expect:
        Assertions.assertEquals(subject, subject);
        // and:
        assertNotEquals(subject, null);
        // and:
        assertNotEquals(subject, new Object());
    }

    @Test
    public void failDifferentExpiry() {
        // given:
        other = new ExpiringEntity(id, consumer, otherExpiry);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentConsumer() {
        // given:
        other = new ExpiringEntity(id, otherConsumer, expiry);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    public void failDifferentId() {
        // given:
        other = new ExpiringEntity(otherId, consumer, expiry);

        // expect:
        assertNotEquals(subject, other);
        // and:
        assertNotEquals(subject.hashCode(), other.hashCode());
    }
}
