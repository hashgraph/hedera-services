/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.swirlds.common.test.fixtures.DummySystemTransaction;
import com.swirlds.platform.components.transaction.system.ConsensusSystemTransactionHandler;
import com.swirlds.platform.components.transaction.system.ConsensusSystemTransactionManager;
import com.swirlds.platform.state.State;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsensusSystemTransactionManagerTests {

    @Test
    @DisplayName("tests that exceptions are handled gracefully")
    void testHandleExceptions() {
        ConsensusSystemTransactionHandler<DummySystemTransaction> consumer =
                (state, dummySystemTransaction, aLong, version) -> {
                    throw new IllegalStateException("this is intentionally thrown");
                };

        final ConsensusSystemTransactionManager manager = new ConsensusSystemTransactionManager();
        manager.addHandler(DummySystemTransaction.class, consumer);

        assertDoesNotThrow(() -> manager.handleRound(mock(State.class), newDummyRound(List.of(1))));
    }

    @Test
    @DisplayName("tests handling system transactions")
    void testHandle() {
        final AtomicInteger handleCount = new AtomicInteger(0);

        ConsensusSystemTransactionHandler<DummySystemTransaction> consumer =
                (state, dummySystemTransaction, aLong, version) -> handleCount.getAndIncrement();

        final ConsensusSystemTransactionManager manager = new ConsensusSystemTransactionManager();
        manager.addHandler(DummySystemTransaction.class, consumer);

        manager.handleRound(mock(State.class), newDummyRound(List.of(0)));
        manager.handleRound(mock(State.class), newDummyRound(List.of(2)));
        manager.handleRound(mock(State.class), newDummyRound(List.of(0, 1, 3)));

        assertEquals(6, handleCount.get(), "incorrect number of post-handle calls");
    }

    @Test
    @DisplayName("tests handling system transactions, where no handle method has been defined")
    void testNoHandleMethod() {
        final ConsensusSystemTransactionManager manager = new ConsensusSystemTransactionManager();

        assertDoesNotThrow(
                () -> manager.handleRound(mock(State.class), newDummyRound(List.of(1))), "should not throw ");
    }
}
