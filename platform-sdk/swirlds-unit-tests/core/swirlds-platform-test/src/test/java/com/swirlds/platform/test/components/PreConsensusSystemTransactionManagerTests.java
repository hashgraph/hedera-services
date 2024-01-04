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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.test.fixtures.DummySystemTransaction;
import com.swirlds.platform.components.transaction.system.PreconsensusSystemTransactionHandler;
import com.swirlds.platform.components.transaction.system.PreconsensusSystemTransactionManager;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreConsensusSystemTransactionManagerTests {

    @Test
    @DisplayName("tests that exceptions are handled gracefully")
    void testHandleExceptions() {
        PreconsensusSystemTransactionHandler<DummySystemTransaction> consumer = (dummySystemTransaction, aLong) -> {
            throw new IllegalStateException("this is intentionally thrown");
        };

        final PreconsensusSystemTransactionManager manager = new PreconsensusSystemTransactionManager();
        manager.addHandler(DummySystemTransaction.class, consumer);

        assertDoesNotThrow(() -> manager.handleEvent(newDummyEvent(1)));
    }

    @Test
    @DisplayName("tests handling system transactions")
    void testHandle() {
        final AtomicInteger handleCount = new AtomicInteger(0);

        PreconsensusSystemTransactionHandler<DummySystemTransaction> consumer =
                (dummySystemTransaction, aLong) -> handleCount.getAndIncrement();

        final PreconsensusSystemTransactionManager manager = new PreconsensusSystemTransactionManager();
        manager.addHandler(DummySystemTransaction.class, consumer);

        manager.handleEvent(newDummyEvent(0));
        manager.handleEvent(newDummyEvent(1));
        manager.handleEvent(newDummyEvent(2));

        assertEquals(3, handleCount.get(), "incorrect number of handle calls");
    }

    @Test
    @DisplayName("tests handling system transactions, where no handle method has been defined")
    void testNoHandleMethod() {
        final PreconsensusSystemTransactionManager manager = new PreconsensusSystemTransactionManager();

        assertDoesNotThrow(() -> manager.handleEvent(newDummyEvent(1)), "should not throw");
    }
}
