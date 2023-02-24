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
import com.hedera.node.app.service.consensus.entity.Topic;
import com.hedera.node.app.service.consensus.entity.TopicBuilder;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.entity.TopicBuilderImpl;
import com.hedera.node.app.service.consensus.impl.entity.TopicImpl;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.QueryContext;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
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
    protected final HederaKey hederaKey = asHederaKey(A_COMPLEX_KEY).get();
    protected final HederaKey adminKey = asHederaKey(A_COMPLEX_KEY).get();
    protected final Long payerNum = payer.getAccountNum();
    protected final Long topicNum = 1L;
    protected final TopicID topicId = TopicID.newBuilder().setTopicNum(topicNum).build();
    protected final String beneficiaryIdStr = "0.0.3";
    protected final long paymentAmount = 1_234L;
    protected final ByteString ledgerId = ByteString.copyFromUtf8("0x03");
    protected final String memo = "test memo";
    protected final long expirationTime = 1_234_567L;
    protected  final long sequenceNumber = 1L;
    protected  final long autoRenewSecs = 100L;
    @Mock
    protected MerkleTopic topic;
    @Mock
    protected ReadableStates readableStates;
    @Mock
    protected WritableStates writableStates;
    @Mock
    protected QueryContext queryContext;

    protected MapReadableKVState<Long, MerkleTopic> readableTopicState;
    protected MapWritableKVState<Long, MerkleTopic> writableTopicState;

    protected ReadableTopicStore readableStore;
    protected WritableTopicStore writableStore;

    @BeforeEach
    void commonSetUp() {
        readableTopicState = readableTopicState();
        writableTopicState = writableTopicState();
        given(readableStates.<Long, MerkleTopic>get(TOPICS)).willReturn(readableTopicState);
        given(writableStates.<Long, MerkleTopic>get(TOPICS)).willReturn(writableTopicState);
        readableStore = new ReadableTopicStore(readableStates);
        writableStore = new WritableTopicStore(writableStates);
    }

    @NonNull
    protected MapWritableKVState<Long, MerkleTopic> writableTopicState() {
        return MapWritableKVState.<Long, MerkleTopic>builder("TOPICS")
                .build();
    }

    @NonNull
    protected MapReadableKVState<Long, MerkleTopic> readableTopicState() {
        return MapReadableKVState.<Long, MerkleTopic>builder("TOPICS")
                .value(topicNum, topic)
                .build();
    }

    protected void givenValidTopic() {
        given(topic.getMemo()).willReturn(memo);
        given(topic.getAdminKey()).willReturn((JKey) adminKey);
        given(topic.getSubmitKey()).willReturn((JKey) adminKey);
        given(topic.getAutoRenewDurationSeconds()).willReturn(autoRenewSecs);
        given(topic.getAutoRenewAccountId()).willReturn(EntityId.fromGrpcAccountId(autoRenewId));
        given(topic.getExpirationTimestamp()).willReturn(RichInstant.MISSING_INSTANT);
        given(topic.getSequenceNumber()).willReturn(sequenceNumber);
        given(topic.getRunningHash()).willReturn(new byte[48]);
        given(topic.getKey()).willReturn(EntityNum.fromLong(topicNum));
        given(topic.isDeleted()).willReturn(false);
    }

    protected Topic createTopic() {
        return new TopicBuilderImpl()
                .topicNumber(topicId.getTopicNum())
                .adminKey(asHederaKey(key).get())
                .submitKey(asHederaKey(key).get())
                .autoRenewSecs(autoRenewSecs)
                .autoRenewAccountNumber(autoRenewId.getAccountNum())
                .expiry(expirationTime)
                .sequenceNumber(sequenceNumber)
                .memo(memo)
                .deleted(true)
                .build();
    }

    protected TopicImpl setUpTopicImpl() {
        return new TopicImpl(
                topicId.getTopicNum(),
                hederaKey,
                hederaKey,
                memo,
                autoRenewId.getAccountNum(),
                autoRenewSecs,
                expirationTime,
                true,
                sequenceNumber
        );
    }
}
