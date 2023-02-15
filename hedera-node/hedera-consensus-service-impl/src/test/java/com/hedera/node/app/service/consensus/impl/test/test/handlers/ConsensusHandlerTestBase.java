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

package com.hedera.node.app.service.consensus.impl.test.test.handlers;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConsensusHandlerTestBase {
    protected static final String TOPICS = "TOPICS";
    protected final Key key = A_COMPLEX_KEY;
    protected final AccountID payer = asAccount("0.0.3");
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().setSeconds(1_234_567L).build();
    protected final HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
    protected final Long payerNum = payer.getAccountNum();
    protected final Long topicNum = 1L;

    @Mock
    protected ReadableKVState<Long, MerkleTopic> topics;

    @Mock
    protected MerkleAccount payerAccount;

    @Mock
    protected MerkleTopic topic;

    @Mock
    protected ReadableStates states;

    protected ReadableTopicStore store;

    @BeforeEach
    void commonSetUp() {
        given(states.<Long, MerkleTopic>get(TOPICS)).willReturn(topics);
        store = new ReadableTopicStore(states);
    }
}
