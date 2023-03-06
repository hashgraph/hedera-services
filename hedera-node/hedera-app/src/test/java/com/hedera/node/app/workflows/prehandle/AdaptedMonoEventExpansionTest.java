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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.context.properties.GlobalStaticProperties;
import com.hedera.node.app.service.mono.sigs.EventExpansion;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private ConsensusTransactionImpl workflowTxn;

    @Mock
    private ConsensusTransactionImpl monoTxn;

    @Mock
    private MerkleHederaState state;

    private AdaptedMonoEventExpansion subject;

    @BeforeEach
    void setUp() {
        subject = new AdaptedMonoEventExpansion(eventExpansion, preHandleWorkflow, staticProperties);
    }

    @Test
    void routesWorkflowEnabledTxnsDifferently() {
        final ArgumentCaptor<Iterator<com.swirlds.common.system.transaction.Transaction>> captor =
                ArgumentCaptor.forClass(Iterator.class);
        final var workflows = Set.of(ConsensusCreateTopic);
        given(staticProperties.workflowsEnabled()).willReturn(workflows);
        given(workflowTxn.getContents()).willReturn(createTopicContents());
        given(monoTxn.getContents()).willReturn(cryptoTransferContents());
        final var txns = List.of(workflowTxn, monoTxn);
        final var nextI = new AtomicInteger();
        willAnswer(invocation -> {
                    final Consumer<com.swirlds.common.system.transaction.Transaction> consumer =
                            invocation.getArgument(0);
                    consumer.accept(txns.get(nextI.getAndIncrement()));
                    consumer.accept(txns.get(nextI.getAndIncrement()));
                    return null;
                })
                .given(event)
                .forEachTransaction(any());

        subject.expand(event, state);

        verify(preHandleWorkflow).preHandle(captor.capture(), eq(state));
        final var iter = captor.getValue();
        assertTrue(iter.hasNext());
        assertEquals(workflowTxn, iter.next());

        verify(eventExpansion).expandSingle(monoTxn, state);
    }

    @Test
    void worksAroundUnparseableTxn() {
        final ArgumentCaptor<Iterator<com.swirlds.common.system.transaction.Transaction>> captor =
                ArgumentCaptor.forClass(Iterator.class);
        final var workflows = Set.of(ConsensusCreateTopic);
        given(staticProperties.workflowsEnabled()).willReturn(workflows);
        given(nonsenseTxn.getContents()).willReturn("NONSENSE".getBytes());
        final var txns = List.of(nonsenseTxn);
        willAnswer(invocation -> {
                    final Consumer<com.swirlds.common.system.transaction.Transaction> consumer =
                            invocation.getArgument(0);
                    consumer.accept(nonsenseTxn);
                    return null;
                })
                .given(event)
                .forEachTransaction(any());

        assertDoesNotThrow(() -> subject.expand(event, state));
    }

    private byte[] createTopicContents() {
        return Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(TransactionBody.newBuilder()
                                .setConsensusCreateTopic(ConsensusCreateTopicTransactionBody.getDefaultInstance())
                                .build()
                                .toByteString())
                        .build()
                        .toByteString())
                .build()
                .toByteArray();
    }

    private byte[] cryptoTransferContents() {
        return Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                                .build()
                                .toByteString())
                        .build()
                        .toByteString())
                .build()
                .toByteArray();
    }
}
