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

import static com.swirlds.platform.test.components.TransactionHandlingTestUtils.newDummyEvent;
import static com.swirlds.platform.test.components.TransactionHandlingTestUtils.newDummyRound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.test.fixtures.DummySystemTransaction;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.components.transaction.system.SystemTransactionExtractionUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SystemTransactionExtractionUtils}
 */
class SystemTransactionExtractionUtilsTests {
    @Test
    @DisplayName("Handle event")
    void testHandleEvent() {
        final List<ScopedSystemTransaction<DummySystemTransaction>> transactions = new ArrayList<>();
        assertNull(SystemTransactionExtractionUtils.extractFromEvent(newDummyEvent(0), DummySystemTransaction.class));
        transactions.addAll(
                SystemTransactionExtractionUtils.extractFromEvent(newDummyEvent(1), DummySystemTransaction.class));
        transactions.addAll(
                SystemTransactionExtractionUtils.extractFromEvent(newDummyEvent(2), DummySystemTransaction.class));

        assertEquals(3, transactions.size(), "incorrect number of transactions returned");
    }

    @Test
    @DisplayName("Handle round")
    void testHandleRound() {
        final List<ScopedSystemTransaction<DummySystemTransaction>> transactions = new ArrayList<>();
        assertNull(
                SystemTransactionExtractionUtils.extractFromRound(newDummyRound(0, 0), DummySystemTransaction.class));
        transactions.addAll(
                SystemTransactionExtractionUtils.extractFromRound(newDummyRound(1, 1), DummySystemTransaction.class));
        transactions.addAll(
                SystemTransactionExtractionUtils.extractFromRound(newDummyRound(2, 2), DummySystemTransaction.class));

        assertEquals(5, transactions.size(), "incorrect number of transactions returned");
    }
}
