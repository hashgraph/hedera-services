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

package com.hedera.node.app.workflows.handle;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalStaticProperties;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.txns.TransitionLogicLookup;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdaptedMonoTransitionRunnerTest {
    private final TransactionBody mockTxn = TransactionBody.getDefaultInstance();

    @Mock
    private EntityIdSource ids;

    @Mock
    private TransactionContext txnCtx;

    @Mock
    private TransactionDispatcher dispatcher;

    @Mock
    private TransitionLogicLookup lookup;

    @Mock
    private GlobalStaticProperties staticProperties;

    @Mock
    private TxnAccessor accessor;

    @Mock
    private WritableStoreFactory storeFactory;

    private AdaptedMonoTransitionRunner subject;

    @BeforeEach
    void setUp() {
        given(staticProperties.workflowsEnabled()).willReturn(Set.of(ConsensusCreateTopic));
        subject = new AdaptedMonoTransitionRunner(ids, txnCtx, dispatcher, lookup, staticProperties, storeFactory);
    }

    @Test
    void delegatesConsensusCreateAndTracksSuccess() {
        given(accessor.getFunction()).willReturn(ConsensusCreateTopic);
        given(accessor.getTxn()).willReturn(mockTxn);
        given(accessor.getTxnId()).willReturn(TransactionID.getDefaultInstance());

        subject.tryTransition(accessor);

        verify(dispatcher).dispatchHandle(ConsensusCreateTopic, mockTxn, storeFactory);
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void delegatesConsensusCreateAndTracksFailureIfThrows() {
        given(accessor.getFunction()).willReturn(ConsensusCreateTopic);
        given(accessor.getTxn()).willReturn(mockTxn);
        given(accessor.getTxnId()).willReturn(TransactionID.getDefaultInstance());
        willThrow(new HandleStatusException(INVALID_EXPIRATION_TIME))
                .given(dispatcher)
                .dispatchHandle(ConsensusCreateTopic, mockTxn, storeFactory);

        assertTrue(subject.tryTransition(accessor));

        verify(dispatcher).dispatchHandle(ConsensusCreateTopic, mockTxn, storeFactory);
        verify(txnCtx).setStatus(INVALID_EXPIRATION_TIME);
    }

    @Test
    void doesNotDelegateOthers() {
        given(accessor.getFunction()).willReturn(CryptoTransfer);
        given(accessor.getTxn()).willReturn(mockTxn);
        given(lookup.lookupFor(CryptoTransfer, mockTxn)).willReturn(Optional.empty());

        assertFalse(subject.tryTransition(accessor));

        verifyNoInteractions(dispatcher);
        verify(txnCtx).setStatus(FAIL_INVALID);
    }
}
