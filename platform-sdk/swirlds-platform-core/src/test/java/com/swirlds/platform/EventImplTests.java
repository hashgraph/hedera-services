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

package com.swirlds.platform;

import static com.swirlds.platform.consensus.ConsensusConstants.MIN_TRANS_TIMESTAMP_INCR_NANOS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.TransactionUtils;
import com.swirlds.platform.internal.EventImpl;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class EventImplTests {

    private static Stream<Arguments> params() {
        return Stream.of(
                // This seed will cause the testFindSystemTransactions() test to fail if the set is not ordered
                Arguments.of(2470662876573509733L, 10_000), Arguments.of(null, 10_000));
    }

    @Test
    @DisplayName("findSystemTransactions() no system transactions")
    void testNoSystemTransaction() {
        final SwirldTransaction[] transactions = TransactionUtils.incrementingSwirldTransactions(100);

        final EventImpl event = newEvent(transactions);

        assertDoesNotThrow(
                event::systemTransactionIterator, "Getting the system transaction iterator should never throw.");
        assertFalse(
                event.systemTransactionIterator().hasNext(), "System transaction iterator should not have any items.");
        assertEquals(
                transactions.length,
                event.getNumAppTransactions(),
                "The number of application transactions is incorrect.");
    }

    @Test
    @DisplayName("findSystemTransactions() no transactions")
    void testTransaction() {
        final EventImpl event = newEvent(new ConsensusTransactionImpl[0]);

        assertDoesNotThrow(
                event::systemTransactionIterator, "Getting the system transaction iterator should never throw.");
        assertFalse(
                event.systemTransactionIterator().hasNext(), "System transaction iterator should not have any items.");
        assertEquals(0, event.getNumAppTransactions(), "There should be no application transactions.");
    }

    @ParameterizedTest
    @MethodSource("params")
    @DisplayName("findSystemTransactions() find correct indices in ascending order")
    void testFindSystemTransactions(final Long seed, final int numTransactions) {
        final TransactionData data = mixedTransactions(seed, numTransactions);

        final EventImpl event = newEvent(data.transactions);
        verifySystemIterator(data, event);
        assertEquals(
                data.transactions.length - data.systemIndices.size(),
                event.getNumAppTransactions(),
                "The number of application transactions is incorrect.");
    }

    @ParameterizedTest
    @MethodSource("params")
    @DisplayName("consensusTransactionIterator() does not iterate system transactions")
    void testConsensusTransactionIterator(final Long seed, final int numTransactions) {
        final TransactionData data = mixedTransactions(seed, numTransactions);

        final EventImpl event = newEvent(data.transactions);
        final Iterator<ConsensusTransaction> iter = event.consensusTransactionIterator();
        final Set<ConsensusTransaction> transactionSet = new HashSet<>();
        while (iter.hasNext()) {
            transactionSet.add(iter.next());
        }

        verifyTransactionIterator(data, transactionSet);
        verifySystemIterator(data, event);
        assertEquals(
                data.transactions.length - data.systemIndices.size(),
                event.getNumAppTransactions(),
                "The number of application transactions is incorrect.");
    }

    @ParameterizedTest
    @MethodSource("params")
    @DisplayName("transactionIterator() does not iterate system transactions")
    void testTransactionIterator(final Long seed, final int numTransactions) {
        final TransactionData data = mixedTransactions(seed, numTransactions);

        final EventImpl event = newEvent(data.transactions);
        final Iterator<Transaction> iter = event.transactionIterator();

        final Set<Transaction> transactionSet = new HashSet<>();
        while (iter.hasNext()) {
            transactionSet.add(iter.next());
        }

        verifyTransactionIterator(data, transactionSet);
        verifySystemIterator(data, event);
        assertEquals(
                data.transactions.length - data.systemIndices.size(),
                event.getNumAppTransactions(),
                "The number of application transactions is incorrect.");
    }

    private void verifyTransactionIterator(
            final TransactionData data, final Set<? extends Transaction> transactionSet) {
        for (int i = 0; i < data.transactions.length; i++) {
            final Transaction t = data.transactions[i];
            if (data.systemIndices.contains(i)) {
                assertTrue(t.isSystem(), String.format("Transaction at index %d should be iterated", i));
                assertFalse(
                        transactionSet.contains(t),
                        String.format("Iterated transactions should include system transaction %s", t));
            } else {
                assertFalse(t.isSystem(), String.format("Transaction at index %d should not be iterated", i));
                assertTrue(
                        transactionSet.contains(t),
                        String.format("Iterated transactions should not include system transaction %s", t));
            }
        }
    }

    private TransactionData mixedTransactions(final Long seed, final int numTransactions) {
        final ConsensusTransactionImpl[] mixedTransactions = new ConsensusTransactionImpl[numTransactions];
        final List<Integer> systemIndices = new ArrayList<>();
        final Random r = RandomUtils.initRandom(seed);
        for (int i = 0; i < numTransactions; i++) {
            if (r.nextBoolean()) {
                mixedTransactions[i] = TransactionUtils.incrementingSystemTransaction();
                systemIndices.add(i);
            } else {
                mixedTransactions[i] = TransactionUtils.incrementingSwirldTransaction();
            }
        }
        return new TransactionData(mixedTransactions, systemIndices);
    }

    private void verifySystemIterator(final TransactionData data, final EventImpl event) {
        if (data.systemIndices.isEmpty()) {
            assertFalse(event.systemTransactionIterator().hasNext());
            return;
        }

        final Iterator<Integer> expectedIndexIt = data.systemIndices.iterator();
        final Iterator<SystemTransaction> actualSysTransIt = event.systemTransactionIterator();

        while (expectedIndexIt.hasNext() && actualSysTransIt.hasNext()) {

            final Integer expectedSysTransIndex = expectedIndexIt.next();
            final ConsensusTransaction expectedSysTrans = data.transactions[expectedSysTransIndex];
            final SystemTransaction actualSysTrans = actualSysTransIt.next();

            assertEquals(
                    expectedSysTrans,
                    actualSysTrans,
                    String.format(
                            "Iterator transaction does not match expected value at index %d", expectedSysTransIndex));
        }

        if (expectedIndexIt.hasNext()) {
            throw new IllegalStateException(String.format(
                    "Event %s did not iterate over all expected system transactions.", event.toShortString()));
        }

        if (actualSysTransIt.hasNext()) {
            throw new IllegalStateException(String.format(
                    "Event %s iterates over more than the expected system transactions.", event.toShortString()));
        }
    }

    @Test
    @DisplayName("consensusReached() propagates consensus data to all transactions")
    void testConsensusReached() {
        final ConsensusTransactionImpl[] mixedTransactions = new ConsensusTransactionImpl[5];
        mixedTransactions[0] = TransactionUtils.incrementingSystemTransaction();
        mixedTransactions[1] = TransactionUtils.incrementingSwirldTransaction();
        mixedTransactions[2] = TransactionUtils.incrementingSystemTransaction();
        mixedTransactions[3] = TransactionUtils.incrementingSwirldTransaction();
        mixedTransactions[4] = TransactionUtils.incrementingSystemTransaction();

        final Instant eventConsTime = Instant.now();
        final EventImpl event = newEvent(mixedTransactions);
        event.setConsensusOrder(3L);
        event.setConsensusTimestamp(eventConsTime);

        event.consensusReached();

        for (int i = 0; i < mixedTransactions.length; i++) {
            assertEquals(3L, mixedTransactions[i].getConsensusOrder(), "Consensus order does not match.");
            final Instant transConsTime = eventConsTime.plusNanos(i * MIN_TRANS_TIMESTAMP_INCR_NANOS);
            assertEquals(
                    transConsTime,
                    mixedTransactions[i].getConsensusTimestamp(),
                    "Consensus timestamp does not match the expected value based on transaction index and event "
                            + "consensus time.");
        }
    }

    private static EventImpl newEvent(final ConsensusTransactionImpl[] transactions) {
        return new EventImpl(
                new BaseEventHashedData(
                        new BasicSoftwareVersion(1),
                        new NodeId(0L),
                        0L,
                        0L,
                        CryptographyHolder.get().getNullHash(),
                        CryptographyHolder.get().getNullHash(),
                        Instant.now(),
                        transactions),
                new BaseEventUnhashedData(new NodeId(0L), new byte[0]));
    }

    private record TransactionData(ConsensusTransactionImpl[] transactions, List<Integer> systemIndices) {}
}
