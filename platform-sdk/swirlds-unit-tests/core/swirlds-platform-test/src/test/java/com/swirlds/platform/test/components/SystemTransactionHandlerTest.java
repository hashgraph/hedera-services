/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.system.transaction.SystemTransactionType.SYS_TRANS_STATE_SIG;
import static com.swirlds.common.test.RandomUtils.randomHash;
import static com.swirlds.common.test.RandomUtils.randomSignature;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.transaction.SystemTransactionType;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.platform.components.SystemTransactionHandler;
import com.swirlds.platform.components.SystemTransactionHandlerImpl;
import com.swirlds.platform.components.common.output.StateSignatureConsumer;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class SystemTransactionHandlerTest {

    static final Random random = new Random();

    static final int THIS_NODE_ID = 5;
    static final int OTHER_NODE_ID = 4;
    static final long ROUND_GOOD = 100;
    static final long ROUND_BAD = 101;
    static final Signature signature = randomSignature(random);
    static final Hash hash = randomHash(random);

    private EventImpl eventWithOtherNodeSig;
    private EventImpl eventWithThisNodeSig;

    @BeforeEach
    void setup() {

        eventWithOtherNodeSig = createSigEvent(OTHER_NODE_ID, ROUND_GOOD, signature, hash);
        eventWithThisNodeSig = createSigEvent(THIS_NODE_ID, ROUND_BAD, signature, hash);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("tests that exceptions are handled gracefully")
    void testHandleExceptions() {
        final StateSignatureConsumer stateSignatureConsumer = (stateSig, isConsensus) -> {
            throw new IllegalStateException("this is intentionally thrown");
        };

        final SystemTransactionHandler handler = new SystemTransactionHandlerImpl(stateSignatureConsumer);

        final ConsensusRound round =
                new ConsensusRound(List.of(eventWithThisNodeSig, eventWithOtherNodeSig),
                        mock(EventImpl.class), mock(GraphGenerations.class));

        assertDoesNotThrow(() -> handler.handlePreConsensusSystemTransactions(eventWithThisNodeSig));
        assertDoesNotThrow(() -> handler.handlePostConsensusSystemTransactions(round));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("tests handling system transactions that have reached consensus")
    void testHandleConsensus() {

        final AtomicInteger preConsensusCount = new AtomicInteger(0);
        final AtomicInteger postConsensusCount = new AtomicInteger(0);
        final StateSignatureConsumer stateSignatureConsumer = (stateSig, isConsensus) -> {
            if (isConsensus) {
                postConsensusCount.getAndIncrement();
            } else {
                preConsensusCount.getAndIncrement();
            }
        };

        final SystemTransactionHandler handler = new SystemTransactionHandlerImpl(stateSignatureConsumer);

        final ConsensusRound round =
                new ConsensusRound(List.of(eventWithThisNodeSig, eventWithOtherNodeSig),
                        mock(EventImpl.class), mock(GraphGenerations.class));

        handler.handlePostConsensusSystemTransactions(round);

        assertEquals(0, preConsensusCount.get(), "incorrect number of handle calls");
        assertEquals(2, postConsensusCount.get(), "incorrect number of handle calls");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("tests handling system transactions in an event that have not reached consensus")
    void testHandlePreConsensus() {
        final AtomicInteger preConsensusCount = new AtomicInteger(0);
        final AtomicInteger postConsensusCount = new AtomicInteger(0);
        final StateSignatureConsumer stateSignatureConsumer = (stateSig, isConsensus) -> {
            if (isConsensus) {
                postConsensusCount.getAndIncrement();
            } else {
                preConsensusCount.getAndIncrement();
            }
        };
        final SystemTransactionHandler handler = new SystemTransactionHandlerImpl(stateSignatureConsumer);

        handler.handlePreConsensusSystemTransactions(eventWithThisNodeSig);
        handler.handlePreConsensusSystemTransactions(eventWithOtherNodeSig);

        assertEquals(2, preConsensusCount.get(), "incorrect number of handle calls");
        assertEquals(0, postConsensusCount.get(), "incorrect number of handle calls");
    }

    private EventImpl createSigEvent(
            final long creatorId, final long round, final Signature signature, final Hash hash) {
        final ConsensusTransactionImpl nodeSig = createSigTransaction(SYS_TRANS_STATE_SIG, round, signature, hash);
        return newEvent(creatorId, new ConsensusTransactionImpl[] {nodeSig});
    }

    private SystemTransaction createSigTransaction(
            final SystemTransactionType type, final long round, final Signature signature, final Hash hash) {
        if (type == SYS_TRANS_STATE_SIG) {
            return new StateSignatureTransaction(round, signature, hash);
        } else {
            return null;
        }
    }

    private static EventImpl newEvent(final long creatorId, final ConsensusTransactionImpl[] transactions) {
        return new EventImpl(
                new BaseEventHashedData(
                        creatorId,
                        0L,
                        0L,
                        CryptographyHolder.get().getNullHash(),
                        CryptographyHolder.get().getNullHash(),
                        Instant.now(),
                        transactions),
                new BaseEventUnhashedData(0L, new byte[0]));
    }
}
