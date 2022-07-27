/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.ledger.accounts.TestAccount;
import com.hedera.services.ledger.properties.TestAccountProperty;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityChangeSetTest {
    private static final TestAccount a = new TestAccount(666L, new Object(), false, 42L);

    private EntityChangeSet<Long, TestAccount, TestAccountProperty> subject =
            new EntityChangeSet<>();

    @Test
    void canAddChanges() {
        final Map<TestAccountProperty, Object> twoChanges = Map.of(TestAccountProperty.FLAG, false);
        subject.include(1L, null, oneChanges);
        subject.include(2L, a, twoChanges);

        assertEquals(2, subject.size());
        assertChangeAt(0, 1L, null, oneChanges);
        assertChangeAt(1, 2L, a, twoChanges);
    }

    @Test
    void toStringWorks() {
        final Map<TestAccountProperty, Object> twoChanges = Map.of(TestAccountProperty.FLAG, false);
        subject.include(1L, null, oneChanges);
        subject.include(2L, a, twoChanges);

        assertNotEquals("", subject.toString());
    }

    @Test
    void canUpdateCachedEntity() {
        final Map<TestAccountProperty, Object> twoChanges = Map.of(TestAccountProperty.FLAG, false);
        subject.include(1L, null, oneChanges);
        subject.include(2L, null, twoChanges);
        subject.cacheEntity(1, a);
        assertSame(a, subject.entity(1));
    }

    @Test
    void canClearChanges() {
        subject.include(1L, null, oneChanges);

        subject.clear();

        assertTrue(subject.getIds().isEmpty());
        assertTrue(subject.getEntities().isEmpty());
        assertTrue(subject.getChanges().isEmpty());
    }

    @Test
    void distinguishesBetweenRetainsAndRemovals() {
        final Map<TestAccountProperty, Object> twoChanges = Map.of(TestAccountProperty.FLAG, false);
        subject.include(1L, null, oneChanges);
        subject.includeRemoval(2L, a);
        subject.include(3L, null, twoChanges);

        assertEquals(3, subject.size());
        assertEquals(2, subject.retainedSize());

        subject.clear();

        assertEquals(0, subject.retainedSize());
    }

    private void assertChangeAt(
            final int i,
            final long k,
            final TestAccount a,
            final Map<TestAccountProperty, Object> p) {
        assertEquals(k, subject.id(i));
        assertEquals(a, subject.entity(i));
        assertEquals(p, subject.changes(i));
    }

    private static final Map<TestAccountProperty, Object> oneChanges =
            Map.of(TestAccountProperty.FLAG, false);
}
