/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.components;

import static com.swirlds.platform.test.components.TransactionHandlingTestUtils.newDummyRound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.components.transaction.system.SystemTransactionExtractionUtils;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SystemTransactionExtractionUtils}
 */
class SystemTransactionExtractionUtilsTests {
    @Test
    @DisplayName("Handle event")
    void testHandleEvent() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final List<ScopedSystemTransaction<StateSignatureTransaction>> transactions = new ArrayList<>();
        assertNull(SystemTransactionExtractionUtils.extractFromEvent(
                new TestingEventBuilder(r).setSystemTransactionCount(0).build(), StateSignatureTransaction.class));
        transactions.addAll(Objects.requireNonNull(SystemTransactionExtractionUtils.extractFromEvent(
                new TestingEventBuilder(r).setSystemTransactionCount(1).build(), StateSignatureTransaction.class)));
        transactions.addAll(Objects.requireNonNull(SystemTransactionExtractionUtils.extractFromEvent(
                new TestingEventBuilder(r).setSystemTransactionCount(2).build(), StateSignatureTransaction.class)));

        transactions.forEach(t -> assertTrue(StateSignatureTransaction.class.isInstance(t.transaction())));
        assertEquals(3, transactions.size(), "incorrect number of transactions returned");
    }

    @Test
    @DisplayName("Handle round")
    void testHandleRound() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final List<ScopedSystemTransaction<StateSignatureTransaction>> transactions = new ArrayList<>();
        assertNull(SystemTransactionExtractionUtils.extractFromRound(
                newDummyRound(r, 0, 0), StateSignatureTransaction.class));
        transactions.addAll(SystemTransactionExtractionUtils.extractFromRound(
                newDummyRound(r, 1, 1), StateSignatureTransaction.class));
        transactions.addAll(SystemTransactionExtractionUtils.extractFromRound(
                newDummyRound(r, 2, 2), StateSignatureTransaction.class));

        transactions.forEach(t -> assertTrue(StateSignatureTransaction.class.isInstance(t.transaction())));
        assertEquals(5, transactions.size(), "incorrect number of transactions returned");
    }
}
