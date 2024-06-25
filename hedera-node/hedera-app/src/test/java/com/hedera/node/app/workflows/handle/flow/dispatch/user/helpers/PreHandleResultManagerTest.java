/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.dispatch.user.helpers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.PreHandleResultManager;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.state.spi.info.NodeInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreHandleResultManagerTest {
    @Mock
    private PreHandleWorkflow preHandleWorkflow;

    @Mock
    private SolvencyPreCheck solvencyPreCheck;

    @Mock
    private ConsensusTransactionImpl platformTxn;

    @Mock
    private ReadableStoreFactory storeFactory;

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private PreHandleResult previousResult;

    private NodeInfo creator = new NodeInfoImpl(
            0L, AccountID.newBuilder().accountNum(3).build(), 500, "localhost", 50006, "", 60005, "", "", null);

    @InjectMocks
    private PreHandleResultManager subject;

    @BeforeEach
    void setUp() {
        subject = new PreHandleResultManager(preHandleWorkflow, solvencyPreCheck);
        when(storeFactory.getStore(ReadableAccountStore.class)).thenReturn(accountStore);
    }

    @Test
    void getCurrentPreHandleResultWithPreviousResult() {
        given(platformTxn.getMetadata()).willReturn(previousResult);
        subject.getCurrentPreHandleResult(creator, platformTxn, storeFactory);
        verify(preHandleWorkflow)
                .preHandleTransaction(
                        eq(creator.accountId()),
                        eq(storeFactory),
                        eq(accountStore),
                        eq(platformTxn),
                        eq(previousResult));
    }

    @Test
    void getCurrentPreHandleResultWithoutPreviousResult() {
        given(platformTxn.getMetadata()).willReturn(null);
        subject.getCurrentPreHandleResult(creator, platformTxn, storeFactory);
        verify(preHandleWorkflow, times(1))
                .preHandleTransaction(
                        eq(creator.accountId()), eq(storeFactory), eq(accountStore), eq(platformTxn), eq(null));
    }

    @Test
    void getCurrentPreHandleResultMetadataNotPreHandleResult() {
        Object wrongMetadata = new Object();
        given(platformTxn.getMetadata()).willReturn(wrongMetadata);

        subject.getCurrentPreHandleResult(creator, platformTxn, storeFactory);
        verify(preHandleWorkflow, times(1))
                .preHandleTransaction(
                        eq(creator.accountId()), eq(storeFactory), eq(accountStore), eq(platformTxn), eq(null));
    }
}
