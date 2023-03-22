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
import static com.hedera.node.app.service.mono.pbj.PbjConverter.protoToPbj;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hedera.test.utils.KeyUtils.B_COMPLEX_KEY;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hashgraph.pbj.runtime.io.Bytes;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.QueryContext;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TopicID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConsensusHandlerTestBase {
    protected static final String TOPICS = "TOPICS";
    protected final Key key = A_COMPLEX_KEY;
    protected final Key anotherKey = B_COMPLEX_KEY;
    protected final String payerIdLiteral = "0.0.3";
    protected final AccountID payerId = protoToPbj(asAccount(payerIdLiteral), AccountID.class);
    protected final AccountID autoRenewId = AccountID.newBuilder().accountNum(4).build();
    protected final byte[] runningHash = "runningHash".getBytes();

    protected final HederaKey adminKey = asHederaKey(key).get();
    protected final EntityNum topicEntityNum = EntityNum.fromLong(1L);
    protected final TopicID topicId =
            TopicID.newBuilder().topicNum(topicEntityNum.longValue()).build();
    protected final Duration WELL_KNOWN_AUTO_RENEW_PERIOD =
            Duration.newBuilder().seconds(100).build();
    protected final Timestamp WELL_KNOWN_EXPIRY =
            Timestamp.newBuilder().seconds(1_234_567L).build();
    protected final TopicID WELL_KNOWN_TOPIC_ID =
            TopicID.newBuilder().topicNum(topicEntityNum.longValue()).build();
    protected final String beneficiaryIdStr = "0.0.3";
    protected final long paymentAmount = 1_234L;
    protected final Bytes ledgerId = Bytes.wrap("0x03");
    protected final String memo = "test memo";
    protected final long expirationTime = 1_234_567L;
    protected final long sequenceNumber = 1L;
    protected final long autoRenewSecs = 100L;
    protected final Instant consensusTimestamp = Instant.ofEpochSecond(1_234_567L);
    protected final AccountID TEST_DEFAULT_PAYER =
            AccountID.newBuilder().accountNum(13257).build();

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
        refreshStoresWithCurrentTopicOnlyInReadable();
    }

    protected void refreshStoresWithCurrentTopicOnlyInReadable() {
        readableTopicState = readableTopicState();
        writableTopicState = emptyWritableTopicState();
        given(readableStates.<EntityNum, Topic>get(TOPICS)).willReturn(readableTopicState);
        given(writableStates.<EntityNum, Topic>get(TOPICS)).willReturn(writableTopicState);
        readableStore = new ReadableTopicStore(readableStates);
        writableStore = new WritableTopicStore(writableStates);
    }

    protected void refreshStoresWithCurrentTopicInBothReadableAndWritable() {
        readableTopicState = readableTopicState();
        writableTopicState = writableTopicStateWithOneKey();
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

    @NonNull
    protected MapReadableKVState<EntityNum, Topic> emptyReadableTopicState() {
        return MapReadableKVState.<EntityNum, Topic>builder("TOPICS").build();
    }

    protected void givenValidTopic() {
        givenValidTopic(autoRenewId.accountNum());
    }

    protected void givenValidTopic(long autoRenewAccountNumber) {
        givenValidTopic(autoRenewAccountNumber, false);
    }

    protected void givenValidTopic(long autoRenewAccountNumber, boolean deleted) {
        givenValidTopic(autoRenewAccountNumber, deleted, true, true);
    }

    protected void givenValidTopic(long autoRenewAccountNumber, boolean deleted, boolean withAdminKey) {
        givenValidTopic(autoRenewAccountNumber, deleted, withAdminKey, true);
    }

    protected void givenValidTopic(
            long autoRenewAccountNumber, boolean deleted, boolean withAdminKey, boolean withSubmitKey) {
        topic = new Topic(
                topicId.topicNum(),
                sequenceNumber,
                expirationTime,
                autoRenewSecs,
                autoRenewAccountNumber,
                deleted,
                Bytes.wrap(runningHash),
                memo,
                withAdminKey ? key : null,
                withSubmitKey ? key : null);
    }

    protected Topic createTopic() {
        return new Topic.Builder()
                .topicNumber(topicId.topicNum())
                .adminKey(key)
                .submitKey(key)
                .autoRenewPeriod(autoRenewSecs)
                .autoRenewAccountNumber(autoRenewId.accountNum())
                .expiry(expirationTime)
                .sequenceNumber(sequenceNumber)
                .memo(memo)
                .deleted(true)
                .runningHash(Bytes.wrap(runningHash))
                .build();
    }
}
