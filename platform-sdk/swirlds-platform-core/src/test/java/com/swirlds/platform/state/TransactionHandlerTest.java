/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.TransactionUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionHandlerTest {

    private static final List<Supplier<Exception>> EXCEPTIONS = List.of(
            () -> new IllegalStateException("intentionally thrown"),
            () -> new NullPointerException("intentionally thrown"),
            () -> new RuntimeException("intentionally thrown"));

    private final NodeId selfId = new NodeId(false, 0L);

    private final State state = mock(State.class);
    private final SwirldState swirldState = mock(SwirldState.class);
    private final SwirldDualState dualState = mock(SwirldDualState.class);

    private TransactionHandler handler;

    @BeforeEach
    void setup() {
        when(state.getSwirldDualState()).thenReturn(dualState);

        handler = new TransactionHandler(selfId, mock(SwirldStateMetrics.class));
    }

    @Test
    @DisplayName("preHandle() invokes SwirldState.preHandle() with the correct arguments")
    void testSwirldStatePreHandle() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final EventImpl event = newEvent(TransactionUtils.incrementingMixedTransactions(r));

        handler.preHandle(event, swirldState);

        verify(swirldState, times(1).description("preHandle() invoked incorrect number of times"))
                .preHandle(event);
    }

    private static EventImpl newEvent(final ConsensusTransactionImpl[] transactions) {
        return new EventImpl(
                new BaseEventHashedData(
                        0L,
                        0L,
                        0L,
                        CryptographyHolder.get().getNullHash(),
                        CryptographyHolder.get().getNullHash(),
                        Instant.now(),
                        transactions),
                new BaseEventUnhashedData(0L, new byte[0]));
    }
}
