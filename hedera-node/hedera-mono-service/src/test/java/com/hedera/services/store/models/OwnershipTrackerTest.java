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
package com.hedera.services.store.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OwnershipTrackerTest {

    private OwnershipTracker subject = new OwnershipTracker();
    private final Id treasury = new Id(1, 2, 3);
    private final Id account = new Id(4, 5, 6);
    private final Id token = new Id(0, 0, 1);

    @BeforeEach
    void setup() {
        // setup the getter/setter test
        subject = new OwnershipTracker();
    }

    @Test
    void add() {
        final var burn = OwnershipTracker.forRemoving(treasury, 1L);

        subject.add(token, burn);
        assertEquals(1, subject.getChanges().size());

        subject.add(token, burn);
        assertEquals(1, subject.getChanges().size());
        assertFalse(subject.isEmpty());
    }

    @Test
    void fromMinting() {
        final var change = OwnershipTracker.forMinting(treasury, 2L);

        assertEquals(treasury, change.getNewOwner());
        assertEquals(Id.DEFAULT, change.getPreviousOwner());
        assertEquals(2L, change.getSerialNumber());
    }

    @Test
    void fromWiping() {
        final var change = OwnershipTracker.forRemoving(account, 2L);

        assertEquals(Id.DEFAULT, change.getNewOwner());
        assertEquals(account, change.getPreviousOwner());
        assertEquals(2L, change.getSerialNumber());
    }

    @Test
    void newChange() {
        final var change = new OwnershipTracker.Change(treasury, Id.DEFAULT, 1L);

        assertEquals(1L, change.getSerialNumber());
        assertEquals(Id.DEFAULT, change.getNewOwner());
        assertEquals(treasury, change.getPreviousOwner());
    }

    @Test
    void compareChanges() {
        final var change = new OwnershipTracker.Change(treasury, account, 7L);
        final var otherChange = new OwnershipTracker.Change(treasury, account, 7L);
        final var refChange = change;

        boolean result = change.equals(null);
        assertFalse(result);
        result = change.equals(treasury);
        assertFalse(result);
        assertEquals(change, refChange);
        assertEquals(change, otherChange);
        assertEquals(change.hashCode(), otherChange.hashCode());
    }
}
