/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.prehandle;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.mono.context.properties.GlobalStaticProperties;
import com.hedera.node.app.service.mono.sigs.EventExpansion;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdaptedMonoEventExpansionTest {

    @Mock
    private EventExpansion eventExpansion;

    @Mock
    private PreHandleWorkflowImpl preHandleWorkflow;

    @Mock
    private GlobalStaticProperties staticProperties;

    @Mock
    private Event event;

    @Mock
    private ConsensusTransactionImpl nonsenseTxn;

    @Mock
    private MerkleHederaState state;

    private AdaptedMonoEventExpansion subject;

    @BeforeEach
    void setUp() {
        subject = new AdaptedMonoEventExpansion(eventExpansion, preHandleWorkflow, staticProperties);
    }

    @Test
    void routesWorkflowEnabledTxnsDifferently() {
        // TODO: this test is no longer relevant. It will need to be rewritten,
        //  incorporating the massive changes to `MerkleHederaState`

        //        final ArgumentCaptor<Iterator<com.swirlds.common.system.transaction.Transaction>> captor =
        //                ArgumentCaptor.forClass(Iterator.class);
        //        final var workflows = Set.of(ConsensusCreateTopic);
        //        given(staticProperties.workflowsEnabled()).willReturn(workflows);
        //        given(workflowTxn.getContents()).willReturn(createTopicContents());
        //        given(monoTxn.getContents()).willReturn(cryptoTransferContents());
        //        final var txns = List.of(workflowTxn, monoTxn);
        //        final var nextI = new AtomicInteger();
        //        willAnswer(invocation -> {
        //                    final Consumer<com.swirlds.common.system.transaction.Transaction> consumer =
        //                            invocation.getArgument(0);
        //                    consumer.onConsensusRound(txns.get(nextI.getAndIncrement()));
        //                    consumer.onConsensusRound(txns.get(nextI.getAndIncrement()));
        //                    return null;
        //                })
        //                .given(event)
        //                .forEachTransaction(any());
        //
        //        subject.expand(event, state);
        //
        //        final var iter = captor.getValue();
        //        assertTrue(iter.hasNext());
        //        assertEquals(workflowTxn, iter.next());
    }

    @Test
    void worksAroundUnparseableTxn() {
        //        final var workflows = Set.of(ConsensusCreateTopic);
        //        given(staticProperties.workflowsEnabled()).willReturn(workflows);
        //        given(nonsenseTxn.getContents()).willReturn("NONSENSE".getBytes());
        //        willAnswer(invocation -> {
        //                    final Consumer<com.swirlds.common.system.transaction.Transaction> consumer =
        //                            invocation.getArgument(0);
        //                    consumer.accept(nonsenseTxn);
        //                    return null;
        //                })
        //                .given(event)
        //                .forEachTransaction(any());
        //
        //        assertDoesNotThrow(() -> subject.expand(event, state));
    }
}
