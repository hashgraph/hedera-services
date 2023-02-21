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

package com.hedera.node.app.service.consensus.impl.test.handlers;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.QueryContext;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConsensusHandlerTestBase {
    protected static final String TOPICS = "TOPICS";
    protected final Key key = A_COMPLEX_KEY;
    protected final String payerId = "0.0.3";
    protected final AccountID payer = asAccount(payerId);
    protected final AccountID autoRenewId = asAccount("0.0.4");
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().setSeconds(1_234_567L).build();
    protected final HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
    protected final HederaKey adminKey = asHederaKey(A_COMPLEX_KEY).get();
    protected final Long payerNum = payer.getAccountNum();
    protected final Long topicNum = 1L;
    protected final TopicID topicId = TopicID.newBuilder().setTopicNum(topicNum).build();
    protected final String beneficiaryIdStr = "0.0.3";
    protected final long paymentAmount = 1_234L;
    protected final ByteString ledgerId = ByteString.copyFromUtf8("0x03");
    protected final String memo = "test memo";

    @Mock
    protected ReadableKVState<Long, MerkleTopic> topics;

    @Mock
    protected MerkleAccount payerAccount;

    @Mock
    protected MerkleTopic topic;

    @Mock
    protected ReadableStates states;

    @Mock
    protected QueryContext queryContext;

    protected ReadableTopicStore store;

    @BeforeEach
    void commonSetUp() {
        given(states.<Long, MerkleTopic>get(TOPICS)).willReturn(topics);
        store = new ReadableTopicStore(states);
    }

    protected void givenValidTopic() {
        given(topics.get(topicNum)).willReturn(topic);
        given(topic.getMemo()).willReturn(memo);
        given(topic.getAdminKey()).willReturn((JKey) adminKey);
        given(topic.getSubmitKey()).willReturn((JKey) adminKey);
        given(topic.getAutoRenewDurationSeconds()).willReturn(100L);
        given(topic.getAutoRenewAccountId()).willReturn(EntityId.fromGrpcAccountId(autoRenewId));
        given(topic.getExpirationTimestamp()).willReturn(RichInstant.MISSING_INSTANT);
        given(topic.getSequenceNumber()).willReturn(1L);
        given(topic.getRunningHash()).willReturn(new byte[48]);
        given(topic.getKey()).willReturn(EntityNum.fromLong(topicNum));
        given(topic.isDeleted()).willReturn(false);
    }
}
