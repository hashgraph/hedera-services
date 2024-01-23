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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.test.fixtures.DummySystemTransaction;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.components.transaction.system.SystemTransactionExtractor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SystemTransactionExtractorTests {

    @Test
    @DisplayName("tests handling system transactions")
    void testHandle() {
        final SystemTransactionExtractor<DummySystemTransaction> manager =
                new SystemTransactionExtractor<>(DummySystemTransaction.class);

        final List<ScopedSystemTransaction<DummySystemTransaction>> transactions = new ArrayList<>();
        assertNull(manager.handleEvent(newDummyEvent(0)));
        transactions.addAll(manager.handleEvent(newDummyEvent(1)));
        transactions.addAll(manager.handleEvent(newDummyEvent(2)));

        assertEquals(3, transactions.size(), "incorrect number of transactions returned");
    }
}
