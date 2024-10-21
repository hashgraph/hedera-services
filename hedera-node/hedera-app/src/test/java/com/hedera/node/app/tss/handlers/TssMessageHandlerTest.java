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

package com.hedera.node.app.tss.handlers;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.tss.TssCryptographyManager;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TssMessageHandlerTest {
    private static final AccountID NODE_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(666L).build();
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock
    private TssSubmissions submissionManager;

    @Mock
    private PreHandleContext preHandleContext;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext handleContext;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private NodeInfo nodeInfo;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private NetworkInfo networkInfo;

    @Mock
    private AppContext.Gossip gossip;

    @Mock
    private TssCryptographyManager tssCryptographyManager;

    private TssMessageHandler subject;

    @BeforeEach
    void setUp() {
        subject = new TssMessageHandler(submissionManager, gossip, tssCryptographyManager);
    }

    @Test
    void nothingImplementedYet() {
        assertDoesNotThrow(() -> subject.preHandle(preHandleContext));
        assertDoesNotThrow(() -> subject.pureChecks(tssMessage()));
    }

    @Test
    void submitsToyVoteOnHandlingMessage() {
        given(handleContext.networkInfo()).willReturn(networkInfo);
        given(handleContext.consensusNow()).willReturn(CONSENSUS_NOW);
        given(handleContext.configuration()).willReturn(DEFAULT_CONFIG);
        given(networkInfo.selfNodeInfo()).willReturn(nodeInfo);
        given(nodeInfo.accountId()).willReturn(NODE_ACCOUNT_ID);

        subject.handle(handleContext);
    }

    private TransactionBody tssMessage() {
        return TransactionBody.DEFAULT;
    }
}
