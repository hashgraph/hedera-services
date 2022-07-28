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
package com.hedera.services.state.merkle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.exceptions.MutabilityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerkleScheduledTransactionsStateTest {

    private static final long currentMinSecond = 23;

    private MerkleScheduledTransactionsState subject;

    @BeforeEach
    void setup() {
        subject = new MerkleScheduledTransactionsState(currentMinSecond);
    }

    @Test
    void toStringWorks() {
        final var desired = "MerkleScheduledTransactionsState{currentMinSecond=23}";
        assertEquals(desired, subject.toString());
    }

    @Test
    void copyIsImmutable() {
        subject.copy();

        assertThrows(MutabilityException.class, () -> subject.setCurrentMinSecond(1L));
    }

    @Test
    void reportsCurrentMinSecond() {
        assertEquals(currentMinSecond, subject.currentMinSecond());
        subject.setCurrentMinSecond(2L);
        assertEquals(2L, subject.currentMinSecond());
    }

    @Test
    void copyWorks() {
        final var copySubject = subject.copy();
        assertNotSame(copySubject, subject);
        assertEquals(subject, copySubject);
    }

    @Test
    void equalsWorksWithRadicalDifferences() {
        final var identical = subject;
        assertEquals(subject, identical);
        Object nul = null;
        assertNotEquals(subject, nul);
        assertNotEquals(subject, new Object());
    }

    @Test
    void equalsWorksForCurrentMinSecond() {
        final var otherSubject = subject.copy();
        otherSubject.setCurrentMinSecond(2L);

        assertNotEquals(subject, otherSubject);
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(MerkleScheduledTransactionsState.RELEASE_0270_VERSION, subject.getVersion());
        assertEquals(MerkleScheduledTransactionsState.CURRENT_VERSION, subject.getVersion());
        assertEquals(
                MerkleScheduledTransactionsState.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
        assertTrue(subject.isLeaf());
    }

    @Test
    void objectContractMet() {
        final var defaultSubject = new MerkleScheduledTransactionsState();
        final var identicalSubject = subject.copy();
        assertNotEquals(subject.hashCode(), defaultSubject.hashCode());
        assertEquals(subject.hashCode(), identicalSubject.hashCode());
    }
}
