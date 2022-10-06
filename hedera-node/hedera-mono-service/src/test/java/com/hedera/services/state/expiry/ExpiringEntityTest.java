/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

import com.hedera.services.state.submerkle.EntityId;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpiringEntityTest {
    private static final long expiry = 1_234_567L;
    private static final long otherExpiry = 1_567_234L;
    private static final EntityId id = new EntityId(0, 0, 123);
    private static final EntityId otherId = new EntityId(0, 0, 456);
    private Consumer<EntityId> consumer, otherConsumer;

    private ExpiringEntity subject;

    @BeforeEach
    void setup() {
        consumer = mock(Consumer.class);
        otherConsumer = mock(Consumer.class);
        subject = new ExpiringEntity(id, consumer, expiry);
    }

    @Test
    void validGetters() {
        assertEquals(id, subject.id());
        assertEquals(consumer, subject.consumer());
        assertEquals(expiry, subject.expiry());
    }

    @Test
    void validEqualityChecks() {
        assertEquals(subject, subject);
        assertNotEquals(null, subject);
        assertNotEquals(new Object(), subject);
    }

    @Test
    void failDifferentExpiry() {
        final var other = new ExpiringEntity(id, consumer, otherExpiry);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void failDifferentConsumer() {
        final var other = new ExpiringEntity(id, otherConsumer, expiry);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void failDifferentId() {
        final var other = new ExpiringEntity(otherId, consumer, expiry);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }
}
