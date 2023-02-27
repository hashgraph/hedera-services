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

import static com.swirlds.platform.consensus.ConsensusConstants.MIN_TRANS_TIMESTAMP_INCR_NANOS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState1;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.TransactionUtils;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final SwirldState1 swirldState1 = mock(SwirldState1.class);
    private final SwirldState2 swirldState2 = mock(SwirldState2.class);
    private final SwirldDualState dualState = mock(SwirldDualState.class);

    private TransactionHandler handler;

    @BeforeEach
    void setup() {
        when(state.getSwirldState()).thenReturn(swirldState1);
        when(state.getSwirldDualState()).thenReturn(dualState);

        handler = new TransactionHandler(selfId, mock(SwirldStateMetrics.class));
    }

    @Test
    @DisplayName("preHandle() invokes SwirldState1.preHandle() with the correct arguments")
    void testSwirldState1PreHandle() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final EventImpl event = newEvent(TransactionUtils.incrementingMixedTransactions(r));
        final Transaction[] transactions = event.getTransactions();

        handler.preHandle(event, swirldState1);

        int numInvocations;
        for (int i = 0; i < transactions.length; i++) {

            final Transaction transaction = transactions[i];

            numInvocations = 1;
            if (transaction.isSystem()) {
                numInvocations = 0;
            }

            verify(
                            swirldState1,
                            times(numInvocations)
                                    .description(
                                            "preHandle() invoked incorrect number of times on transaction index " + i))
                    .preHandle(transaction);
        }
    }

    @Test
    @DisplayName("preHandle() invokes SwirldState2.preHandle() with the correct arguments")
    void testSwirldState2PreHandle() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final EventImpl event = newEvent(TransactionUtils.incrementingMixedTransactions(r));

        handler.preHandle(event, swirldState2);

        verify(swirldState2, times(1).description("preHandle() invoked incorrect number of times"))
                .preHandle(event);
    }

    @Test
    @DisplayName("handlePreConsensusEvent() invokes SwirldState.handlePreConsensusEvent() with the correct arguments")
    void testHandlePreConsensusEvent() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final EventImpl event = newEvent(TransactionUtils.incrementingMixedTransactions(r));
        final Transaction[] transactions = event.getTransactions();

        event.estimateTime(new NodeId(false, 0L), 0.0, 0.0);

        handler.handlePreConsensusEvent(swirldState1, dualState, event);

        int numInvocations;
        for (int i = 0; i < transactions.length; i++) {

            final Transaction transaction = transactions[i];

            numInvocations = 1;
            if (transaction.isSystem()) {
                numInvocations = 0;
            }

            verify(
                            swirldState1,
                            times(numInvocations)
                                    .description(
                                            "handlePreConsensusEvent() invoked with incorrect arguments for transaction "
                                                    + i))
                    .handleTransaction(
                            eq(0L), eq(event.getTimeCreated()), any(Instant.class), eq(transaction), eq(dualState));
        }
    }

    @Test
    @DisplayName("handleRound() invokes SwirldState.handleConsensusRound() with the correct arguments")
    void testHandleRound() {
        final ConsensusRound round = mock(ConsensusRound.class);

        handler.handleRound(round, state);

        verify(swirldState1, times(1).description("handleConsensusRound() invoked with incorrect arguments"))
                .handleConsensusRound(round, dualState);
    }

    @Test
    @DisplayName("handleTransactions() invokes SwirldState1.handleTransaction() with the correct arguments")
    void testHandleTransactions() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final ConsensusTransaction[] transactions = TransactionUtils.incrementingMixedTransactions(r);
        final AtomicInteger index = new AtomicInteger(0);
        final Instant estimatedTime = Instant.now();

        handler.handleTransactions(
                () -> transactions.length,
                () -> transactions[index.getAndIncrement()],
                () -> estimatedTime,
                swirldState1,
                dualState);

        int numInvocations;
        for (int i = 0; i < transactions.length; i++) {

            final Transaction transaction = transactions[i];

            numInvocations = 1;
            if (transaction.isSystem()) {
                numInvocations = 0;
            }

            verify(
                            swirldState1,
                            times(numInvocations)
                                    .description("handleTransaction() invoked with incorrect arguments for transaction "
                                            + i))
                    .handleTransaction(
                            eq(0L),
                            any(Instant.class),
                            eq(estimatedTime.plusNanos(i * MIN_TRANS_TIMESTAMP_INCR_NANOS)),
                            eq(transaction),
                            eq(dualState));
        }
    }

    @Test
    @DisplayName("preHandle() handles exceptions gracefully")
    void testPreHandleThrows() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final EventImpl event = newEvent(TransactionUtils.incrementingMixedTransactions(r));
        for (final Supplier<Exception> ex : EXCEPTIONS) {
            doThrow(ex.get()).when(swirldState1).preHandle(any(Transaction.class));
            assertDoesNotThrow(() -> handler.preHandle(event, swirldState1));
        }

        for (final Supplier<Exception> ex : EXCEPTIONS) {
            doThrow(ex.get()).when(swirldState2).preHandle(any(Event.class));
            assertDoesNotThrow(() -> handler.preHandle(event, swirldState2));
        }
    }

    @Test
    @DisplayName("handleTransactions() handles exceptions gracefully")
    void testHandleTransactionThrows() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final ConsensusTransaction[] transactions = TransactionUtils.incrementingMixedTransactions(r);
        final Instant estimatedTime = Instant.now();

        for (final Supplier<Exception> ex : EXCEPTIONS) {
            final AtomicInteger index = new AtomicInteger(0);

            doThrow(ex.get())
                    .when(swirldState1)
                    .handleTransaction(
                            anyLong(),
                            any(Instant.class),
                            any(Instant.class),
                            any(Transaction.class),
                            any(SwirldDualState.class));

            assertDoesNotThrow(() -> handler.handleTransactions(
                    () -> transactions.length,
                    () -> transactions[index.getAndIncrement()],
                    () -> estimatedTime,
                    swirldState1,
                    dualState));
        }
    }

    @Test
    @DisplayName("handlePreConsensusEvent() handles exceptions gracefully")
    void testHandlePreConsensusEventThrows() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final EventImpl event = newEvent(TransactionUtils.incrementingMixedTransactions(r));
        for (final Supplier<Exception> ex : EXCEPTIONS) {
            doThrow(ex.get())
                    .when(swirldState1)
                    .handleTransaction(
                            anyLong(),
                            any(Instant.class),
                            any(Instant.class),
                            any(Transaction.class),
                            any(SwirldDualState.class));
            assertDoesNotThrow(() -> handler.handlePreConsensusEvent(swirldState1, dualState, event));
        }
    }

    @Test
    @DisplayName("handleConsensusRound() handles exceptions gracefully")
    void testHandleConsensusRoundThrows() {
        for (final Supplier<Exception> ex : EXCEPTIONS) {
            doThrow(ex.get()).when(swirldState1).handleConsensusRound(any(Round.class), any(SwirldDualState.class));
            assertDoesNotThrow(() -> handler.handleRound(mock(ConsensusRound.class), state));
        }
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
