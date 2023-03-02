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

import static com.hedera.node.app.service.consensus.impl.handlers.TemporaryUtils.fromGrpcKey;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hashgraph.pbj.runtime.io.Bytes;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.TemporaryUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.QueryContext;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TopicID;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConsensusHandlerTestBase {
    protected static final String TOPICS = "TOPICS";
    protected final Key key = A_COMPLEX_KEY;
    protected final String payerId = "0.0.3";
    protected final AccountID autoRenewId = asAccount("0.0.4");
    protected final byte[] runningHash = "runningHash".getBytes();

    protected final HederaKey adminKey = asHederaKey(key).get();
    protected final EntityNum topicEntityNum = EntityNum.fromLong(1L);
    protected final TopicID topicId =
            TopicID.newBuilder().setTopicNum(topicEntityNum.longValue()).build();
    protected final String beneficiaryIdStr = "0.0.3";
    protected final long paymentAmount = 1_234L;
    protected final ByteString ledgerId = ByteString.copyFromUtf8("0x03");
    protected final String memo = "test memo";
    protected final long expirationTime = 1_234_567L;
    protected final long sequenceNumber = 1L;
    protected final long autoRenewSecs = 100L;

    protected Topic topic;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected WritableStates writableStates;

    @Mock
    protected QueryContext queryContext;

    protected MapReadableKVState<EntityNum, Topic> readableTopicState;
    protected MapWritableKVState<EntityNum, Topic> writableTopicState;

    protected ReadableTopicStore readableStore;
    protected WritableTopicStore writableStore;

    @BeforeEach
    void commonSetUp() {
        givenValidTopic();
        readableTopicState = readableTopicState();
        writableTopicState = emptyWritableTopicState();
        given(readableStates.<EntityNum, Topic>get(TOPICS)).willReturn(readableTopicState);
        given(writableStates.<EntityNum, Topic>get(TOPICS)).willReturn(writableTopicState);
        readableStore = new ReadableTopicStore(readableStates);
        writableStore = new WritableTopicStore(writableStates);
    }

    @NonNull
    protected MapWritableKVState<EntityNum, Topic> emptyWritableTopicState() {
        return MapWritableKVState.<EntityNum, Topic>builder("TOPICS").build();
    }

    @NonNull
    protected MapWritableKVState<EntityNum, Topic> writableTopicStateWithOneKey() {
        return MapWritableKVState.<EntityNum, Topic>builder("TOPICS")
                .value(topicEntityNum, topic)
                .build();
    }

    @NonNull
    protected MapReadableKVState<EntityNum, Topic> readableTopicState() {
        return MapReadableKVState.<EntityNum, Topic>builder("TOPICS")
                .value(topicEntityNum, topic)
                .build();
    }

    protected void givenValidTopic() {
        givenValidTopic(autoRenewId.getAccountNum());
    }

    protected void givenValidTopic(long autoRenewAccountNumber) {
        givenValidTopic(autoRenewAccountNumber, false);
    }

    protected void givenValidTopic(long autoRenewAccountNumber, boolean deleted) {
        topic = new Topic(
                topicId.getTopicNum(),
                sequenceNumber,
                expirationTime,
                autoRenewSecs,
                autoRenewAccountNumber,
                deleted,
                Bytes.wrap(runningHash),
                memo,
                TemporaryUtils.fromGrpcKey(key),
                TemporaryUtils.fromGrpcKey(key));
    }

    protected Topic createTopic() {
        return new Topic.Builder()
                .topicNumber(topicId.getTopicNum())
                .adminKey(fromGrpcKey(key))
                .submitKey(fromGrpcKey(key))
                .autoRenewPeriod(autoRenewSecs)
                .autoRenewAccountNumber(autoRenewId.getAccountNum())
                .expiry(expirationTime)
                .sequenceNumber(sequenceNumber)
                .memo(memo)
                .deleted(true)
                .runningHash(Bytes.wrap(runningHash))
                .build();
    }
}
